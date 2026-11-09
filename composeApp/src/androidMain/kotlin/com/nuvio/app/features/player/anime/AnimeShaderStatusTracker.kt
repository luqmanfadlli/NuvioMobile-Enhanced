package com.nuvio.app.features.player.anime

import com.nuvio.app.features.player.AnimeEnhancementStatus

internal class AnimeShaderStatusTracker(
    private val onChanged: (AnimeEnhancementStatus?) -> Unit,
) {
    private var owner: Any? = null
    private var status: AnimeEnhancementStatus? = null

    @Synchronized
    fun publish(source: Any, status: AnimeEnhancementStatus?) {
        owner = source
        if (this.status == status) return
        this.status = status
        onChanged(status)
    }

    @Synchronized
    fun clear(source: Any) {
        if (owner !== source) return
        owner = null
        if (status == null) return
        status = null
        onChanged(null)
    }
}

internal fun MpvShaderPlan.toAnimeEnhancementStatus(
    input: MpvTextureSize,
    sharpenPasses: List<MpvUserShaderPass>,
    upscalePasses: List<MpvUserShaderPass>,
): AnimeEnhancementStatus? {
    val lineSharpen = passes.any { it.pass in sharpenPasses }
    val upscale = passes.any { it.pass in upscalePasses }
    if (!lineSharpen && !upscale) return null
    return AnimeEnhancementStatus(
        lineSharpen = lineSharpen,
        upscale = upscale,
        sourceWidth = input.width,
        sourceHeight = input.height,
        outputWidth = outputSize.width,
        outputHeight = outputSize.height,
    )
}
