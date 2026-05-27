import AVFoundation
import AVKit
import CoreMedia
import CoreVideo
import Foundation
import UIKit

protocol MPVPictureInPicturePlaybackController: AnyObject {
    var isPlaying: Bool { get }
    var positionMs: Int64 { get }
    var durationMs: Int64 { get }
    func play()
    func pause()
    func seek(byMs offsetMs: Int64)
}

protocol MPVPictureInPictureControllerDelegate: AnyObject {
    func pictureInPictureDidChangeActiveState(active: Bool)
}

/// Supplies decoded video frames to the PiP layer. Called on a background queue at the
/// frame pump's cadence; the implementation must be safe to invoke off the main thread.
protocol MPVPictureInPictureFrameSource: AnyObject {
    func capturePictureInPictureFrame() -> CVPixelBuffer?
}

/// Wraps an `AVPictureInPictureController` driven by an `AVSampleBufferDisplayLayer`.
///
/// Important context: this app uses mpv with a Metal layer for normal playback. iOS PiP
/// requires an `AVPlayerLayer` or `AVSampleBufferDisplayLayer`. We can't pipe mpv's GPU
/// frames into the display layer without rewriting the renderer, so PiP renders a static
/// placeholder frame while audio continues. System transport controls (play/pause/seek)
/// remain functional via the playback delegate.
@available(iOS 15.0, *)
final class MPVPictureInPictureController: NSObject {

    // MARK: - Public

    weak var delegate: MPVPictureInPictureControllerDelegate?
    weak var playbackController: MPVPictureInPicturePlaybackController?
    weak var frameSource: MPVPictureInPictureFrameSource?

    var isSupported: Bool { AVPictureInPictureController.isPictureInPictureSupported() }
    private(set) var isActive: Bool = false {
        didSet {
            guard oldValue != isActive else { return }
            delegate?.pictureInPictureDidChangeActiveState(active: isActive)
        }
    }

    let displayLayer: AVSampleBufferDisplayLayer

    // MARK: - Private

    private var pictureInPictureController: AVPictureInPictureController?
    private var framePumpTimer: DispatchSourceTimer?
    private var hostView: UIView?
    private var placeholderColor: UIColor = .black
    private let renderQueue = DispatchQueue(label: "nuvio.pip.render", qos: .userInteractive)
    private var lastEnqueuedPresentationSeconds: Double = 0
    private var hasInstalledTimebase: Bool = false
    private let framePumpIntervalSeconds: Double = 1.0 / 10.0

    override init() {
        let layer = AVSampleBufferDisplayLayer()
        layer.videoGravity = .resizeAspect
        layer.backgroundColor = UIColor.black.cgColor
        self.displayLayer = layer
        super.init()
        configureTimebase()
    }

    /// Attach the display layer to a host view so the system PiP can pull frames from it.
    /// The display layer sits behind the metal layer at full size; when PiP starts iOS
    /// reparents it into its own window so on-screen visibility doesn't matter.
    func attach(toHostView host: UIView) {
        guard hostView !== host else { return }
        detachFromHost()
        hostView = host
        displayLayer.frame = host.bounds
        host.layer.insertSublayer(displayLayer, at: 0)

        let controller = AVPictureInPictureController(
            contentSource: AVPictureInPictureController.ContentSource(
                sampleBufferDisplayLayer: displayLayer,
                playbackDelegate: self
            )
        )
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        controller.delegate = self
        pictureInPictureController = controller

        // Prime the display layer with a single frame so iOS recognises it as ready
        // for PiP. Without this, `canStartPictureInPictureAutomaticallyFromInline`
        // does not trigger automatic PiP when the app moves to the background.
        enqueueNextFrame()
    }

    func detachFromHost() {
        stopFramePump()
        pictureInPictureController?.delegate = nil
        pictureInPictureController = nil
        displayLayer.removeFromSuperlayer()
        hostView = nil
        isActive = false
    }

    func updateLayout() {
        guard let host = hostView else { return }
        displayLayer.frame = host.bounds
    }

