import CoreVideo
import Metal
import simd

final class PictureInPictureDolbyVisionConverter {
    private struct Params {
        var yOffset: Float
        var yScale: Float
        var cOffset: Float
        var cScale: Float
        var rawScale: Float
        var step: UInt32
    }

    private struct PoolKey: Equatable {
        let width: Int
        let height: Int
    }

    private static let shaderSource = """
    #include <metal_stdlib>
    using namespace metal;

    struct Params {
        float yOffset;
        float yScale;
        float cOffset;
        float cScale;
        float rawScale;
        uint step;
    };

    constant float m1 = 0.1593017578125;
    constant float m2 = 78.84375;
    constant float c1 = 0.8359375;
    constant float c2 = 18.8515625;
    constant float c3 = 18.6875;

    static float3 pqToLinear(float3 e) {
        float3 p = pow(max(e, 0.0), 1.0 / m2);
        float3 num = max(p - c1, 0.0);
        float3 den = max(c2 - c3 * p, 1e-6);
        return pow(num / den, 1.0 / m1);
    }

    static float3 linearToPq(float3 y) {
        float3 yp = pow(clamp(y, 0.0, 1.0), m1);
        return pow((c1 + c2 * yp) / (1.0 + c3 * yp), m2);
    }

    kernel void dolbyVisionToPq(
        texture2d<float, access::read> inY [[texture(0)]],
        texture2d<float, access::read> inC [[texture(1)]],
        texture2d<float, access::write> outY [[texture(2)]],
        texture2d<float, access::write> outC [[texture(3)]],
        constant float3x3 &iptToLms [[buffer(0)]],
        constant float3x3 &lmsToRgb [[buffer(1)]],
        constant Params &p [[buffer(2)]],
        uint2 gid [[thread_position_in_grid]]
    ) {
        if (gid.x >= outC.get_width() || gid.y >= outC.get_height()) return;
        uint2 inYMax = uint2(inY.get_width() - 1, inY.get_height() - 1);
        uint2 inCMax = uint2(inC.get_width() - 1, inC.get_height() - 1);
        float2 chromaSum = float2(0.0);
        float count = 0.0;
        for (uint dy = 0; dy < 2; dy++) {
            for (uint dx = 0; dx < 2; dx++) {
                uint2 o = gid * 2 + uint2(dx, dy);
                if (o.x >= outY.get_width() || o.y >= outY.get_height()) continue;
                uint2 src = min(o * p.step, inYMax);
                float luma = inY.read(src).r;
                float2 chroma = inC.read(min(src / 2, inCMax)).rg;
                float3 ipt = float3((luma - p.yOffset) / p.yScale, (chroma - p.cOffset) / p.cScale);
                float3 rgb = linearToPq(lmsToRgb * pqToLinear(iptToLms * ipt));
                float y = dot(rgb, float3(0.2627, 0.6780, 0.0593));
                outY.write(float4(64.0 / 1023.0 + y * (876.0 / 1023.0)), o);
                chromaSum += float2((rgb.b - y) / 1.8814, (rgb.r - y) / 1.4746);
                count += 1.0;
            }
        }
        float2 chroma = chromaSum / max(count, 1.0);
        outC.write(float4(512.0 / 1023.0 + chroma * (896.0 / 1023.0), 0.0, 1.0), gid);
    }

    static float3 reshape(float3 raw, constant float *rpu) {
        float3 lo = float3(0.0);
        float3 hi = float3(1.0);
        for (int c = 0; c < 3; c++) {
            constant float *d = rpu + 21 + c * 226;
            int n = int(d[0]);
            if (n >= 2) {
                lo[c] = d[1];
                hi[c] = d[n];
            }
        }
        float3 sig = clamp(raw, lo, hi);
        float4 sigX = float4(sig.x * sig.y, sig.x * sig.z, sig.y * sig.z, sig.x * sig.y * sig.z);
        float3 result = raw;
        for (int c = 0; c < 3; c++) {
            constant float *d = rpu + 21 + c * 226;
            int n = int(d[0]);
            if (n < 2) continue;
            float s = sig[c];
            int piece = 0;
            for (int k = 1; k < n - 1; k++) {
                if (s >= d[1 + k]) piece = k;
            }
            float r;
            if (d[10 + piece] < 0.5) {
                constant float *pc = d + 18 + piece * 3;
                r = (pc[2] * s + pc[1]) * s + pc[0];
            } else {
                int order = int(d[42 + piece]);
                r = d[50 + piece];
                float3 p = sig;
                float4 px = sigX;
                for (int j = 0; j < order; j++) {
                    constant float *mc = d + 58 + piece * 21 + j * 7;
                    r += mc[0] * p.x + mc[1] * p.y + mc[2] * p.z;
                    r += mc[3] * px.x + mc[4] * px.y + mc[5] * px.z + mc[6] * px.w;
                    p *= sig;
                    px *= sigX;
                }
            }
            result[c] = clamp(r, 0.0, 1.0);
        }
        return result;
    }

    static float3 rowMul(constant float *m, float3 v) {
        return float3(
            m[0] * v.x + m[1] * v.y + m[2] * v.z,
            m[3] * v.x + m[4] * v.y + m[5] * v.z,
            m[6] * v.x + m[7] * v.y + m[8] * v.z
        );
    }

    kernel void dolbyVisionRpuToPq(
        texture2d<float, access::read> inY [[texture(0)]],
        texture2d<float, access::read> inC [[texture(1)]],
        texture2d<float, access::write> outY [[texture(2)]],
        texture2d<float, access::write> outC [[texture(3)]],
        constant float *rpu [[buffer(0)]],
        constant Params &p [[buffer(2)]],
        uint2 gid [[thread_position_in_grid]]
    ) {
        if (gid.x >= outC.get_width() || gid.y >= outC.get_height()) return;
        uint2 inYMax = uint2(inY.get_width() - 1, inY.get_height() - 1);
        uint2 inCMax = uint2(inC.get_width() - 1, inC.get_height() - 1);
        float3 offset = float3(rpu[0], rpu[1], rpu[2]);
        float2 chromaSum = float2(0.0);
        float count = 0.0;
        for (uint dy = 0; dy < 2; dy++) {
            for (uint dx = 0; dx < 2; dx++) {
                uint2 o = gid * 2 + uint2(dx, dy);
                if (o.x >= outY.get_width() || o.y >= outY.get_height()) continue;
                uint2 src = min(o * p.step, inYMax);
                float luma = inY.read(src).r;
                float2 chroma = inC.read(min(src / 2, inCMax)).rg;
                float3 shaped = reshape(float3(luma, chroma) * p.rawScale, rpu);
                float3 lms = pqToLinear(rowMul(rpu + 3, shaped - offset));
                float3 rgb = linearToPq(rowMul(rpu + 12, lms));
                float y = dot(rgb, float3(0.2627, 0.6780, 0.0593));
                outY.write(float4(64.0 / 1023.0 + y * (876.0 / 1023.0)), o);
                chromaSum += float2((rgb.b - y) / 1.8814, (rgb.r - y) / 1.4746);
                count += 1.0;
            }
        }
        float2 chroma = chromaSum / max(count, 1.0);
        outC.write(float4(512.0 / 1023.0 + chroma * (896.0 / 1023.0), 0.0, 1.0), gid);
    }
    """

