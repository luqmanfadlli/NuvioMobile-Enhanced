package com.nuvio.app.core.ui.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.nuvio.app.core.ui.LocalNuvioThemeTokens
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioThemeTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.components.loadedBackdropImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

internal expect fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder

@Immutable
internal data class GlassUiStyle(
    val cardAlpha: Float,
    val overlayAlpha: Float,
    val dim: Float,
    val hairlineAlpha: Float,
)

internal fun GlassUiPreset.style(): GlassUiStyle = when (this) {
    GlassUiPreset.DARK -> GlassUiStyle(cardAlpha = 0.72f, overlayAlpha = 0.88f, dim = 0.50f, hairlineAlpha = 0.08f)
    GlassUiPreset.BALANCED -> GlassUiStyle(cardAlpha = 0.54f, overlayAlpha = 0.80f, dim = 0.35f, hairlineAlpha = 0.12f)
    GlassUiPreset.VIVID -> GlassUiStyle(cardAlpha = 0.36f, overlayAlpha = 0.70f, dim = 0.22f, hairlineAlpha = 0.18f)
}

private const val DefaultBackgroundDim = 0.40f

@Stable
internal class AppBackgroundGeometry {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

@Immutable
internal data class AppGlass(
    val blurred: ImageBitmap?,
    val dim: Float,
    val hairlineAlpha: Float,
    val geometry: AppBackgroundGeometry,
    val glassEnabled: Boolean,
)

internal val LocalAppGlass = staticCompositionLocalOf<AppGlass?> { null }

val LocalAppBackgroundActive = staticCompositionLocalOf { false }

@Immutable
private data class OriginalTheme(
    val tokens: NuvioThemeTokens,
    val colorScheme: ColorScheme,
    val typography: Typography,
)

private val LocalOriginalTheme = staticCompositionLocalOf<OriginalTheme?> { null }

@Composable
internal fun AppBackgroundHost(content: @Composable () -> Unit) {
    val config by remember {
        AppBackgroundRepository.ensureLoaded()
        AppBackgroundRepository.config
    }.collectAsStateWithLifecycle()
    val suspendCount by AppBackgroundRepository.suspended.collectAsStateWithLifecycle()
    val model = remember(config.glassEnabled, config.source, config.url, config.localFile) {
        if (config.glassEnabled) config.imageModel() else null
    }
    val glassStyle = if (config.glassEnabled) config.glassPreset.style() else null
    val glassUiActive = config.glassEnabled && model != null
    LaunchedEffect(glassUiActive) {
        com.nuvio.app.features.settings.ThemeSettingsRepository.onGlassUiActiveChanged(glassUiActive)
    }
    val dim = glassStyle?.dim ?: DefaultBackgroundDim
    val baseTokens = MaterialTheme.nuvio
    val baseScheme = MaterialTheme.colorScheme
    val geometry = remember { AppBackgroundGeometry() }
    val blurred = rememberBlurredBackground(model)

    val hasImage = model != null
    val tokens = remember(baseTokens, hasImage, glassStyle) { baseTokens.withAppBackground(hasImage, glassStyle) }
    val scheme = remember(baseScheme, hasImage, glassStyle) { baseScheme.withAppBackground(hasImage, glassStyle) }
    val glass = remember(blurred, dim, glassStyle, geometry, hasImage) {
        if (glassStyle == null && !hasImage) {
            null
        } else {
            AppGlass(
                blurred = blurred,
                dim = dim,
                hairlineAlpha = glassStyle?.hairlineAlpha ?: 0.10f,
                geometry = geometry,
                glassEnabled = glassStyle != null,
            )
        }
    }
    val baseTypography = MaterialTheme.typography
    val original = remember(baseTokens, baseScheme, baseTypography) {
        OriginalTheme(baseTokens, baseScheme, baseTypography)
    }
    val typography = remember(baseTypography, hasImage) {
        if (hasImage) baseTypography.withTextShadow() else baseTypography
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                geometry.origin = it.positionOnScreen()
                geometry.size = it.size
            },
    ) {
        if (model != null && suspendCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseTokens.colors.background),
            ) {
                NuvioAsyncImage(
                    imageUrl = model,
                    contentDescription = "",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    animateIfPossible = true,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dim)),
                )
            }
        }
        CompositionLocalProvider(
            LocalNuvioThemeTokens provides tokens,
            LocalAppGlass provides glass,
            LocalAppBackgroundActive provides hasImage,
            LocalOriginalTheme provides original,
        ) {
            MaterialTheme(
                colorScheme = scheme,
                typography = typography,
                shapes = MaterialTheme.shapes,
                content = content,
            )
        }
    }
}

