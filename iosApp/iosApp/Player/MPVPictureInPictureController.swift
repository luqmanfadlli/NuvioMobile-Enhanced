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

protocol MPVPictureInPictureFrameSource: AnyObject {
    func capturePictureInPictureFrame() -> CVPixelBuffer?
}

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
    private let framePumpIntervalSeconds: Double = 1.0 / 24.0

    override init() {
        let layer = AVSampleBufferDisplayLayer()
        layer.videoGravity = .resizeAspect
        layer.backgroundColor = UIColor.black.cgColor
        self.displayLayer = layer
        super.init()
        configureTimebase()
    }

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

        ensureFramePumpRunning()
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
        DispatchQueue.main.async {
            controller.startPictureInPicture()
        }
    }

    func stopPictureInPicture() {
        pictureInPictureController?.stopPictureInPicture()
    }

    // MARK: - Frame pump

    private func ensureFramePumpRunning() {
        guard framePumpTimer == nil else { return }
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

        let pixelBuffer: CVPixelBuffer?
        if isActive {
            pixelBuffer = frameSource?.capturePictureInPictureFrame()
                ?? makePlaceholderPixelBuffer(size: CGSize(width: 640, height: 360), color: placeholderColor)
        } else {
            pixelBuffer = makePlaceholderPixelBuffer(size: CGSize(width: 640, height: 360), color: .black)
        }

        guard let pb = pixelBuffer else { return }
        enqueuePixelBuffer(pb)
    }

    func invalidatePlaybackState(positionMs: Int64, isPlaying: Bool) {
        if displayLayer.controlTimebase == nil {
            var newTimebase: CMTimebase?
            CMTimebaseCreateWithSourceClock(
                allocator: kCFAllocatorDefault,
                sourceClock: CMClockGetHostTimeClock(),
                timebaseOut: &newTimebase
            )
            displayLayer.controlTimebase = newTimebase
        }

        guard let timebase = displayLayer.controlTimebase else { return }
        let positionTime = CMTime(value: max(positionMs, 0), timescale: 1000)
        CMTimebaseSetTime(timebase, time: positionTime)
        CMTimebaseSetRate(timebase, rate: isPlaying ? 1.0 : 0.0)
    }

    private func syncControlTimebaseToPlayback() {
        guard let playbackController = playbackController else { return }
        invalidatePlaybackState(
            positionMs: playbackController.positionMs,
            isPlaying: playbackController.isPlaying
        )
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

// MARK: - AVPictureInPictureControllerDelegate

extension MPVPictureInPictureController: AVPictureInPictureControllerDelegate {

    func pictureInPictureControllerWillStartPictureInPicture(_ controller: AVPictureInPictureController) {
        isActive = true
        ensureFramePumpRunning()
    }

    func pictureInPictureControllerDidStartPictureInPicture(_ controller: AVPictureInPictureController) {
        isActive = true
    }

    func pictureInPictureController(_ controller: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) {
        print("[NuvioPiP] failed to start: \(error.localizedDescription)")
        isActive = false
    }

    func pictureInPictureControllerWillStopPictureInPicture(_ controller: AVPictureInPictureController) {
        stopFramePump()
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) {
        isActive = false
        ensureFramePumpRunning()
    }

    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
    ) {
        DispatchQueue.main.async {
            self.hostView?.setNeedsLayout()
            self.hostView?.layoutIfNeeded()
            self.updateLayout()
            completionHandler(true)
    }
}

// MARK: - AVPictureInPictureSampleBufferPlaybackDelegate

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
    ) {}

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
