package com.nuvio.app.features.player.anime

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@UnstableApi
internal class AnimeShaderEffect(
    private val passes: List<MpvUserShaderPass>,
    private val sharpenPasses: List<MpvUserShaderPass>,
    private val upscalePasses: List<MpvUserShaderPass>,
    private val displayLongSide: Int,
    private val displayShortSide: Int,
    private val statusTracker: AnimeShaderStatusTracker? = null,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        AnimeShaderProgram(
            passes = passes,
            sharpenPasses = sharpenPasses,
            upscalePasses = upscalePasses,
            displayLongSide = displayLongSide,
            displayShortSide = displayShortSide,
            useHdr = useHdr,
            statusTracker = statusTracker,
        )

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        planFor(passes, inputWidth, inputHeight, displayLongSide, displayShortSide).passes.isEmpty()

    companion object {
        fun planFor(
            passes: List<MpvUserShaderPass>,
            inputWidth: Int,
            inputHeight: Int,
            displayLongSide: Int,
            displayShortSide: Int,
        ): MpvShaderPlan {
            val input = MpvTextureSize(inputWidth, inputHeight)
            val plan = MpvUserShaderParser.plan(
                passes = passes,
                input = input,
                output = fitToDisplay(input, displayLongSide, displayShortSide),
            )
            val target = targetSize(input, displayLongSide, displayShortSide)
            val output = plan.outputSize
            val upscaledPastTarget = output.width > input.width &&
                (output.width > target.width || output.height > target.height)
            if (!upscaledPastTarget) return plan
            return plan.copy(outputSize = if (target.width >= input.width) target else input)
        }

        fun targetSize(input: MpvTextureSize, displayLongSide: Int, displayShortSide: Int): MpvTextureSize {
            val display = fitToDisplay(input, displayLongSide, displayShortSide)
            val minimum = fitToDisplay(input, AnimeShaders.MINIMUM_UPSCALE_LONG_SIDE, AnimeShaders.MINIMUM_UPSCALE_SHORT_SIDE)
            return if (minimum.width.toLong() * minimum.height > display.width.toLong() * display.height) minimum else display
        }

        fun fitToDisplay(input: MpvTextureSize, displayLongSide: Int, displayShortSide: Int): MpvTextureSize {
            if (input.width <= 0 || input.height <= 0 || displayLongSide <= 0 || displayShortSide <= 0) {
                return input
            }
            val landscape = input.width >= input.height
            val displayWidth = if (landscape) displayLongSide else displayShortSide
            val displayHeight = if (landscape) displayShortSide else displayLongSide
            val scale = min(
                displayWidth.toFloat() / input.width.toFloat(),
                displayHeight.toFloat() / input.height.toFloat(),
            )
            return MpvTextureSize(
                width = max(1, (input.width * scale).roundToInt()),
                height = max(1, (input.height * scale).roundToInt()),
            )
        }
    }
}