@Composable
internal fun OpaqueAppSurfaces(enabled: Boolean = true, content: @Composable () -> Unit) {
    val original = LocalOriginalTheme.current?.takeIf { enabled }
    CompositionLocalProvider(
        LocalNuvioThemeTokens provides (original?.tokens ?: MaterialTheme.nuvio),
        LocalAppGlass provides if (original != null) null else LocalAppGlass.current,
        LocalAppBackgroundActive provides if (original != null) false else LocalAppBackgroundActive.current,
    ) {
        MaterialTheme(
            colorScheme = original?.colorScheme ?: MaterialTheme.colorScheme,
            typography = original?.typography ?: MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

private val ReadableTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = Offset(0f, 1f),
    blurRadius = 6f,
)

private fun TextStyle.shadowed(): TextStyle = copy(shadow = ReadableTextShadow)

private fun Typography.withTextShadow(): Typography = copy(
    displayLarge = displayLarge.shadowed(),
    displayMedium = displayMedium.shadowed(),
    displaySmall = displaySmall.shadowed(),
    headlineLarge = headlineLarge.shadowed(),
    headlineMedium = headlineMedium.shadowed(),
    headlineSmall = headlineSmall.shadowed(),
    titleLarge = titleLarge.shadowed(),
    titleMedium = titleMedium.shadowed(),
    titleSmall = titleSmall.shadowed(),
    bodyLarge = bodyLarge.shadowed(),
    bodyMedium = bodyMedium.shadowed(),
    bodySmall = bodySmall.shadowed(),
    labelLarge = labelLarge.shadowed(),
    labelMedium = labelMedium.shadowed(),
    labelSmall = labelSmall.shadowed(),
)

private fun Color.glass(alpha: Float): Color = copy(alpha = this.alpha * alpha)

private fun NuvioThemeTokens.withAppBackground(hasImage: Boolean, glass: GlassUiStyle?): NuvioThemeTokens {
    if (!hasImage && glass == null) return this
    var c = colors
    if (hasImage) {
        c = c.copy(
            background = Color.Transparent,
            backgroundAmoled = Color.Transparent,
            textSecondary = Color.White.copy(alpha = 0.88f),
            textMuted = Color.White.copy(alpha = 0.78f),
            textDisabled = Color.White.copy(alpha = 0.55f),
        )
    }
    if (glass != null) {
        c = c.copy(
            surface = c.surface.glass(glass.cardAlpha),
            surfaceElevated = c.surfaceElevated.glass(glass.cardAlpha),
            surfaceCard = c.surfaceCard.glass(glass.cardAlpha),
            surfaceSheet = c.surfaceSheet.glass(glass.overlayAlpha),
            surfaceDialog = c.surfaceDialog.glass(glass.overlayAlpha),
            surfacePopover = c.surfacePopover.glass(glass.overlayAlpha),
        )
    }
    return copy(colors = c)
}

private fun ColorScheme.withAppBackground(hasImage: Boolean, glass: GlassUiStyle?): ColorScheme {
    if (!hasImage && glass == null) return this
    var s = this
    if (hasImage) {
        s = s.copy(
            background = Color.Transparent,
            onSurfaceVariant = Color.White.copy(alpha = 0.85f),
        )
    }
    if (glass != null) {
        s = s.copy(
            surface = surface.glass(glass.cardAlpha),
            surfaceVariant = surfaceVariant.glass(glass.cardAlpha),
            surfaceContainerLowest = surfaceContainerLowest.glass(glass.cardAlpha),
            surfaceContainerLow = surfaceContainerLow.glass(glass.overlayAlpha),
            surfaceContainer = surfaceContainer.glass(glass.overlayAlpha),
            surfaceContainerHigh = surfaceContainerHigh.glass(glass.overlayAlpha),
            surfaceContainerHighest = surfaceContainerHighest.glass(glass.overlayAlpha),
        )
    }
    return s
}

@Composable
internal fun Modifier.appGlassBackdrop(shape: Shape): Modifier {
    val glass = LocalAppGlass.current?.takeIf { it.glassEnabled } ?: return this
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    return this
        .onGloballyPositioned { origin.value = it.positionOnScreen() }
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            clipPath(Path().apply { addOutline(outline) }) {
                drawBlurredSlice(glass, origin.value)
            }
            drawContent()
            drawOutline(
                outline = outline,
                color = Color.White.copy(alpha = glass.hairlineAlpha),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

@Composable
fun Modifier.appBackgroundCover(fallback: Color): Modifier {
    val glass = LocalAppGlass.current?.takeIf { it.blurred != null }
        ?: return this.background(fallback)
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    return this
        .onGloballyPositioned { origin.value = it.positionOnScreen() }
        .drawBehind {
            val position = origin.value
            if (position.isUnspecified) return@drawBehind
            val left = -(position.x - glass.geometry.origin.x)
            val top = -(position.y - glass.geometry.origin.y)
            val right = left + glass.geometry.size.width
            clipRect(left = left, top = top, right = right, bottom = size.height) {
                drawBlurredSlice(glass, position)
                drawRect(
                    color = Color.Black.copy(alpha = HeaderExtraDim),
                    topLeft = Offset(left, top),
                    size = Size(right - left, size.height - top),
                )
            }
        }
}

private const val HeaderExtraDim = 0.28f

private fun DrawScope.drawBlurredSlice(glass: AppGlass, position: Offset) {
    val image = glass.blurred ?: return
    val rootSize = glass.geometry.size
    if (rootSize.width <= 0 || position.isUnspecified) return
    val scale = max(rootSize.width / image.width.toFloat(), rootSize.height / image.height.toFloat())
    val drawnWidth = image.width * scale
    val drawnHeight = image.height * scale
    val left = (rootSize.width - drawnWidth) / 2f - (position.x - glass.geometry.origin.x)
    val top = (rootSize.height - drawnHeight) / 2f - (position.y - glass.geometry.origin.y)
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
        filterQuality = FilterQuality.Low,
    )
    drawRect(
        color = Color.Black.copy(alpha = glass.dim),
        topLeft = Offset(left, top),
        size = Size(drawnWidth, drawnHeight),
    )
}

private const val BlurWidthPx = 40
private const val BlurRadiusPx = 2
private const val BlurPasses = 3

@Composable
private fun rememberBlurredBackground(model: String?): ImageBitmap? {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    var blurred by remember(model) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(model) {
        if (model == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(BlurWidthPx * 3, BlurWidthPx * 6)
            .softwareBitmap()
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        blurred = runCatching {
            val bitmap = loadedBackdropImageBitmap(result)
            if (bitmap != null) {
                blurBitmap(density, bitmap.width.toFloat(), bitmap.height.toFloat()) { size ->
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            } else {
                val painter: Painter = result.image.asPainter(context)
                val intrinsic = painter.intrinsicSize
                blurBitmap(density, intrinsic.width, intrinsic.height) { size ->
                    with(painter) { draw(size) }
                }
            }
        }.getOrNull()
    }
    return blurred
}

private fun blurBitmap(
    density: Density,
    sourceWidth: Float,
    sourceHeight: Float,
    drawSource: DrawScope.(Size) -> Unit,
): ImageBitmap {
    val aspect = if (sourceWidth > 0f && sourceHeight > 0f) sourceHeight / sourceWidth else 2f
    val width = BlurWidthPx
    val height = (width * aspect).roundToInt().coerceIn(16, BlurWidthPx * 4)
    val small = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(small), size) { drawSource(size) }

    val pixels = IntArray(width * height)
    small.readPixels(pixels)
    repeat(BlurPasses) { boxBlur(pixels, width, height, BlurRadiusPx) }

    val output = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(output), size) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                drawRect(
                    color = Color(pixels[y * width + x]).copy(alpha = 1f),
                    topLeft = Offset(x.toFloat(), y.toFloat()),
                    size = Size(1f, 1f),
                )
            }
        }
    }
    return output
}

private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val temp = IntArray(pixels.size)
    fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
        val outer = if (horizontal) height else width
        val inner = if (horizontal) width else height
        for (o in 0 until outer) {
            for (i in 0 until inner) {
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (k in -radius..radius) {
                    val j = (i + k).coerceIn(0, inner - 1)
                    val c = if (horizontal) src[o * width + j] else src[j * width + o]
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
                val value = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                if (horizontal) dst[o * width + i] = value else dst[i * width + o] = value
            }
        }
    }
    pass(pixels, temp, horizontal = true)
    pass(temp, pixels, horizontal = false)
}

@Composable
internal fun isGlassUiActive(): Boolean = LocalAppGlass.current?.glassEnabled == true