    func updatePlaceholder(color: UIColor) {
        placeholderColor = color
    }

    func startPictureInPicture() {
        guard let controller = pictureInPictureController else { return }
        guard !controller.isPictureInPictureActive else { return }
        ensureFramePumpRunning()
        // Allow at least one frame to be enqueued before starting PiP — the system requires
        // the layer to have content already queued.
        DispatchQueue.main.async {
            controller.startPictureInPicture()
        }
    }

    func stopPictureInPicture() {
        pictureInPictureController?.stopPictureInPicture()
    }

    // MARK: - Frame pump

    /// Pumps a placeholder solid-color frame at a slow cadence so the AVSampleBufferDisplayLayer
    /// always has content. AVPictureInPictureController refuses to start (and may drop the
    /// session mid-flight) if the layer is empty. Audio + system transport controls continue
    /// regardless of what visual is being shown.
    private func ensureFramePumpRunning() {
        guard framePumpTimer == nil else { return }
        // Push an initial frame immediately so the layer has content before PiP starts.
        enqueueNextFrame()

        let timer = DispatchSource.makeTimerSource(queue: renderQueue)
        let interval = DispatchTimeInterval.milliseconds(Int(framePumpIntervalSeconds * 1000))
        timer.schedule(deadline: .now() + interval, repeating: interval)
        timer.setEventHandler { [weak self] in
            self?.enqueueNextFrame()
        }
        timer.resume()
        framePumpTimer = timer
    }

    private func stopFramePump() {
        framePumpTimer?.cancel()
        framePumpTimer = nil
    }

    private func enqueueNextFrame() {
        syncControlTimebaseToPlayback()
        let pixelBuffer = frameSource?.capturePictureInPictureFrame()
            ?? makePlaceholderPixelBuffer(size: CGSize(width: 320, height: 180), color: placeholderColor)
        guard let pixelBuffer else { return }
        enqueuePixelBuffer(pixelBuffer)
    }

    private func syncControlTimebaseToPlayback() {
        guard let timebase = displayLayer.controlTimebase,
              let playbackController = playbackController else { return }
        let positionMs = playbackController.positionMs
        let positionTime = CMTime(value: max(positionMs, 0), timescale: 1000)
        CMTimebaseSetTime(timebase, time: positionTime)
        CMTimebaseSetRate(timebase, rate: playbackController.isPlaying ? 1.0 : 0.0)
    }

    private func configureTimebase() {
        var timebase: CMTimebase?
        let status = CMTimebaseCreateWithSourceClock(
            allocator: kCFAllocatorDefault,
            sourceClock: CMClockGetHostTimeClock(),
            timebaseOut: &timebase
        )
        guard status == noErr, let timebase else { return }
        CMTimebaseSetTime(timebase, time: .zero)
        CMTimebaseSetRate(timebase, rate: 1.0)
        displayLayer.controlTimebase = timebase
        hasInstalledTimebase = true
    }

    private func enqueuePixelBuffer(_ pixelBuffer: CVPixelBuffer) {
        var formatDescription: CMVideoFormatDescription?
        CMVideoFormatDescriptionCreateForImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescriptionOut: &formatDescription
        )
        guard let formatDescription else { return }

        let presentationSeconds: Double
        if hasInstalledTimebase, let timebase = displayLayer.controlTimebase {
            presentationSeconds = CMTimeGetSeconds(CMTimebaseGetTime(timebase))
        } else {
            presentationSeconds = lastEnqueuedPresentationSeconds + framePumpIntervalSeconds
        }
        let nextPts = max(presentationSeconds, lastEnqueuedPresentationSeconds + 0.01)
        let pts = CMTime(seconds: nextPts, preferredTimescale: 600)
        lastEnqueuedPresentationSeconds = CMTimeGetSeconds(pts)

        var timing = CMSampleTimingInfo(
            duration: CMTime(seconds: framePumpIntervalSeconds, preferredTimescale: 600),
            presentationTimeStamp: pts,
            decodeTimeStamp: .invalid
        )

