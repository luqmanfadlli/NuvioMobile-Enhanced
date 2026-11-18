import AVKit
import CoreMedia

let playerSceneDidActivateNotification = Notification.Name("NuvioPlayerSceneDidActivate")

final class PlayerPictureInPictureController: NSObject,
    AVPictureInPictureControllerDelegate, AVPictureInPictureSampleBufferPlaybackDelegate {
    let displayLayer: AVSampleBufferDisplayLayer
    private weak var owner: MPVPlayerViewController?
    private weak var metalLayer: MetalLayer?
    private var controller: AVPictureInPictureController?
    private var sceneActivationObserver: NSObjectProtocol?
    private let frameQueue: DispatchQueue
    private let prepareFrameRenderer: () -> Bool
    private let enqueueFrame: (CMSampleBuffer) -> Void
    private let clearFrameRenderer: () -> Void
    private let frameLock = NSLock()
    private var pendingFrame: (CVPixelBuffer, Double, Bool, Data?)?
    private var subtitleText = ""
    private var subtitleBold = false
    private let subtitleComposer = PictureInPictureSubtitleComposer()
    private let dolbyVisionConverter = PictureInPictureDolbyVisionConverter()
    private var convertsDolbyVision = false
    private var drainScheduled = false
    private var acceptingFrames = true
    private var feedingRestoreTransition = false
    private var lastPrimeTime: TimeInterval = 0
    private var hasReceivedFrame = false
    private var receivedFrame: Bool {
        frameLock.lock()
        defer { frameLock.unlock() }
        return hasReceivedFrame
    }
    private var timebase: CMTimebase?
    private var cachedFormat: CMVideoFormatDescription?
    private(set) var isStarting = false
    private(set) var isActive = false
    private var isBackground = false
    private var hasPresentedPictureInPicture = false
    private var handoffGeneration: UInt = 0
    private var isForegroundActive = true
    private var shouldReturnToInline = false
    private var stopRequested = false
    private var systemStartInProgress = false
    private var waitingForInlinePresentation = false
    private var restoringInline = false
    private var inlinePresentationPrepared = false
    private var parksOnStart = false

    private var isParked: Bool {
        guard let owner else { return false }
        return MainActor.assumeIsolated { PlayerPictureInPictureParking.shared.isParked(player: owner) }
    }

    private func cancelInlinePresentationWait() {
        waitingForInlinePresentation = false
        restoringInline = false
        inlinePresentationPrepared = false
        frameLock.lock()
        feedingRestoreTransition = false
        frameLock.unlock()
        metalLayer?.cancelPresentationObserver()
    }

    private func prepareInlinePresentation() {
        guard !inlinePresentationPrepared, !isBackground,
              UIApplication.shared.applicationState == .active,
              owner?.viewIfLoaded?.window?.windowScene?.activationState == .foregroundActive,
              owner?.isPlayerPlaying == true, owner?.isPlayerLoading == false,
              let metalLayer else { return }
        inlinePresentationPrepared = true
        waitingForInlinePresentation = true
        frameLock.lock()
        feedingRestoreTransition = true
        frameLock.unlock()
        metalLayer.observeNextPresentedDrawable { [weak self] _ in
            guard let self, self.waitingForInlinePresentation, !self.isBackground else { return }
            self.waitingForInlinePresentation = false
            if !self.isActive, !self.isStarting {
                self.owner?.setPictureInPicturePlaceholderVisible(false)
            }
        }
        metalLayer.nuvioPiPConsumesVideo = false
    }

    var keepsPlaybackAlive: Bool { isStarting || isActive }

    var isSupported: Bool { controller != nil }
    var isPictureInPictureActive: Bool { controller?.isPictureInPictureActive == true }

    func start() {
        guard let controller, !controller.isPictureInPictureActive else { return }
        metalLayer?.nuvioPiPConsumesVideo = true
        stopRequested = false
        shouldReturnToInline = false
        parksOnStart = true
        controller.startPictureInPicture()
    }

    init(owner: MPVPlayerViewController, metalLayer: MetalLayer) {
        let layer = AVSampleBufferDisplayLayer()
        self.displayLayer = layer
        self.owner = owner
        self.metalLayer = metalLayer
        if #available(iOS 17.0, *) {
            let renderer = layer.sampleBufferRenderer
            frameQueue = DispatchQueue(label: "com.nuvio.pip.frames", qos: .userInitiated)
            prepareFrameRenderer = {
                if renderer.status == .failed { renderer.flush() }
                return renderer.isReadyForMoreMediaData
            }
            enqueueFrame = { renderer.enqueue($0) }
            clearFrameRenderer = { renderer.flush(removingDisplayedImage: true, completionHandler: nil) }
        } else {
            frameQueue = .main
            prepareFrameRenderer = {
                if layer.status == .failed { layer.flush() }
                return layer.isReadyForMoreMediaData
            }
            enqueueFrame = { layer.enqueue($0) }
            clearFrameRenderer = { layer.flushAndRemoveImage() }
        }
        super.init()
        displayLayer.videoGravity = .resizeAspect
        var clock: CMTimebase?
        CMTimebaseCreateWithSourceClock(allocator: kCFAllocatorDefault,
                                       sourceClock: CMClockGetHostTimeClock(), timebaseOut: &clock)
        timebase = clock
        displayLayer.controlTimebase = clock
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        controller = AVPictureInPictureController(contentSource: .init(
            sampleBufferDisplayLayer: displayLayer, playbackDelegate: self))
        controller?.delegate = self
        controller?.canStartPictureInPictureAutomaticallyFromInline = true
        sceneActivationObserver = NotificationCenter.default.addObserver(
            forName: playerSceneDidActivateNotification, object: nil, queue: .main
        ) { [weak self] _ in
            self?.becameActive()
        }
        metalLayer.pictureInPictureSink = self
    }

    deinit {
        if let sceneActivationObserver {
            NotificationCenter.default.removeObserver(sceneActivationObserver)
        }
    }

    func submit(_ buffer: CVPixelBuffer, pts: Double, isPictureInPictureFrame: Bool, doviMetadata: Data?) {
        frameLock.lock()
        guard acceptingFrames else { frameLock.unlock(); return }
        let now = ProcessInfo.processInfo.systemUptime
        if !isPictureInPictureFrame && !feedingRestoreTransition && now - lastPrimeTime < 0.5 {
            frameLock.unlock()
            return
        }
        lastPrimeTime = now
        pendingFrame = (buffer, pts, isPictureInPictureFrame, doviMetadata)
        let schedule = !drainScheduled
        drainScheduled = true
        frameLock.unlock()
        if schedule {
            frameQueue.async { [weak self] in self?.drainFrame() }
        }
    }

    private func drainFrame() {
        frameLock.lock()
        let frame = pendingFrame
        pendingFrame = nil
        drainScheduled = false
        let accepts = acceptingFrames
        let cue = subtitleText
        let cueBold = subtitleBold
        let dolbyVision = convertsDolbyVision
        frameLock.unlock()
        guard accepts, let (decodedBuffer, pts, isPictureInPictureFrame, doviMetadata) = frame, pts.isFinite else { return }
        let sourceBuffer = dolbyVision
            ? (dolbyVisionConverter.convert(decodedBuffer, doviMetadata: doviMetadata) ?? decodedBuffer)
            : decodedBuffer
        let buffer = isPictureInPictureFrame && !cue.isEmpty
            ? (subtitleComposer.compose(sourceBuffer, text: cue, bold: cueBold) ?? sourceBuffer)
            : sourceBuffer
        guard prepareFrameRenderer() else { return }
        if cachedFormat.map({ CMVideoFormatDescriptionMatchesImageBuffer($0, imageBuffer: buffer) }) != true {
            var format: CMVideoFormatDescription?
            guard CMVideoFormatDescriptionCreateForImageBuffer(allocator: kCFAllocatorDefault,
                imageBuffer: buffer, formatDescriptionOut: &format) == noErr else { return }
            cachedFormat = format
        }
        guard let format = cachedFormat else { return }
        var timing = CMSampleTimingInfo(duration: .invalid,
            presentationTimeStamp: CMTime(seconds: pts, preferredTimescale: 60000), decodeTimeStamp: .invalid)
        var sample: CMSampleBuffer?
        guard CMSampleBufferCreateReadyWithImageBuffer(allocator: kCFAllocatorDefault,
            imageBuffer: buffer, formatDescription: format, sampleTiming: &timing,
            sampleBufferOut: &sample) == noErr, let sample else { return }
        if let attachments = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: true) as? [NSMutableDictionary] {
            attachments.first?[kCMSampleAttachmentKey_DisplayImmediately] = true
        }
        frameLock.lock()
        let firstFrame = !hasReceivedFrame
        hasReceivedFrame = true
        frameLock.unlock()
        if firstFrame {
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.frameLock.lock()
                let accepts = self.acceptingFrames
                self.frameLock.unlock()
                if accepts { self.updatePlaybackState() }
            }
        }
        enqueueFrame(sample)
    }

    func setDolbyVisionColorspace(_ enabled: Bool) {
        frameLock.lock()
        convertsDolbyVision = enabled
        frameLock.unlock()
        if enabled {
            frameQueue.async { [weak self] in self?.dolbyVisionConverter.warmUp() }
        }
    }

    func setSubtitleText(_ text: String, bold: Bool) {
        frameLock.lock()
        subtitleText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        subtitleBold = bold
        frameLock.unlock()
    }

    func updatePlaybackState() {
        if waitingForInlinePresentation,
           owner?.isPlayerPlaying != true || owner?.isPlayerLoading == true {
            cancelInlinePresentationWait()
            owner?.setPictureInPicturePlaceholderVisible(false)
        }
        guard let owner, let timebase else { return }
        let position = Double(owner.positionMs) / 1000
        let now = CMTimeGetSeconds(CMTimebaseGetTime(timebase))
        if !now.isFinite || abs(now - position) > 0.25 {
            CMTimebaseSetTime(timebase, time: CMTime(seconds: position, preferredTimescale: 60000))
        }
        CMTimebaseSetRate(timebase, rate: owner.isPlayerPlaying ? Double(owner.currentSpeed) : 0)
        controller?.invalidatePlaybackState()
    }

    func enteredBackground() -> Bool {
        if waitingForInlinePresentation { owner?.setPictureInPicturePlaceholderVisible(false) }
        cancelInlinePresentationWait()
        handoffGeneration &+= 1
        let generation = handoffGeneration
        isBackground = true
        isForegroundActive = false
        shouldReturnToInline = false
        stopRequested = false
        metalLayer?.nuvioPiPConsumesVideo = true
        if !keepsPlaybackAlive, receivedFrame, owner?.isPlayerPlaying == true,
           controller?.isPictureInPicturePossible == true {
            isStarting = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
                guard let self, self.handoffGeneration == generation,
                      self.isBackground, self.isStarting, !self.isActive else { return }
                self.isStarting = false
                self.owner?.beginBackgroundGracePeriod()
            }
        }
        return keepsPlaybackAlive
    }

    func enteredForeground() -> Bool {
        handoffGeneration &+= 1
        let preservePlaybackState = keepsPlaybackAlive || hasPresentedPictureInPicture || controller?.isPictureInPictureActive == true
        let closedInBackground = hasPresentedPictureInPicture && !keepsPlaybackAlive && controller?.isPictureInPictureActive != true
        if isStarting, !systemStartInProgress, controller?.isPictureInPictureActive != true {
            isStarting = false
        }
        hasPresentedPictureInPicture = false
        isBackground = false
        isForegroundActive = false
        shouldReturnToInline = preservePlaybackState && !isParked
        if !isActive, !isStarting, controller?.isPictureInPictureActive != true {
            isStarting = false
            metalLayer?.nuvioPiPConsumesVideo = false
        }
        if closedInBackground { owner?.restoreBackgroundVideo() }
        return preservePlaybackState
    }

    func becameActive() {
        isBackground = false
        isForegroundActive = true
        if restoringInline { prepareInlinePresentation() }
        guard !isParked else { return }
        shouldReturnToInline = shouldReturnToInline || keepsPlaybackAlive || controller?.isPictureInPictureActive == true
        restoreInlineIfReady()
    }

    private func restoreInlineIfReady() {
        guard shouldReturnToInline, isForegroundActive, !stopRequested, let controller else { return }
        if controller.isPictureInPictureActive {
            stopRequested = true
            restoringInline = true
            prepareInlinePresentation()
            controller.stopPictureInPicture()
        } else if !isStarting {
            isActive = false
            shouldReturnToInline = false
            metalLayer?.nuvioPiPConsumesVideo = false
        }
    }

    func invalidate() {
        cancelInlinePresentationWait()
        owner?.setPictureInPicturePlaceholderVisible(false)
        handoffGeneration &+= 1
        if let sceneActivationObserver {
            NotificationCenter.default.removeObserver(sceneActivationObserver)
            self.sceneActivationObserver = nil
        }
        frameLock.lock()
        acceptingFrames = false
        pendingFrame = nil
        frameLock.unlock()
        controller?.delegate = nil
        controller?.stopPictureInPicture()
        controller = nil
        metalLayer?.pictureInPictureSink = nil
        displayLayer.removeFromSuperlayer()
        frameQueue.async { [weak self, clearFrameRenderer] in
            self?.cachedFormat = nil
            clearFrameRenderer()
        }
    }

    func pictureInPictureControllerWillStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        cancelInlinePresentationWait()
        isStarting = true
        systemStartInProgress = true
        metalLayer?.nuvioPiPConsumesVideo = true
        updatePlaybackState()
    }

    func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        handoffGeneration &+= 1
        isStarting = false
        systemStartInProgress = false
        isActive = true
        hasPresentedPictureInPicture = true
        owner?.setPictureInPicturePlaceholderVisible(true)
        restoreInlineIfReady()
        let parks = parksOnStart
        parksOnStart = false
        if parks, !isBackground, !shouldReturnToInline,
           UIApplication.shared.applicationState == .active, let owner {
            MainActor.assumeIsolated { _ = PlayerPictureInPictureParking.shared.park(player: owner) }
        }
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                   failedToStartPictureInPictureWithError error: Error) {
        cancelInlinePresentationWait()
        owner?.setPictureInPicturePlaceholderVisible(false)
        handoffGeneration &+= 1
        isStarting = false
        systemStartInProgress = false
        isActive = false
        stopRequested = false
        shouldReturnToInline = false
        parksOnStart = false
        metalLayer?.nuvioPiPConsumesVideo = isBackground
        if isBackground { owner?.pausePlayback() }
    }

    func pictureInPictureControllerWillStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        restoringInline = true
        prepareInlinePresentation()
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        prepareInlinePresentation()
        restoringInline = false
        frameLock.lock()
        feedingRestoreTransition = false
        frameLock.unlock()
        if !inlinePresentationPrepared || !waitingForInlinePresentation {
            owner?.setPictureInPicturePlaceholderVisible(false)
        }
        handoffGeneration &+= 1
        isStarting = false
        systemStartInProgress = false
        isActive = false
        stopRequested = false
        shouldReturnToInline = false
        metalLayer?.nuvioPiPConsumesVideo = isBackground
        if isBackground { owner?.pauseBackgroundPlayback() }
        if isParked {
            DispatchQueue.main.async {
                MainActor.assumeIsolated { PlayerPictureInPictureParking.shared.closeParked() }
            }
        }
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                   restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        if let owner, isParked {
            let handled = MainActor.assumeIsolated {
                PlayerPictureInPictureParking.shared.restore(player: owner) { [weak self] restored in
                    guard let self else { completionHandler(false); return }
                    self.restoringInline = true
                    self.prepareInlinePresentation()
                    completionHandler(restored && self.owner?.isViewLoaded == true)
                }
            }
            if handled { return }
        }
        restoringInline = true
        prepareInlinePresentation()
        completionHandler(owner?.isViewLoaded == true)
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, setPlaying playing: Bool) {
        owner?.requestSystemPlaying(playing)
        updatePlaybackState()
    }

    func pictureInPictureControllerTimeRangeForPlayback(_ pictureInPictureController: AVPictureInPictureController) -> CMTimeRange {
        guard let owner, owner.durationMs > 0 else {
            return CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity)
        }
        return CMTimeRange(start: .zero, duration: CMTime(seconds: Double(owner.durationMs) / 1000, preferredTimescale: 60000))
    }

    func pictureInPictureControllerIsPlaybackPaused(_ pictureInPictureController: AVPictureInPictureController) -> Bool {
        owner?.isPlayerPlaying != true
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {}

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                   skipByInterval skipInterval: CMTime, completion completionHandler: @escaping () -> Void) {
        if let owner, owner.durationMs > 0 {
            owner.requestSystemSeekBy(Int64(CMTimeGetSeconds(skipInterval) * 1000))
        }
        updatePlaybackState()
        completionHandler()
    }
}
