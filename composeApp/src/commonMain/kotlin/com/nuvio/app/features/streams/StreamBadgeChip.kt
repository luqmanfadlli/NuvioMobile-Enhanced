package com.nuvio.app.features.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

internal object StreamBadgeChipDefaults {
    val shape = RoundedCornerShape(6.dp)
    val fileSizeHorizontalPadding = 6.dp
    val fileSizeFontSize: TextUnit = 10.sp
    val fileSizeLineHeight: TextUnit = 12.sp
    val fileSizeLetterSpacing: TextUnit = 0.sp
}

internal enum class StreamBadgeChipSize(
    val containerHeight: Dp,
    val imageHeight: Dp,
    val minImageWidth: Dp,
    val maxImageWidth: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
) {
    STREAM(
        containerHeight = 20.dp,
        imageHeight = 16.dp,
        minImageWidth = 34.dp,
        maxImageWidth = 92.dp,
        horizontalPadding = 3.dp,
        verticalPadding = 2.dp,
    ),
    PREVIEW(
        containerHeight = 24.dp,
        imageHeight = 18.dp,
        minImageWidth = 38.dp,
        maxImageWidth = 112.dp,
        horizontalPadding = 4.dp,
        verticalPadding = 3.dp,
    ),
}

@Composable
internal fun StreamBadgeChip(
    imageURL: String,
    name: String,
    tagColor: String,
    tagStyle: String,
    borderColor: String,
    size: StreamBadgeChipSize,
    modifier: Modifier = Modifier,
) {
    val backgroundColorArgb = if (tagStyle.equals("filled", ignoreCase = true)) {
        tagColor.toBadgeColorArgbOrNull()
    } else {
        null
    }
    val outlineColorArgb = borderColor.toBadgeColorArgbOrNull()
    val shape = StreamBadgeChipDefaults.shape
    var chipModifier = modifier.height(size.containerHeight)
    if (backgroundColorArgb != null) {
        chipModifier = chipModifier.background(Color(backgroundColorArgb), shape)
    }
    if (outlineColorArgb != null) {
        chipModifier = chipModifier.border(1.dp, Color(outlineColorArgb), shape)
    }

    Box(
        modifier = chipModifier
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageURL,
            contentDescription = name,
            modifier = Modifier
                .height(size.imageHeight)
                .widthIn(min = size.minImageWidth, max = size.maxImageWidth)
                .clip(shape),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun String.toBadgeColorArgbOrNull(): Long? {
    val hex = trim().removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return argb.toLongOrNull(16)
}