        var sampleBuffer: CMSampleBuffer?
        CMSampleBufferCreateReadyWithImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescription: formatDescription,
            sampleTiming: &timing,
            sampleBufferOut: &sampleBuffer
        )
        guard let sampleBuffer else { return }

        if let attachmentsArray = CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, createIfNecessary: true),
           CFArrayGetCount(attachmentsArray) > 0 {
            let dict = unsafeBitCast(
                CFArrayGetValueAtIndex(attachmentsArray, 0),
                to: CFMutableDictionary.self
            )
            CFDictionarySetValue(
                dict,
                Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
                Unmanaged.passUnretained(kCFBooleanTrue).toOpaque()
            )
        }

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            if self.displayLayer.status == .failed {
                self.displayLayer.flush()
            }
            if self.displayLayer.isReadyForMoreMediaData {
                self.displayLayer.enqueue(sampleBuffer)
            }
        }
    }

    private func makePlaceholderPixelBuffer(size: CGSize, color: UIColor) -> CVPixelBuffer? {
        let width = Int(size.width)
        let height = Int(size.height)
        let attrs: [CFString: Any] = [
            kCVPixelBufferIOSurfacePropertiesKey: [:],
            kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true,
        ]
        var pixelBuffer: CVPixelBuffer?
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            kCVPixelFormatType_32BGRA,
            attrs as CFDictionary,
            &pixelBuffer
        )
        guard status == kCVReturnSuccess, let pixelBuffer else { return nil }

        CVPixelBufferLockBaseAddress(pixelBuffer, [])
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, []) }

        let baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedFirst.rawValue
            | CGBitmapInfo.byteOrder32Little.rawValue)
        guard let context = CGContext(
            data: baseAddress,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: bitmapInfo.rawValue
        ) else { return nil }

        context.setFillColor(color.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return pixelBuffer
    }
}

@available(iOS 15.0, *)
extension MPVPictureInPictureController: AVPictureInPictureControllerDelegate {

    func pictureInPictureControllerWillStartPictureInPicture(_ controller: AVPictureInPictureController) {
        ensureFramePumpRunning()
    }

    func pictureInPictureControllerDidStartPictureInPicture(_ controller: AVPictureInPictureController) {
        isActive = true
    }

    func pictureInPictureController(_ controller: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) {
        print("[NuvioPiP] failed to start: \(error.localizedDescription)")
        isActive = false
        stopFramePump()
    }

    func pictureInPictureControllerWillStopPictureInPicture(_ controller: AVPictureInPictureController) {
        // no-op
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) {
        isActive = false
        stopFramePump()
    }

    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(true)
    }
}

@available(iOS 15.0, *)
extension MPVPictureInPictureController: AVPictureInPictureSampleBufferPlaybackDelegate {

    func pictureInPictureController(_ controller: AVPictureInPictureController, setPlaying playing: Bool) {
        if playing {
            playbackController?.play()
        } else {
            playbackController?.pause()
        }
    }

    func pictureInPictureControllerTimeRangeForPlayback(_ controller: AVPictureInPictureController) -> CMTimeRange {
        let durationMs = playbackController?.durationMs ?? 0
        if durationMs <= 0 {
            return CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity)
        }
        return CMTimeRange(
            start: .zero,
            duration: CMTime(value: durationMs, timescale: 1000)
        )
    }

    func pictureInPictureControllerIsPlaybackPaused(_ controller: AVPictureInPictureController) -> Bool {
        return !(playbackController?.isPlaying ?? false)
    }

    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        didTransitionToRenderSize newRenderSize: CMVideoDimensions
    ) {
        // no-op
    }

    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        skipByInterval skipInterval: CMTime,
        completion completionHandler: @escaping () -> Void
    ) {
        let offsetMs = Int64(CMTimeGetSeconds(skipInterval) * 1000.0)
        playbackController?.seek(byMs: offsetMs)
        completionHandler()
    }
}