@UnstableApi
private class AnimeShaderProgram(
    private val passes: List<MpvUserShaderPass>,
    private val sharpenPasses: List<MpvUserShaderPass>,
    private val upscalePasses: List<MpvUserShaderPass>,
    private val displayLongSide: Int,
    private val displayShortSide: Int,
    private val useHdr: Boolean,
    private val statusTracker: AnimeShaderStatusTracker?,
) : BaseGlShaderProgram(useHdr, OUTPUT_FRAME_CAPACITY) {

    private class CompiledPass(
        val program: Int,
        val positionLocation: Int,
        val samplerLocations: List<Int>,
        val sizeLocations: List<Int>,
        val ptLocations: List<Int>,
        val tapOffsetLocation: Int,
    )

    private class Target(val texId: Int, val fboId: Int, val size: MpvTextureSize)

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD)
            position(0)
        }

    private var plan = MpvShaderPlan(emptyList(), MpvTextureSize(0, 0))
    private val compiled = mutableMapOf<MpvUserShaderPass, CompiledPass>()
    private val targets = mutableListOf<Target?>()
    private var copyPass: CompiledPass? = null
    private var inputSize = MpvTextureSize(0, 0)
    private var disabled = false

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        releaseTargets()
        inputSize = MpvTextureSize(inputWidth, inputHeight)
        plan = MpvShaderPlan(emptyList(), inputSize)
        if (!disabled && !useHdr && isGlSupported()) {
            try {
                val candidate = AnimeShaderEffect.planFor(
                    passes = passes,
                    inputWidth = inputWidth,
                    inputHeight = inputHeight,
                    displayLongSide = displayLongSide,
                    displayShortSide = displayShortSide,
                )
                candidate.passes.forEach { planned ->
                    if (planned.pass !in compiled) {
                        compiled[planned.pass] = compile(ANIME_PASS_VERTEX_SHADER, animePassFragmentShader(planned), planned.binds)
                    }
                }
                candidate.passes.forEachIndexed { index, planned ->
                    val writesOutput = index == candidate.passes.lastIndex &&
                        planned.target == MAIN &&
                        planned.size == candidate.outputSize
                    targets += if (writesOutput) {
                        null
                    } else {
                        val texId = GlUtil.createTexture(planned.size.width, planned.size.height, planned.target != MAIN)
                        Target(texId = texId, fboId = GlUtil.createFboForTexture(texId), size = planned.size)
                    }
                }
                plan = candidate
            } catch (error: Exception) {
                Log.w(TAG, "Anime shader setup failed, passing frames through", error)
                disabled = true
                releaseTargets()
                plan = MpvShaderPlan(emptyList(), inputSize)
            }
        }
        statusTracker?.publish(this, plan.toAnimeEnhancementStatus(inputSize, sharpenPasses, upscalePasses))
        if (copyPass == null) {
            copyPass = try {
                compile(COPY_VERTEX_SHADER, RESAMPLE_FRAGMENT_SHADER, listOf(MAIN))
            } catch (error: IllegalStateException) {
                throw VideoFrameProcessingException(error)
            }
        }
        return Size(plan.outputSize.width, plan.outputSize.height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val outputFbo = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outputFbo, 0)
            GLES20.glDisable(GLES20.GL_BLEND)
            val textures = mutableMapOf(MAIN to (inputTexId to inputSize))
            var mainWrittenToOutput = false
            plan.passes.forEachIndexed { index, planned ->
                val target = targets[index]
                if (target == null) {
                    GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], planned.size.width, planned.size.height)
                    mainWrittenToOutput = true
                } else {
                    GlUtil.focusFramebufferUsingCurrentContext(target.fboId, planned.size.width, planned.size.height)
                }
                draw(compiled.getValue(planned.pass), planned.binds.map { textures.getValue(it) })
                if (target != null) {
                    textures[planned.target] = target.texId to target.size
                }
            }
            if (!mainWrittenToOutput) {
                val main = textures.getValue(MAIN)
                val output = plan.outputSize
                GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], output.width, output.height)
                val tapOffset = if (main.second == output) {
                    0f to 0f
                } else {
                    0.25f / output.width.toFloat() to 0.25f / output.height.toFloat()
                }
                draw(checkNotNull(copyPass), listOf(main), tapOffset)
            }
            GlUtil.checkGlError()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        statusTracker?.clear(this)
        super.release()
        try {
            releaseTargets()
            compiled.values.forEach { GLES20.glDeleteProgram(it.program) }
            compiled.clear()
            copyPass?.let { GLES20.glDeleteProgram(it.program) }
            copyPass = null
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    private fun releaseTargets() {
        targets.filterNotNull().forEach { target ->
            GlUtil.deleteFbo(target.fboId)
            GlUtil.deleteTexture(target.texId)
        }
        targets.clear()
    }

    private fun draw(
        pass: CompiledPass,
        inputs: List<Pair<Int, MpvTextureSize>>,
        tapOffset: Pair<Float, Float> = 0f to 0f,
    ) {
        GLES20.glUseProgram(pass.program)
        if (pass.tapOffsetLocation >= 0) {
            GLES20.glUniform2f(pass.tapOffsetLocation, tapOffset.first, tapOffset.second)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        inputs.forEachIndexed { unit, (texId, size) ->
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glUniform1i(pass.samplerLocations[unit], unit)
            GLES20.glUniform2f(pass.sizeLocations[unit], size.width.toFloat(), size.height.toFloat())
            GLES20.glUniform2f(pass.ptLocations[unit], 1f / size.width.toFloat(), 1f / size.height.toFloat())
        }
        quad.position(0)
        GLES20.glEnableVertexAttribArray(pass.positionLocation)
        GLES20.glVertexAttribPointer(pass.positionLocation, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pass.positionLocation)
    }

    private fun compile(vertexSource: String, fragmentSource: String, binds: List<String>): CompiledPass {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = try {
            compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        } catch (error: Exception) {
            GLES20.glDeleteShader(vertex)
            throw error
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Anime shader link failed: $log")
        }
        return CompiledPass(
            program = program,
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition"),
            samplerLocations = binds.map { GLES20.glGetUniformLocation(program, "${it}_raw") },
            sizeLocations = binds.map { GLES20.glGetUniformLocation(program, "${it}_size") },
            ptLocations = binds.map { GLES20.glGetUniformLocation(program, "${it}_pt") },
            tapOffsetLocation = GLES20.glGetUniformLocation(program, "uTapOffset"),
        )
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Anime shader compile failed: $log")
        }
        return shader
    }

    private fun isGlSupported(): Boolean {
        val version = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
        val match = Regex("OpenGL ES (\\d+)\\.(\\d+)").find(version) ?: return false
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        if (major < 3) return false
        if (major > 3 || minor >= 2) return true
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
        return "GL_EXT_color_buffer_half_float" in extensions || "GL_EXT_color_buffer_float" in extensions
    }

    private companion object {
        const val TAG = "AnimeShaderEffect"
        const val OUTPUT_FRAME_CAPACITY = 3
        const val MAIN = "MAIN"

        val QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )

        const val COPY_VERTEX_SHADER = "attribute vec2 aPosition;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vTexCoord = aPosition * 0.5 + 0.5;\n" +
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n"

        const val RESAMPLE_FRAGMENT_SHADER = "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D MAIN_raw;\n" +
            "uniform vec2 uTapOffset;\n" +
            "void main() {\n" +
            "    gl_FragColor = 0.25 * (\n" +
            "        texture2D(MAIN_raw, vTexCoord + vec2(-uTapOffset.x, -uTapOffset.y)) +\n" +
            "        texture2D(MAIN_raw, vTexCoord + vec2(uTapOffset.x, -uTapOffset.y)) +\n" +
            "        texture2D(MAIN_raw, vTexCoord + vec2(-uTapOffset.x, uTapOffset.y)) +\n" +
            "        texture2D(MAIN_raw, vTexCoord + vec2(uTapOffset.x, uTapOffset.y)));\n" +
            "}\n"
    }
}

