package com.nuvio.app.features.experience

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profiles.ProfilePopupPositionProvider
import com.nuvio.app.features.settings.descriptionRes
import com.nuvio.app.features.settings.titleRes
import org.jetbrains.compose.resources.stringResource

/**
 * The small menu a long press on the profile icon opens: pick how the app is arranged (Balanced or Sports).
 * Profiles are switched from Settings.
 */
@Composable
fun ModeSwitcherPopup(
    onDismissRequest: () -> Unit,
    popupBelowAnchor: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    val current by ExperienceRepository.mode.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    Popup(
        popupPositionProvider = remember(density, popupBelowAnchor) {
            ProfilePopupPositionProvider(
                margin = with(density) { 16.dp.roundToPx() },
                gap = with(density) { 18.dp.roundToPx() },
                preferBelow = popupBelowAnchor,
                onPositioned = { _, _ -> },
            )
        },
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 300.dp)
                .shadow(tokens.elevation.overlay, tokens.shapes.sheet)
                .background(tokens.colors.surfaceSheet, tokens.shapes.sheet)
                .padding(tokens.spacing.sheetPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Switch mode",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            ExperienceMode.selectable.forEach { mode ->
                val isCurrent = mode == current
                Row(
                    modifier = Modifier
                        .clip(tokens.shapes.compactCard)
                        .clickable {
                            ExperienceRepository.setMode(mode)
                            onDismissRequest()
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(mode.titleRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) tokens.colors.accent else tokens.colors.textPrimary,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(mode.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = tokens.colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
