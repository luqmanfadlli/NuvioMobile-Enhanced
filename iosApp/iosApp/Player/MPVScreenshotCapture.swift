import Foundation
import CoreVideo
import Libmpv

/// Captures the current decoded video frame from an mpv instance into a CVPixelBuffer.
///
/// Uses `screenshot-raw video` which returns the post-decode frame (no OSD or subtitles)
/// as a BGRA byte array. Called from the PiP frame pump on a background queue so the PiP
/// layer can show actual video while the mpv Metal layer is off-screen (PiP active in
/// background) or visually obscured (PiP overlay in-app).
///
/// Caveats:
///   • `screenshot-raw` blocks until the next decoded frame is available and allocates
///     internally on every call. Drive it at sub-realtime cadence (≤ ~10 Hz).
///   • mpv must still be decoding video for the call to return a frame — never set
///     `vid=no` while PiP is active.
enum MPVScreenshotCapture {

    static func capture(mpv: OpaquePointer) -> CVPixelBuffer? {
        var result = mpv_node()
        var argv: [UnsafePointer<CChar>?] = [
            UnsafePointer(strdup("screenshot-raw")),
            UnsafePointer(strdup("video")),
            nil,
        ]

        defer {
            for case let ptr? in argv {
                free(UnsafeMutablePointer(mutating: ptr))
            }
            mpv_free_node_contents(&result)
        }

        let status: Int32 = argv.withUnsafeMutableBufferPointer { buffer in
            guard let base = buffer.baseAddress else { return Int32(-1) }
            return mpv_command_ret(mpv, base, &result)
        }

        guard status >= 0 else { return nil }
        guard result.format == MPV_FORMAT_NODE_MAP else { return nil }
        guard let list = result.u.list else { return nil }

        var width: Int = 0
        var height: Int = 0
        var stride: Int = 0
        var pixelFormat: String?
        var dataPtr: UnsafeMutableRawPointer?
        var dataSize: Int = 0

        let count = Int(list.pointee.num)
        let values = list.pointee.values
        let keys = list.pointee.keys

        for i in 0..<count {
            guard let keyCStr = keys?[i] else { continue }
            let key = String(cString: keyCStr)
            let value = values![i]

            switch key {
            case "w":
                width = Int(value.u.int64)
            case "h":
                height = Int(value.u.int64)
            case "stride":
                stride = Int(value.u.int64)
            case "format":
                if let s = value.u.string {
                    pixelFormat = String(cString: s)
                }
            case "data":
                if let ba = value.u.ba {
                    dataPtr = ba.pointee.data
                    dataSize = Int(ba.pointee.size)
                }
            default:
                break
            }
        }

        guard width > 0, height > 0, stride > 0,
              let data = dataPtr, dataSize > 0 else { return nil }

        let cvFormat: OSType
        switch pixelFormat {
        case "bgr0", "bgra":
            cvFormat = kCVPixelFormatType_32BGRA
        case "rgb0", "rgba":
            cvFormat = kCVPixelFormatType_32RGBA
        default:
            cvFormat = kCVPixelFormatType_32BGRA
        }

        var pixelBuffer: CVPixelBuffer?
        let attrs: [CFString: Any] = [
            kCVPixelBufferIOSurfacePropertiesKey: [:],
            kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true,
        ]
        let createStatus = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            cvFormat,
            attrs as CFDictionary,
            &pixelBuffer
        )

        guard createStatus == kCVReturnSuccess, let pb = pixelBuffer else { return nil }

        CVPixelBufferLockBaseAddress(pb, [])
        defer { CVPixelBufferUnlockBaseAddress(pb, []) }

        guard let dst = CVPixelBufferGetBaseAddress(pb) else { return nil }
        let dstStride = CVPixelBufferGetBytesPerRow(pb)
        let rowBytes = min(stride, dstStride)

        for row in 0..<height {
            let srcRow = data.advanced(by: row * stride)
            let dstRow = dst.advanced(by: row * dstStride)
            memcpy(dstRow, srcRow, rowBytes)
        }

        return pb
    }
}
