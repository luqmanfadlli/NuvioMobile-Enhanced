import CoreImage
import CoreVideo
import UIKit

final class PictureInPictureSubtitleComposer {
    private struct PoolKey: Equatable {
        let format: OSType
        let width: Int
        let height: Int
    }

    private struct OverlayKey: Equatable {
        let text: String
        let width: Int
        let height: Int
        let bold: Bool
    }

    private let context = CIContext(options: [.cacheIntermediates: false])
    private var pool: CVPixelBufferPool?
    private var poolKey: PoolKey?
    private var renderAsBGRA = false
    private var overlayKey: OverlayKey?
    private var overlay: CIImage?

    private static let maxOutputEdge: CGFloat = 1280

    func compose(_ buffer: CVPixelBuffer, text: String, bold: Bool) -> CVPixelBuffer? {
        let sourceWidth = CVPixelBufferGetWidth(buffer)
        let sourceHeight = CVPixelBufferGetHeight(buffer)
        guard sourceWidth > 0, sourceHeight > 0 else { return nil }
        let scale = min(1, Self.maxOutputEdge / CGFloat(max(sourceWidth, sourceHeight)))
        let width = max(2, Int((CGFloat(sourceWidth) * scale).rounded()) & ~1)
        let height = max(2, Int((CGFloat(sourceHeight) * scale).rounded()) & ~1)
        guard let overlay = overlayImage(text: text, width: width, height: height, bold: bold) else {
            return nil
        }
        var base = CIImage(cvPixelBuffer: buffer)
        if scale < 1 {
            base = base.transformed(by: CGAffineTransform(
                scaleX: CGFloat(width) / CGFloat(sourceWidth),
                y: CGFloat(height) / CGFloat(sourceHeight)
            ))
        }
        let extent = CGRect(x: 0, y: 0, width: width, height: height)
        let composed = overlay.composited(over: base).cropped(to: extent)
        if let output = render(composed, extent: extent, like: buffer, bgra: renderAsBGRA) {
            return output
        }
        guard !renderAsBGRA else { return nil }
        renderAsBGRA = true
        return render(composed, extent: extent, like: buffer, bgra: true)
    }

    private func render(_ image: CIImage, extent: CGRect, like source: CVPixelBuffer, bgra: Bool) -> CVPixelBuffer? {
        let format = bgra ? kCVPixelFormatType_32BGRA : CVPixelBufferGetPixelFormatType(source)
        guard let output = makeBuffer(format: format, width: Int(extent.width), height: Int(extent.height)) else {
            return nil
        }
        let destination = CIRenderDestination(pixelBuffer: output)
        if let colorSpace = image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB) {
            destination.colorSpace = colorSpace
        }
        do {
            try context.startTask(toRender: image, to: destination).waitUntilCompleted()
        } catch {
            return nil
        }
        CVBufferPropagateAttachments(source, output)
        return output
    }

    private func makeBuffer(format: OSType, width: Int, height: Int) -> CVPixelBuffer? {
        let key = PoolKey(format: format, width: width, height: height)
        if key != poolKey {
            let attributes: [String: Any] = [
                kCVPixelBufferPixelFormatTypeKey as String: format,
                kCVPixelBufferWidthKey as String: width,
                kCVPixelBufferHeightKey as String: height,
                kCVPixelBufferIOSurfacePropertiesKey as String: [String: Any](),
                kCVPixelBufferMetalCompatibilityKey as String: true,
            ]
            let poolAttributes: [String: Any] = [kCVPixelBufferPoolMinimumBufferCountKey as String: 3]
            var newPool: CVPixelBufferPool?
            guard CVPixelBufferPoolCreate(kCFAllocatorDefault, poolAttributes as CFDictionary,
                                          attributes as CFDictionary, &newPool) == kCVReturnSuccess else {
                return nil
            }
            pool = newPool
            poolKey = key
        }
        guard let pool else { return nil }
        var buffer: CVPixelBuffer?
        guard CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, pool, &buffer) == kCVReturnSuccess else {
            return nil
        }
        return buffer
    }

    private func overlayImage(text: String, width: Int, height: Int, bold: Bool) -> CIImage? {
        let key = OverlayKey(text: text, width: width, height: height, bold: bold)
        if key == overlayKey { return overlay }
        overlayKey = key
        overlay = drawOverlay(text: text, width: CGFloat(width), height: CGFloat(height), bold: bold)
        return overlay
    }

    private func drawOverlay(text: String, width: CGFloat, height: CGFloat, bold: Bool) -> CIImage? {
        let fontSize = max(height * 0.055, 12)
        let font = UIFont.systemFont(ofSize: fontSize, weight: bold ? .bold : .semibold)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineBreakMode = .byWordWrapping
        let fill: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: UIColor.white,
            .paragraphStyle: paragraph,
        ]
        var stroke = fill
        stroke[.foregroundColor] = UIColor.clear
        stroke[.strokeColor] = UIColor.black
        stroke[.strokeWidth] = 16.0

        let maxWidth = width * 0.9
        let bounds = NSAttributedString(string: text, attributes: fill).boundingRect(
            with: CGSize(width: maxWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            context: nil
        )
        let padding = fontSize * 0.3
        let imageSize = CGSize(width: ceil(bounds.width) + padding * 2, height: ceil(bounds.height) + padding * 2)
        guard imageSize.width > 0, imageSize.height > 0 else { return nil }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: imageSize, format: format).image { _ in
            let rect = CGRect(x: padding, y: padding, width: ceil(bounds.width), height: ceil(bounds.height))
            NSAttributedString(string: text, attributes: stroke).draw(with: rect,
                options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
            NSAttributedString(string: text, attributes: fill).draw(with: rect,
                options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        }
        guard let cgImage = image.cgImage else { return nil }
        let x = ((width - imageSize.width) / 2).rounded()
        let y = (height * 0.06).rounded()
        return CIImage(cgImage: cgImage).transformed(by: CGAffineTransform(translationX: x, y: y))
    }
}