    private static let rpuFloatCount = 21 + 3 * 226

    private static let doviLmsToRgb = simd_float3x3(rows: [
        SIMD3<Float>(3.064396767855625, -2.165961226711146, 0.1015574148869036),
        SIMD3<Float>(-0.65613130540261551, 1.7855694129834121, -0.12943951030147041),
        SIMD3<Float>(0.017360633500538487, -0.047244531564539943, 1.0298894699757705),
    ])

    private static let iptToLms = simd_float3x3(
        SIMD3<Float>(1.0, 1.0, 1.0),
        SIMD3<Float>(0.0975689, -0.1138760, 0.0326151),
        SIMD3<Float>(0.205226, 0.133217, -0.676887)
    )

    private static let lmsToRgb: simd_float3x3 = {
        let lmsToRgb = simd_float3x3(rows: [
            SIMD3<Float>(3.06441879, -2.16597676, 0.10155818),
            SIMD3<Float>(-0.65612108, 1.78554118, -0.12943749),
            SIMD3<Float>(0.01736321, -0.04725154, 1.03004253),
        ])
        let c: Float = 0.02
        let crosstalk = simd_float3x3(rows: [
            SIMD3<Float>(1 - 2 * c, c, c),
            SIMD3<Float>(c, 1 - 2 * c, c),
            SIMD3<Float>(c, c, 1 - 2 * c),
        ])
        return lmsToRgb * crosstalk.inverse
    }()

