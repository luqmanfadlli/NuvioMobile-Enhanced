package com.nuvio.app.features.player

import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SUBTITLE_DEVICE_PREFERENCES_VERSION = 1

/**
 * A versioned, profile-scoped record stored only in this application installation.
 *
 * Colors are stored as ARGB hex strings because Compose Color is not serializable.
 * SubtitleAppearance is already serializable and keeps custom-font IDs local to the device.
 */
@Serializable
private data class SubtitleDevicePreferencesRecord(
    val version: Int = SUBTITLE_DEVICE_PREFERENCES_VERSION,
    val textColor: String = SubtitleStyleState.DEFAULT.textColor.toStorageHexString(),
    val backgroundColor: String = SubtitleStyleState.DEFAULT.backgroundColor.toStorageHexString(),
    val outlineColor: String = SubtitleStyleState.DEFAULT.outlineColor.toStorageHexString(),
    val outlineEnabled: Boolean = SubtitleStyleState.DEFAULT.outlineEnabled,
    val outlineWidth: Int = SubtitleStyleState.DEFAULT.outlineWidth,
    val bold: Boolean = SubtitleStyleState.DEFAULT.bold,
    val fontSizeSp: Int = SubtitleStyleState.DEFAULT.fontSizeSp,
    val bottomOffset: Int = SubtitleStyleState.DEFAULT.bottomOffset,
    val stripSdh: Boolean = SubtitleStyleState.DEFAULT.stripSdh,
    val useForcedSubtitles: Boolean = SubtitleStyleState.DEFAULT.useForcedSubtitles,
    val showOnlyPreferredLanguages: Boolean = SubtitleStyleState.DEFAULT.showOnlyPreferredLanguages,
    val appearance: SubtitleAppearance = SubtitleAppearance(),
)

private val subtitleDevicePreferencesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun SubtitleStyleState.toDevicePreferencesRecord() = SubtitleDevicePreferencesRecord(
    textColor = textColor.toStorageHexString(),
    backgroundColor = backgroundColor.toStorageHexString(),
    outlineColor = outlineColor.toStorageHexString(),
    outlineEnabled = outlineEnabled,
    outlineWidth = outlineWidth,
    bold = bold,
    fontSizeSp = fontSizeSp,
    bottomOffset = bottomOffset,
    stripSdh = stripSdh,
    useForcedSubtitles = useForcedSubtitles,
    showOnlyPreferredLanguages = showOnlyPreferredLanguages,
    appearance = appearance.normalized(),
)

private fun SubtitleDevicePreferencesRecord.toSubtitleStyleState(): SubtitleStyleState {
    val defaults = SubtitleStyleState.DEFAULT
    return SubtitleStyleState(
        textColor = subtitleColorFromStorage(textColor) ?: defaults.textColor,
        backgroundColor = subtitleColorFromStorage(backgroundColor) ?: defaults.backgroundColor,
        outlineColor = subtitleColorFromStorage(outlineColor) ?: defaults.outlineColor,
        outlineEnabled = outlineEnabled,
        outlineWidth = outlineWidth.coerceAtLeast(0),
        bold = bold,
        fontSizeSp = fontSizeSp.coerceIn(subtitleFontSizeRangeSp),
        bottomOffset = bottomOffset.coerceIn(0, 200),
        stripSdh = stripSdh,
        useForcedSubtitles = useForcedSubtitles,
        showOnlyPreferredLanguages = showOnlyPreferredLanguages,
        appearance = appearance.normalized(),
    )
}

internal fun encodeSubtitleDevicePreferences(style: SubtitleStyleState): String =
    subtitleDevicePreferencesJson.encodeToString(style.toDevicePreferencesRecord())

internal fun decodeSubtitleDevicePreferences(value: String?): SubtitleStyleState? =
    value?.let {
        runCatching {
            val record = subtitleDevicePreferencesJson.decodeFromString<SubtitleDevicePreferencesRecord>(it)
            record.takeIf { decoded -> decoded.version in 1..SUBTITLE_DEVICE_PREFERENCES_VERSION }
                ?.toSubtitleStyleState()
        }.getOrNull()
    }

/**
 * Keeps subtitle appearance preferences local to this installation and profile.
 *
 * Existing profile-scoped player settings are migrated on first read. The legacy values
 * remain untouched so account synchronization and account cleanup cannot destroy the
 * migration source before the new record has been persisted.
 */
internal object SubtitleDevicePreferencesRepository {
    fun loadOrMigrate(profileId: Int, legacyStyle: SubtitleStyleState): SubtitleStyleState {
        decodeSubtitleDevicePreferences(SubtitleDevicePreferencesStorage.load(profileId))?.let {
            return it
        }

        val migrated = legacyStyle.normalizedForDevice()
        migrateLegacy(profileId, migrated)
        return decodeSubtitleDevicePreferences(SubtitleDevicePreferencesStorage.load(profileId)) ?: migrated
    }

    /**
     * Migrates a legacy profile once. Called for every profile before account cleanup,
     * because the platform cleaner removes the whole legacy player-settings namespace.
     */
    fun migrateLegacy(profileId: Int, legacyStyle: SubtitleStyleState) {
        if (decodeSubtitleDevicePreferences(SubtitleDevicePreferencesStorage.load(profileId)) != null) return
        val migrated = legacyStyle.normalizedForDevice()
        SubtitleDevicePreferencesStorage.saveForMigration(
            profileId,
            encodeSubtitleDevicePreferences(migrated),
        )
    }

    fun load(profileId: Int): SubtitleStyleState? =
        decodeSubtitleDevicePreferences(SubtitleDevicePreferencesStorage.load(profileId))

    fun removeProfile(profileId: Int) {
        SubtitleDevicePreferencesStorage.remove(profileId)
    }

    fun save(profileId: Int, style: SubtitleStyleState) {
        SubtitleDevicePreferencesStorage.save(profileId, encodeSubtitleDevicePreferences(style.normalizedForDevice()))
    }

    private fun SubtitleStyleState.normalizedForDevice(): SubtitleStyleState = copy(
        fontSizeSp = fontSizeSp.coerceIn(subtitleFontSizeRangeSp),
        bottomOffset = bottomOffset.coerceIn(0, 200),
        outlineWidth = outlineWidth.coerceAtLeast(0),
        appearance = appearance.normalized(),
    )
}

internal expect object SubtitleDevicePreferencesStorage {
    fun load(profileId: Int): String?
    fun save(profileId: Int, value: String)
    fun saveForMigration(profileId: Int, value: String)
    fun remove(profileId: Int)
}
