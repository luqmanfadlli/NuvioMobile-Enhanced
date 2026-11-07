package com.nuvio.app.features.player.anime

import com.nuvio.app.features.player.AnimeEnhancementStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpvUserShaderTest {
    private fun shader(name: String): List<MpvUserShaderPass> =
        MpvUserShaderParser.parse(File("src/androidMain/assets/anime4k/$name").readText())

    private val upscale = MpvUserShaderParser.parse(
        AnimeShaders.withMinimumUpscaleTarget(
            File("src/androidMain/assets/anime4k/Anime4K_Upscale_CNN_x2_S.glsl").readText(),
        ),
    )
    private val restore = shader("Anime4K_Restore_CNN_S.glsl")
    private val finalSharpen = shader("Nuvio_Line_Sharpen.glsl")
    private val darken = shader("Anime4K_Darken_HQ.glsl")
    private val thin = shader("Anime4K_Thin_HQ.glsl")

    @Test
    fun parsesBundledAnime4kShaders() {
        assertEquals(5, upscale.size)
        assertEquals(4, restore.size)
        assertEquals(listOf("MAIN", "conv2d_last_tf"), upscale.last().binds)
        assertEquals("MAIN", upscale.last().save)
        assertEquals("conv2d_last_tf.w 2 *", upscale.last().width)
        assertTrue(upscale.all { it.condition.orEmpty().endsWith("* + 1440 MAIN.w / 1.200 > 2560 MAIN.h / 1.200 > * + 0 >") })
        assertTrue(upscale.all { it.hook == "MAIN" && it.body.contains("vec4 hook()") })
        assertTrue(restore.none { it.body.contains("//!") })
    }

    @Test
    fun upscalesSmallVideoTwiceWhenTheScreenIsLarger() {
        val plan = AnimeShaderEffect.planFor(restore + upscale, 1280, 720, 2400, 1080)
        assertEquals(9, plan.passes.size)
        assertEquals(MpvTextureSize(2560, 1440), plan.outputSize)
        assertEquals("MAIN", plan.passes[3].target)
        assertEquals(MpvTextureSize(1280, 720), plan.passes[3].size)
    }

    @Test
    fun upscales1080pTo1440pOnA1080pScreen() {
        val upscaleOnly = AnimeShaderEffect.planFor(upscale, 1920, 1080, 2400, 1080)
        assertEquals(5, upscaleOnly.passes.size)
        assertEquals(MpvTextureSize(3840, 2160), upscaleOnly.passes.last().size)
        assertEquals(MpvTextureSize(2560, 1440), upscaleOnly.outputSize)

        val withRestore = AnimeShaderEffect.planFor(restore + upscale, 1920, 1080, 2400, 1080)
        assertEquals(9, withRestore.passes.size)
        assertEquals(MpvTextureSize(2560, 1440), withRestore.outputSize)
    }

    @Test
    fun upscales1080pTo1440pOnA1440pScreen() {
        val plan = AnimeShaderEffect.planFor(upscale, 1920, 1080, 3200, 1440)
        assertEquals(5, plan.passes.size)
        assertEquals(MpvTextureSize(2560, 1440), plan.outputSize)
    }

    @Test
    fun keepsScreenSizedUpscaleOnLargerDisplays() {
        val plan = AnimeShaderEffect.planFor(upscale, 1280, 720, 3840, 2160)
        assertEquals(MpvTextureSize(2560, 1440), plan.outputSize)
        val fullHd = AnimeShaderEffect.planFor(upscale, 1920, 1080, 3840, 2160)
        assertEquals(MpvTextureSize(3840, 2160), fullHd.outputSize)
    }

    @Test
    fun skipsUpscaleAt1440pAndAbove() {
        val qhd = AnimeShaderEffect.planFor(upscale, 2560, 1440, 2400, 1080)
        assertTrue(qhd.passes.isEmpty())
        assertEquals(MpvTextureSize(2560, 1440), qhd.outputSize)

        val uhdSharpenOnly = AnimeShaderEffect.planFor(restore + upscale, 3840, 2160, 2400, 1080)
        assertEquals(4, uhdSharpenOnly.passes.size)
        assertEquals(MpvTextureSize(3840, 2160), uhdSharpenOnly.outputSize)
    }

    @Test
    fun upscalesPortrait1080pTo1440p() {
        val plan = AnimeShaderEffect.planFor(upscale, 1080, 1920, 2400, 1080)
        assertEquals(5, plan.passes.size)
        assertEquals(MpvTextureSize(1440, 2560), plan.outputSize)
    }

    @Test
    fun keepsVideoOpaqueOnlyForPassesThatWriteMain() {
        val plan = AnimeShaderEffect.planFor(upscale, 1280, 720, 2400, 1080)
        assertEquals(MpvTextureSize(2560, 1440), plan.outputSize)
        assertTrue(animePassFragmentShader(plan.passes.first()).contains("nuvioFragColor = hook();"))
        assertTrue(animePassFragmentShader(plan.passes.last()).contains("nuvioFragColor = vec4(hook().rgb, 1.0);"))
        assertTrue(animePassFragmentShader(plan.passes.last()).contains("#define MAIN_tex(pos) texture(MAIN_raw, vec2((pos).x, 1.0 - (pos).y))"))
    }

    @Test
    fun reportsWhatActuallyRuns() {
        val both = AnimeShaderEffect.planFor(restore + upscale, 1920, 1080, 2400, 1080)
            .toAnimeEnhancementStatus(MpvTextureSize(1920, 1080), restore, upscale)
        assertEquals(AnimeEnhancementStatus(true, true, 1920, 1080, 2560, 1440), both)

        val sharpenOnly = AnimeShaderEffect.planFor(restore + upscale, 2560, 1440, 2400, 1080)
            .toAnimeEnhancementStatus(MpvTextureSize(2560, 1440), restore, upscale)
        assertEquals(AnimeEnhancementStatus(true, false, 2560, 1440, 2560, 1440), sharpenOnly)

        val nothing = AnimeShaderEffect.planFor(upscale, 2560, 1440, 2400, 1080)
            .toAnimeEnhancementStatus(MpvTextureSize(2560, 1440), emptyList(), upscale)
        assertNull(nothing)
    }

    @Test
    fun ignoresStatusClearsFromReplacedPrograms() {
        val updates = mutableListOf<AnimeEnhancementStatus?>()
        val tracker = AnimeShaderStatusTracker { updates += it }
        val oldProgram = Any()
        val newProgram = Any()
        val status = AnimeEnhancementStatus(true, false, 1920, 1080, 1920, 1080)
        tracker.publish(oldProgram, status)
        tracker.publish(newProgram, status)
        tracker.clear(oldProgram)
        assertEquals(listOf<AnimeEnhancementStatus?>(status), updates)
        tracker.clear(newProgram)
        assertEquals(listOf(status, null), updates)
    }

    @Test
    fun runsTheLineSharpenerAfterTheUpscaler() {
        assertEquals(1, finalSharpen.size)
        assertEquals(listOf("HOOKED"), finalSharpen.single().binds)
        val plan = AnimeShaderEffect.planFor(restore + upscale + finalSharpen, 1920, 1080, 2400, 1080)
        assertEquals(10, plan.passes.size)
        assertEquals(finalSharpen.single(), plan.passes.last().pass)
        assertEquals(listOf("MAIN"), plan.passes.last().binds)
        assertEquals(MpvTextureSize(3840, 2160), plan.passes.last().size)
        assertEquals(MpvTextureSize(2560, 1440), plan.outputSize)
        assertTrue(animePassFragmentShader(plan.passes.last()).contains("nuvioFragColor = vec4(hook().rgb, 1.0);"))

        val sharpenOnly = AnimeShaderEffect.planFor(restore + finalSharpen, 1920, 1080, 2400, 1080)
        assertEquals(5, sharpenOnly.passes.size)
        assertEquals(MpvTextureSize(1920, 1080), sharpenOnly.outputSize)
        assertEquals(
            AnimeEnhancementStatus(true, false, 1920, 1080, 1920, 1080),
            sharpenOnly.toAnimeEnhancementStatus(MpvTextureSize(1920, 1080), restore + finalSharpen, emptyList()),
        )
    }

    @Test
    fun runsTheFullLineSharpenChainAroundTheUpscaler() {
        assertEquals(5, darken.size)
        assertEquals(8, thin.size)
        val chain = restore + darken + thin + upscale + finalSharpen
        val plan = AnimeShaderEffect.planFor(chain, 1920, 1080, 2400, 1080)
        assertEquals(chain.size, plan.passes.size)
        assertEquals(MpvTextureSize(1920, 1080), plan.passes[restore.size + darken.size + thin.size - 1].size)
        assertEquals(MpvTextureSize(3840, 2160), plan.passes.last().size)
        assertEquals(MpvTextureSize(2560, 1440), plan.outputSize)
        assertEquals(
            AnimeEnhancementStatus(true, true, 1920, 1080, 2560, 1440),
            plan.toAnimeEnhancementStatus(MpvTextureSize(1920, 1080), restore + darken + thin + finalSharpen, upscale),
        )
    }

    @Test
    fun fitsPortraitVideoToTheDisplay() {
        assertEquals(
            MpvTextureSize(1080, 1920),
            AnimeShaderEffect.fitToDisplay(MpvTextureSize(540, 960), 2400, 1080),
        )
    }

    @Test
    fun evaluatesMpvExpressions() {
        val sizes = mapOf("MAIN" to (1280f to 720f), "OUTPUT" to (2560f to 1440f))
        val lookup = { name: String, dimension: Char ->
            sizes[name]?.let { if (dimension == 'w') it.first else it.second }
        }
        assertEquals(1f, MpvUserShaderParser.evaluate("OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *", lookup))
        assertEquals(2560f, MpvUserShaderParser.evaluate("MAIN.width 2 *", lookup))
        assertEquals(0f, MpvUserShaderParser.evaluate("1 !", lookup))
        assertNull(MpvUserShaderParser.evaluate("MISSING.w", lookup))
        assertNull(MpvUserShaderParser.evaluate("1 +", lookup))
    }

    @Test
    fun rejectsUnsupportedDirectives() {
        assertFailsWith<IllegalArgumentException> {
            MpvUserShaderParser.parse("//!TEXTURE LUT\n//!SIZE 4 4\n//!HOOK MAIN\nvec4 hook() { return vec4(0.0); }\n")
        }
    }
}
