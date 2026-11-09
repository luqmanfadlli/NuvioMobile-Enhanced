package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.experience.ExperienceMode
import com.nuvio.app.features.experience.ExperienceRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.experience_mode_balanced
import nuvio.composeapp.generated.resources.experience_mode_balanced_description
import nuvio.composeapp.generated.resources.experience_mode_music
import nuvio.composeapp.generated.resources.experience_mode_music_description
import nuvio.composeapp.generated.resources.experience_mode_sports
import nuvio.composeapp.generated.resources.experience_mode_sports_description
import nuvio.composeapp.generated.resources.experience_section_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val ExperienceMode.titleRes: StringResource
    get() = when (this) {
        ExperienceMode.Balanced -> Res.string.experience_mode_balanced
        ExperienceMode.SportsFocus -> Res.string.experience_mode_sports
        ExperienceMode.Music -> Res.string.experience_mode_music
    }

internal val ExperienceMode.descriptionRes: StringResource
    get() = when (this) {
        ExperienceMode.Balanced -> Res.string.experience_mode_balanced_description
        ExperienceMode.SportsFocus -> Res.string.experience_mode_sports_description
        ExperienceMode.Music -> Res.string.experience_mode_music_description
    }

internal fun LazyListScope.experienceSettingsContent(isTablet: Boolean) {
    item {
        val selected by ExperienceRepository.mode.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.experience_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                ExperienceMode.selectable.forEachIndexed { index, mode ->
                    if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                    ExperienceOptionRow(
                        mode = mode,
                        selected = mode == selected,
                        isTablet = isTablet,
                        onClick = { ExperienceRepository.setMode(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperienceOptionRow(
    mode: ExperienceMode,
    selected: Boolean,
    isTablet: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 16.dp else 14.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(mode.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(mode.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
