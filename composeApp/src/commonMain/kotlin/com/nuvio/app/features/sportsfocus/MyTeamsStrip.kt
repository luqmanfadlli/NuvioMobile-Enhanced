package com.nuvio.app.features.sportsfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.sports.FollowedTeam
import com.nuvio.app.features.sports.SportsRepository

/**
 * "My Teams" at the top of the Balanced Sports tab: the teams you follow as round badges, and a + to follow
 * more. With none followed yet it is an invitation to pick some. Tapping anything opens the team picker.
 */
@Composable
internal fun MyTeamsStrip(onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val follows by SportsRepository.follows.collectAsStateWithLifecycle()
    val teams = follows.teams
    if (teams.isEmpty()) {
        Column(
            modifier = modifier
                .padding(horizontal = NuvioTokens.Space.s16)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.colors.surface)
                .clickable(onClick = onChoose)
                .padding(NuvioTokens.Space.s16),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Pick your favourite teams",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
            )
            Text(
                "Follow the teams you care about and their games show up first, with alerts when they play.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
            Text(
                "Choose teams",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "My Teams",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Edit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.accent,
                modifier = Modifier.clickable(onClick = onChoose).padding(vertical = 4.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(teams, key = { it.key }) { team -> TeamBadge(team, onChoose) }
            item(key = "add-team") {
                Column(
                    modifier = Modifier.width(64.dp).clickable(onClick = onChoose),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(tokens.colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add teams", tint = tokens.colors.textPrimary, modifier = Modifier.size(26.dp))
                    }
                    Text("Add", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TeamBadge(team: FollowedTeam, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier.width(64.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            val logo = team.logo?.takeIf { it.isNotBlank() }
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = team.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            } else {
                Text(
                    team.abbr.take(3).ifBlank { team.name.take(2).uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }
        }
        Text(
            team.name,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