internal const val ANIME_PASS_VERTEX_SHADER = "#version 300 es\n" +
    "in vec2 aPosition;\n" +
    "out vec2 vTexCoord;\n" +
    "void main() {\n" +
    "    vTexCoord = aPosition * 0.5 + 0.5;\n" +
    "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
    "}\n"

internal fun animePassFragmentShader(planned: MpvPlannedPass): String = buildString {
    append("#version 300 es\n")
    append("precision highp float;\n")
    append("precision highp int;\n")
    append("in vec2 vTexCoord;\n")
    append("out vec4 nuvioFragColor;\n")
    append("vec2 nuvioPos;\n")
    planned.binds.forEach { name ->
        append("uniform sampler2D ${name}_raw;\n")
        append("uniform vec2 ${name}_size;\n")
        append("uniform vec2 ${name}_pt;\n")
        append("#define ${name}_pos nuvioPos\n")
        append("#define ${name}_mul 1.0\n")
        append("#define ${name}_tex(pos) texture(${name}_raw, vec2((pos).x, 1.0 - (pos).y))\n")
        append("#define ${name}_texOff(off) ${name}_tex(${name}_pos + ${name}_pt * vec2(off))\n")
    }
    if ("MAIN" in planned.binds) {
        append("#define HOOKED_raw MAIN_raw\n")
        append("#define HOOKED_size MAIN_size\n")
        append("#define HOOKED_pt MAIN_pt\n")
        append("#define HOOKED_pos MAIN_pos\n")
        append("#define HOOKED_mul MAIN_mul\n")
        append("#define HOOKED_tex(pos) MAIN_tex(pos)\n")
        append("#define HOOKED_texOff(off) MAIN_texOff(off)\n")
    }
    append(planned.pass.body)
    append("\nvoid main() {\n")
    append("    nuvioPos = vec2(vTexCoord.x, 1.0 - vTexCoord.y);\n")
    if (planned.target == "MAIN") {
        append("    nuvioFragColor = vec4(hook().rgb, 1.0);\n")
    } else {
        append("    nuvioFragColor = hook();\n")
    }
    append("}\n")
}
