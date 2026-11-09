package com.nuvio.app.features.news

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.sports.compat.LiveTvClock

private sealed interface ReaderLoad {
    data object Loading : ReaderLoad
    data object Failed : ReaderLoad
    data class Ready(val content: ArticleContent) : ReaderLoad
}

private data class ReaderPalette(val background: Color, val text: Color, val secondary: Color, val accent: Color, val surface: Color, val onAccent: Color)

@Composable
private fun readerPalette(appearance: ReaderAppearance): ReaderPalette {
    val colors = MaterialTheme.nuvio.colors
    return when (appearance) {
        ReaderAppearance.Auto -> ReaderPalette(colors.background, colors.textPrimary, colors.textMuted, colors.accent, colors.surface, colors.onAccent)
        ReaderAppearance.Light -> ReaderPalette(Color(0xFFFAF8F5), Color(0xFF1A1A1F), Color(0xFF737378), Color(0xFF2F6BFF), Color(0xFFEFEDE9), Color.White)
        ReaderAppearance.Dark -> ReaderPalette(Color(0xFF171719), Color(0xFFEBEBF0), Color(0xFF99999E), Color(0xFF5B8CFF), Color(0xFF232326), Color.White)
        ReaderAppearance.Sepia -> ReaderPalette(Color(0xFFF7EDD6), Color(0xFF42301F), Color(0xFF856647), Color(0xFF9A5B1C), Color(0xFFEBDDBF), Color.White)
    }
}

/**
 * The in-app article reader. The story's text is fetched and shown here, in the app's own typography,
 * whichever publisher it came from; where a site won't give its text up the summary is shown with a
 * button that opens the full page in an in-app browser.
 */
@Composable
internal fun ArticleReaderScreen(article: NewsArticle, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = readerPalette(ReaderSettings.appearance)
    val openInApp = rememberInAppBrowser()
    val uri = LocalUriHandler.current
    val scroll = rememberScrollState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val load by produceState<ReaderLoad>(ReaderLoad.Loading, article.url) {
        val url = article.url
        value = if (url == null) {
            ReaderLoad.Failed
        } else {
            ArticleExtractor.load(url)?.takeIf { it.paragraphs.isNotEmpty() }?.let { ReaderLoad.Ready(it) } ?: ReaderLoad.Failed
        }
    }

    val fontFamily = if (ReaderSettings.typeface == ReaderTypeface.Serif) FontFamily.Serif else FontFamily.Default
    val bodySize = 17 + (ReaderSettings.sizeStep - 3) * 2
    val progress = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f

    Column(modifier.fillMaxSize().background(palette.background)) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderIconButton(palette, onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = palette.text, modifier = Modifier.size(22.dp))
            }
            Text(
                article.publisher,
                color = palette.secondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                maxLines = 1,
            )
            ReaderIconButton(palette, selected = showSettings, onClick = { showSettings = !showSettings }) {
                Icon(Icons.Rounded.TextFields, contentDescription = "Reader settings", tint = palette.text, modifier = Modifier.size(22.dp))
            }
            if (article.url != null) {
                ReaderIconButton(palette, onClick = { openInApp(article.url) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open full page", tint = palette.text, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(palette.surface)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(palette.accent))
        }
        if (showSettings) ReaderSettingsPanel(palette)

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s32)),
            verticalArrangement = Arrangement.spacedBy(ReaderSettings.spacing.paragraphGapDp.dp),
        ) {
            val ready = load as? ReaderLoad.Ready
            val image = article.imageUrl ?: ready?.content?.imageUrl
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(palette.surface),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                article.leagueShort?.let {
                    Text(it.uppercase(), color = palette.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(article.publisher, color = palette.secondary, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                article.headline,
                color = palette.text,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (bodySize + 9).sp,
                    lineHeight = ((bodySize + 9) * 1.2f).sp,
                ),
            )
            val byline = ready?.content?.byline ?: article.author
            val time = NewsText.relative(article.publishedMs, LiveTvClock.nowMs())
            val meta = listOfNotNull(byline, time.ifBlank { null }).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = palette.secondary, style = MaterialTheme.typography.bodySmall)

            val bodyStyle = TextStyle(
                fontFamily = fontFamily,
                fontSize = bodySize.sp,
                lineHeight = (bodySize * ReaderSettings.spacing.lineHeightFactor).sp,
                color = palette.text,
            )
            when (load) {
                ReaderLoad.Loading -> {
                    if (article.summary.isNotBlank()) Text(article.summary, style = bodyStyle.copy(color = palette.secondary))
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
                is ReaderLoad.Ready -> {
                    (load as ReaderLoad.Ready).content.paragraphs.forEach { Text(it, style = bodyStyle) }
                    ReaderFooter(palette, article, openInApp, uri::openUri)
                }
                ReaderLoad.Failed -> {
                    if (article.summary.isNotBlank()) Text(article.summary, style = bodyStyle)
                    Text(
                        "${article.publisher} doesn't share the full text of this story, so it opens as the original page.",
                        color = palette.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ReaderFooter(palette, article, openInApp, uri::openUri)
                }
            }
        }
    }
}

@Composable
private fun ReaderFooter(
    palette: ReaderPalette,
    article: NewsArticle,
    openInApp: (String) -> Unit,
    openExternally: (String) -> Unit,
) {
    val url = article.url ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.accent)
                .clickable { openInApp(url) }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Read on ${article.publisher}", color = palette.onAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "Open in browser",
            color = palette.accent,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth().clickable { runCatching { openExternally(url) } }.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun ReaderIconButton(palette: ReaderPalette, selected: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (selected) palette.accent.copy(alpha = 0.25f) else palette.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ReaderSettingsPanel(palette: ReaderPalette) {
    Column(
        Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingGroup("Appearance", palette) {
            ReaderAppearance.entries.forEach { option ->
                OptionChip(option.label, ReaderSettings.appearance == option, palette) { ReaderSettings.appearance = option }
            }
        }
        SettingGroup("Typeface", palette) {
            ReaderTypeface.entries.forEach { option ->
                OptionChip(option.label, ReaderSettings.typeface == option, palette) { ReaderSettings.typeface = option }
            }
        }
        SettingGroup("Text size", palette) {
            OptionChip("A−", false, palette) { ReaderSettings.sizeStep -= 1 }
            OptionChip("A+", false, palette) { ReaderSettings.sizeStep += 1 }
        }
        SettingGroup("Spacing", palette) {
            ReaderSpacing.entries.forEach { option ->
                OptionChip(option.label, ReaderSettings.spacing == option, palette) { ReaderSettings.spacing = option }
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, palette: ReaderPalette, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = palette.secondary, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, palette: ReaderPalette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) palette.accent else Color.Transparent)
            .border(1.dp, if (selected) palette.accent else palette.secondary.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) palette.onAccent else palette.text, style = MaterialTheme.typography.labelLarge)
    }
}
