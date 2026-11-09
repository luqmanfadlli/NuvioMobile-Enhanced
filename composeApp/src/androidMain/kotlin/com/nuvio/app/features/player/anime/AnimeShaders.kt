package com.nuvio.app.features.player.anime

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import com.nuvio.app.features.player.AnimeEnhancementStatus
import java.io.File
import kotlin.math.max
import kotlin.math.min

internal object AnimeShaders {
    private const val TAG = "AnimeShaders"
    private const val ASSET_DIR = "anime4k"
    private const val RESTORE_SHADER = "Anime4K_Restore_CNN_S.glsl"
    private const val DARKEN_SHADER = "Anime4K_Darken_HQ.glsl"
    private const val THIN_SHADER = "Anime4K_Thin_HQ.glsl"
    private const val UPSCALE_SHADER = "Anime4K_Upscale_CNN_x2_S.glsl"
    private const val FINAL_SHARPEN_SHADER = "Nuvio_Line_Sharpen.glsl"
    private const val UPSCALE_CONDITION = "//!WHEN OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *"
    const val MINIMUM_UPSCALE_LONG_SIDE = 2560
    const val MINIMUM_UPSCALE_SHORT_SIDE = 1440

    fun shaderNames(upscaleEnabled: Boolean, lineSharpenEnabled: Boolean): List<String> = buildList {
        if (lineSharpenEnabled) addAll(listOf(RESTORE_SHADER, DARKEN_SHADER, THIN_SHADER))
        if (upscaleEnabled) add(UPSCALE_SHADER)
        if (lineSharpenEnabled) add(FINAL_SHARPEN_SHADER)
    }

    fun mpvShaderPaths(context: Context, upscaleEnabled: Boolean, lineSharpenEnabled: Boolean): List<String> {
        val names = shaderNames(upscaleEnabled, lineSharpenEnabled)
        if (names.isEmpty()) return emptyList()
        val directory = File(context.filesDir, ASSET_DIR)
        return names.mapNotNull { name ->
            runCatching {
                val bytes = shaderSource(context, name).toByteArray()
                directory.mkdirs()
                val file = File(directory, name)
                if (!file.exists() || !file.readBytes().contentEquals(bytes)) {
                    file.writeBytes(bytes)
                }
                file.absolutePath
            }.onFailure { error ->
                Log.w(TAG, "Unable to prepare $name for libmpv", error)
            }.getOrNull()
        }
    }

    @UnstableApi
    fun exoVideoEffects(
        context: Context,
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        statusTracker: AnimeShaderStatusTracker? = null,
    ): List<Effect> {
        if (!upscaleEnabled && !lineSharpenEnabled) return emptyList()
        return runCatching {
            val (longSide, shortSide) = displaySize(context)
            val passes = orderedPasses(context, upscaleEnabled, lineSharpenEnabled)
            listOf<Effect>(
                AnimeShaderEffect(
                    passes = passes.ordered,
                    sharpenPasses = passes.sharpen,
                    upscalePasses = passes.upscale,
                    displayLongSide = longSide,
                    displayShortSide = shortSide,
                    statusTracker = statusTracker,
                ),
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to prepare anime shaders for ExoPlayer", error)
        }.getOrDefault(emptyList())
    }

    fun mpvStatus(
        context: Context,
        shaderPaths: List<String>,
        videoWidth: Int,
        videoHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ): AnimeEnhancementStatus? {
        val upscaleEnabled = shaderPaths.any { it.endsWith("/$UPSCALE_SHADER") }
        val lineSharpenEnabled = shaderPaths.any { it.endsWith("/$RESTORE_SHADER") }
        if (!upscaleEnabled && !lineSharpenEnabled) return null
        if (videoWidth <= 0 || videoHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) return null
        return runCatching {
            val passes = orderedPasses(context, upscaleEnabled, lineSharpenEnabled)
            val input = MpvTextureSize(videoWidth, videoHeight)
            MpvUserShaderParser
                .plan(passes.ordered, input, MpvTextureSize(outputWidth, outputHeight))
                .toAnimeEnhancementStatus(input, passes.sharpen, passes.upscale)
        }.getOrNull()
    }

    private class OrderedPasses(
        val ordered: List<MpvUserShaderPass>,
        val sharpen: List<MpvUserShaderPass>,
        val upscale: List<MpvUserShaderPass>,
    )

    private fun orderedPasses(context: Context, upscaleEnabled: Boolean, lineSharpenEnabled: Boolean): OrderedPasses {
        val restore = if (lineSharpenEnabled) {
            parsedShader(context, RESTORE_SHADER) + parsedShader(context, DARKEN_SHADER) + parsedShader(context, THIN_SHADER)
        } else {
            emptyList()
        }
        val upscale = if (upscaleEnabled) parsedShader(context, UPSCALE_SHADER) else emptyList()
        val finalSharpen = if (lineSharpenEnabled) parsedShader(context, FINAL_SHARPEN_SHADER) else emptyList()
        return OrderedPasses(
            ordered = restore + upscale + finalSharpen,
            sharpen = restore + finalSharpen,
            upscale = upscale,
        )
    }

    private val parsedShaders = mutableMapOf<String, List<MpvUserShaderPass>>()

    @Synchronized
    private fun parsedShader(context: Context, name: String): List<MpvUserShaderPass> =
        parsedShaders.getOrPut(name) { MpvUserShaderParser.parse(shaderSource(context, name)) }

    private fun shaderSource(context: Context, name: String): String {
        val source = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        return if (name == UPSCALE_SHADER) withMinimumUpscaleTarget(source) else source
    }

    fun withMinimumUpscaleTarget(source: String): String {
        val long = MINIMUM_UPSCALE_LONG_SIDE
        val short = MINIMUM_UPSCALE_SHORT_SIDE
        val condition = "$UPSCALE_CONDITION " +
            "$long MAIN.w / 1.200 > $short MAIN.h / 1.200 > * + " +
            "$short MAIN.w / 1.200 > $long MAIN.h / 1.200 > * + 0 >"
        return source.replace(UPSCALE_CONDITION + "\n", condition + "\n")
    }

    private fun displaySize(context: Context): Pair<Int, Int> {
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
            if (bounds != null) {
                bounds.width() to bounds.height()
            } else {
                context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
            }
        } else {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        return max(width, height) to min(width, height)
    }
}
