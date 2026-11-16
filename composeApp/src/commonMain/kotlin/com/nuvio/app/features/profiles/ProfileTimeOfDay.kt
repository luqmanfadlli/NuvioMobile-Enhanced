package com.nuvio.app.features.profiles

import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.ui.ProfileMeshPalette
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal enum class ProfileTimeOfDay(
    val greeting: String,
    val palette: ProfileMeshPalette,
) {
    MORNING(
        greeting = "Good morning",
        palette = ProfileMeshPalette(
            base = Color(0xFF100B15),
            primary = Color(0xFFFFB36B),
            secondary = Color(0xFFF4778D),
            tertiary = Color(0xFF8271B8),
        ),
    ),
    AFTERNOON(
        greeting = "Good afternoon",
        palette = ProfileMeshPalette(
            base = Color(0xFF0B111A),
            primary = Color(0xFF6D9BC3),
            secondary = Color(0xFF8877BE),
            tertiary = Color(0xFF4E9191),
        ),
    ),
    EVENING(
        greeting = "Good evening",
        palette = ProfileMeshPalette(
            base = Color(0xFF060810),
            primary = Color(0xFF3D508F),
            secondary = Color(0xFF70528E),
            tertiary = Color(0xFF254C65),
        ),
    );

    companion object {
        fun forHour(hour: Int): ProfileTimeOfDay = when (hour) {
            in 0..11 -> MORNING
            in 12..17 -> AFTERNOON
            else -> EVENING
        }

        fun now(): ProfileTimeOfDay {
            val localHour = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .hour
            return forHour(localHour)
        }
    }
}