    private static let maxOutputEdge = 1920

    private let device: MTLDevice?
    private var queue: MTLCommandQueue?
    private var pipeline: MTLComputePipelineState?
    private var rpuPipeline: MTLComputePipelineState?
    private var textureCache: CVMetalTextureCache?
    private var setupFailed = false
    private var pool: CVPixelBufferPool?
    private var poolKey: PoolKey?

    init() {
        device = MTLCreateSystemDefaultDevice()
    }

    func warmUp() {
        _ = prepare()
    }

    func convert(_ source: CVPixelBuffer, doviMetadata: Data?) -> CVPixelBuffer? {
        guard prepare(), let queue, let pipeline, let textureCache else { return nil }
        let rpu = doviMetadata.flatMap(Self.rpuCoefficients)
        let format = CVPixelBufferGetPixelFormatType(source)
        guard CVPixelBufferGetPlaneCount(source) == 2,
              let layout = Self.layout(for: format) else { return nil }

        let sourceWidth = CVPixelBufferGetWidth(source)
        let sourceHeight = CVPixelBufferGetHeight(source)
        let step = max(sourceWidth, sourceHeight) > Self.maxOutputEdge ? 2 : 1
        let width = max(2, (sourceWidth / step) & ~1)
        let height = max(2, (sourceHeight / step) & ~1)
        guard let output = makeBuffer(width: width, height: height) else { return nil }

        guard let inY = texture(source, plane: 0, format: layout.lumaFormat, cache: textureCache),
              let inC = texture(source, plane: 1, format: layout.chromaFormat, cache: textureCache),
              let outY = texture(output, plane: 0, format: .r16Unorm, cache: textureCache),
              let outC = texture(output, plane: 1, format: .rg16Unorm, cache: textureCache),
              let commandBuffer = queue.makeCommandBuffer(),
              let encoder = commandBuffer.makeComputeCommandEncoder() else { return nil }

        var params = Params(
            yOffset: layout.yOffset,
            yScale: layout.yScale,
            cOffset: layout.cOffset,
            cScale: layout.cScale,
            rawScale: layout.rawScale,
            step: UInt32(step)
        )
        encoder.setTexture(CVMetalTextureGetTexture(inY), index: 0)
        encoder.setTexture(CVMetalTextureGetTexture(inC), index: 1)
        encoder.setTexture(CVMetalTextureGetTexture(outY), index: 2)
        encoder.setTexture(CVMetalTextureGetTexture(outC), index: 3)
        if let rpu, let rpuPipeline {
            encoder.setComputePipelineState(rpuPipeline)
            rpu.withUnsafeBytes { encoder.setBytes($0.baseAddress!, length: $0.count, index: 0) }
        } else {
            var iptToLms = Self.iptToLms
            var lmsToRgb = Self.lmsToRgb
            encoder.setComputePipelineState(pipeline)
            encoder.setBytes(&iptToLms, length: MemoryLayout<simd_float3x3>.stride, index: 0)
            encoder.setBytes(&lmsToRgb, length: MemoryLayout<simd_float3x3>.stride, index: 1)
        }
        encoder.setBytes(&params, length: MemoryLayout<Params>.stride, index: 2)

        let chromaWidth = width / 2
        let chromaHeight = height / 2
        let threads = MTLSize(width: 16, height: 16, depth: 1)
        let groups = MTLSize(
            width: (chromaWidth + threads.width - 1) / threads.width,
            height: (chromaHeight + threads.height - 1) / threads.height,
            depth: 1
        )
        encoder.dispatchThreadgroups(groups, threadsPerThreadgroup: threads)
        encoder.endEncoding()
        commandBuffer.commit()
        commandBuffer.waitUntilCompleted()
        guard commandBuffer.status == .completed else { return nil }

        CVBufferSetAttachment(output, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_2020, .shouldPropagate)
        CVBufferSetAttachment(output, kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_SMPTE_ST_2084_PQ, .shouldPropagate)
        CVBufferSetAttachment(output, kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_2020, .shouldPropagate)
        return output
    }

    private struct Layout {
        let lumaFormat: MTLPixelFormat
        let chromaFormat: MTLPixelFormat
        let yOffset: Float
        let yScale: Float
        let cOffset: Float
        let cScale: Float
        let rawScale: Float
    }

    private static func rpuCoefficients(_ data: Data) -> [Float]? {
        guard data.count == rpuFloatCount * MemoryLayout<Float>.size else { return nil }
        var values = [Float](repeating: 0, count: rpuFloatCount)
        _ = values.withUnsafeMutableBytes { data.copyBytes(to: $0) }
        guard values.allSatisfy({ $0.isFinite }) else { return nil }
        let linear = simd_float3x3(rows: [
            SIMD3<Float>(values[12], values[13], values[14]),
            SIMD3<Float>(values[15], values[16], values[17]),
            SIMD3<Float>(values[18], values[19], values[20]),
        ])
        let lmsToRgb = doviLmsToRgb * linear
        for row in 0..<3 {
            for column in 0..<3 {
                values[12 + row * 3 + column] = lmsToRgb[column][row]
            }
        }
        return values
    }

    private static func layout(for format: OSType) -> Layout? {
        switch format {
        case kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange:
            return Layout(lumaFormat: .r16Unorm, chromaFormat: .rg16Unorm,
                          yOffset: 64 / 1023, yScale: 876 / 1023, cOffset: 512 / 1023, cScale: 896 / 1023, rawScale: 65535 / 65536)
        case kCVPixelFormatType_420YpCbCr10BiPlanarFullRange:
            return Layout(lumaFormat: .r16Unorm, chromaFormat: .rg16Unorm,
                          yOffset: 0, yScale: 1, cOffset: 512 / 1023, cScale: 1, rawScale: 65535 / 65536)
        case kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange:
            return Layout(lumaFormat: .r8Unorm, chromaFormat: .rg8Unorm,
                          yOffset: 16 / 255, yScale: 219 / 255, cOffset: 128 / 255, cScale: 224 / 255, rawScale: 255 / 256)
        case kCVPixelFormatType_420YpCbCr8BiPlanarFullRange:
            return Layout(lumaFormat: .r8Unorm, chromaFormat: .rg8Unorm,
                          yOffset: 0, yScale: 1, cOffset: 128 / 255, cScale: 1, rawScale: 255 / 256)
        default:
            return nil
        }
    }

    private func prepare() -> Bool {
        if pipeline != nil { return true }
        guard !setupFailed, let device else { return false }
        do {
            let library = try device.makeLibrary(source: Self.shaderSource, options: nil)
            guard let function = library.makeFunction(name: "dolbyVisionToPq") else {
                setupFailed = true
                return false
            }
            pipeline = try device.makeComputePipelineState(function: function)
            if let rpuFunction = library.makeFunction(name: "dolbyVisionRpuToPq") {
                rpuPipeline = try? device.makeComputePipelineState(function: rpuFunction)
            }
            queue = device.makeCommandQueue()
            var cache: CVMetalTextureCache?
            CVMetalTextureCacheCreate(kCFAllocatorDefault, nil, device, nil, &cache)
            textureCache = cache
        } catch {
            setupFailed = true
            return false
        }
        guard queue != nil, textureCache != nil else {
            setupFailed = true
            return false
        }
        return true
    }

    private func texture(
        _ buffer: CVPixelBuffer,
        plane: Int,
        format: MTLPixelFormat,
        cache: CVMetalTextureCache
    ) -> CVMetalTexture? {
        var texture: CVMetalTexture?
        let status = CVMetalTextureCacheCreateTextureFromImage(
            kCFAllocatorDefault,
            cache,
            buffer,
            nil,
            format,
            CVPixelBufferGetWidthOfPlane(buffer, plane),
            CVPixelBufferGetHeightOfPlane(buffer, plane),
            plane,
            &texture
        )
        return status == kCVReturnSuccess ? texture : nil
    }

    private func makeBuffer(width: Int, height: Int) -> CVPixelBuffer? {
        let key = PoolKey(width: width, height: height)
        if key != poolKey {
            let attributes: [String: Any] = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange,
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
}
