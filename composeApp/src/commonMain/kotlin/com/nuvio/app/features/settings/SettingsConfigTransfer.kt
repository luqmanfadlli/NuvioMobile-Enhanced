package com.nuvio.app.features.settings

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.network.DnsProvider
import com.nuvio.app.core.network.DnsSettingsRepository
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.poster.CustomPosterUrlStorage
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncFloat
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.decodeSyncStringSet
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncFloat
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.sync.encodeSyncStringSet
import com.nuvio.app.core.ui.CardDepthStyleRepository
import com.nuvio.app.core.ui.CardDepthStyleStorage
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.PosterCardStyleStorage
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.autosync.AutoSyncPreferencesRepository
import com.nuvio.app.features.debrid.DebridProviders
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DebridSettingsStorage
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaScreenSettingsStorage
import com.nuvio.app.features.details.OmdbSettingsRepository
import com.nuvio.app.features.details.SeasonViewMode
import com.nuvio.app.features.details.SeasonViewModeStorage
import com.nuvio.app.features.discord.DiscordRpcActivityType
import com.nuvio.app.features.discord.DiscordRpcRepository
import com.nuvio.app.features.discord.DiscordRpcSettings
import com.nuvio.app.features.discord.DiscordRpcStatus
import com.nuvio.app.features.downloads.DownloadsSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsSyncService
import com.nuvio.app.features.home.HomeHeroStyle
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.SyncHomeCatalogPayload
import com.nuvio.app.features.library.LibraryDisplaySettingsRepository
import com.nuvio.app.features.library.LibraryDisplaySettingsStorage
import com.nuvio.app.features.livetv.LiveTvEpgSource
import com.nuvio.app.features.livetv.LiveTvPlaylist
import com.nuvio.app.features.livetv.LiveTvPlaylistType
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.livetv.LiveTvStalkerSettings
import com.nuvio.app.features.livetv.LiveTvXtreamSettings
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.mdblist.MdbListSettingsStorage
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.p2p.P2pSettingsStorage
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsStorage
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.search.SearchHistoryRepository
import com.nuvio.app.features.shuffle.EpisodeShuffleRepository
import com.nuvio.app.features.streams.PinnedStreamSourcesRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsStorage
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsStorage
import com.nuvio.app.features.updater.AppUpdaterPlatform
import com.nuvio.app.features.updater.UpdateChannel
import com.nuvio.app.features.updater.UpdatePreferences
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_backup_import_done
import nuvio.composeapp.generated.resources.settings_backup_import_failed
import nuvio.composeapp.generated.resources.settings_backup_import_partial
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

internal object SettingsConfigTransfer {
    private const val FORMAT = "nuvio_settings_config"
    private const val VERSION = 1
    private const val KEY_SETTINGS = "settings"
    private const val COLLECTION_KEY_PREFIX = "collection_"
    private const val DISMISSED_NEXT_UP_KEYS = "dismissedNextUpKeys"
    private const val DISCORD_APPLICATION_ID_KEY = "application_id"
    private const val KEY_INCLUDES_SENSITIVE_DATA = "includes_sensitive_data"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun exportFileName(): String {
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "nuvio-settings-$date.json"
    }

    fun export(includeCredentials: Boolean): String {
        ensureRepositoriesLoaded()
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("app_version", AppVersionConfig.VERSION_NAME)
            put("exported_at", Clock.System.now().toString())
            put(KEY_INCLUDES_SENSITIVE_DATA, includeCredentials)
            put(
                KEY_SETTINGS,
                buildJsonObject {
                    put("theme", exportThemeSettings())
                    put("poster_card_style", exportPayload(PosterCardStyleStorage.loadPayload()))
                    put("custom_poster", exportCustomPosterSettings())
                    put("card_depth_style", exportPayload(CardDepthStyleStorage.loadPayload()))
                    put("player", exportPlayerSettings(includeCredentials))
                    put("subtitle_auto_sync", exportAutoSyncSettings())
                    put("stream_badges", exportStreamBadgeSettings())
                    put("debrid", exportDebridSettings(includeCredentials))
                    put("tmdb", exportTmdbSettings(includeCredentials))
                    put("mdblist", exportMdbListSettings(includeCredentials))
                    put("omdb", exportOmdbSettings(includeCredentials))
                    put("meta_screen", exportPayload(MetaScreenSettingsStorage.loadPayload()))
                    put("details", exportDetailsSettings())
                    put("continue_watching", exportContinueWatchingSettings())
                    put("library_display", exportPayload(LibraryDisplaySettingsStorage.loadPayload()))
                    put("home", exportHomeSettings())
                    put("general", exportGeneralSettings())
                    put("p2p", exportP2pSettings())
                    put("discord_rpc", exportDiscordSettings(includeCredentials))
                    if (includeCredentials) {
                        put("live_tv", exportLiveTvSettings())
                        put("addons", exportAddons())
                    }
                    if (AppFeaturePolicy.pluginsEnabled) {
                        put("plugins", exportPlugins())
                    }
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun parse(content: String): SettingsConfigImport? {
        val root = runCatching {
            json.parseToJsonElement(content.removePrefix("\uFEFF").trim()) as? JsonObject
        }.getOrNull() ?: return null
        if (root.string("format") != FORMAT) return null
        if ((root.int("version") ?: 0) > VERSION) return null
        val settings = root[KEY_SETTINGS] as? JsonObject ?: return null
        return SettingsConfigImport(
            settings = settings,
            includesSensitiveData = root.boolean(KEY_INCLUDES_SENSITIVE_DATA) ?: root.boolean("includes_credentials") ?: false,
        )
    }

    fun startImport(settings: JsonObject) {
        if (_isImporting.value) return
        _isImporting.value = true
        scope.launch {
            val failures = try {
                importAll(settings)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                -1
            } finally {
                _isImporting.value = false
            }
            NuvioToastController.show(
                getString(
                    when {
                        failures < 0 -> Res.string.settings_backup_import_failed
                        failures > 0 -> Res.string.settings_backup_import_partial
                        else -> Res.string.settings_backup_import_done
                    },
                ),
            )
        }
    }

    private suspend fun importAll(settings: JsonObject): Int {
        ensureRepositoriesLoaded()
        var failures = 0
        suspend fun section(key: String, block: suspend (JsonElement) -> Unit) {
            val value = settings[key] ?: return
            try {
                block(value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failures += 1
            }
        }

        section("theme") { value ->
            importThemeSettings(value.jsonObject)
            ThemeSettingsRepository.onProfileChanged()
        }
        section("poster_card_style") { value ->
            value.payloadOrNull()?.let(PosterCardStyleStorage::savePayload)
            PosterCardStyleRepository.onProfileChanged()
        }
        section("custom_poster") { value -> importCustomPosterSettings(value.jsonObject) }
        section("card_depth_style") { value ->
            value.payloadOrNull()?.let(CardDepthStyleStorage::savePayload)
            CardDepthStyleRepository.onProfileChanged()
        }
        section("player") { value ->
            importPlayerSettings(value.jsonObject)
            PlayerSettingsRepository.onProfileChanged()
        }
        section("subtitle_auto_sync") { value -> importAutoSyncSettings(value.jsonObject) }
        section("stream_badges") { value ->
            importStreamBadgeSettings(value.jsonObject)
            StreamBadgeSettingsRepository.onProfileChanged()
            PinnedStreamSourcesRepository.onProfileChanged()
        }
        section("debrid") { value ->
            importDebridSettings(value.jsonObject)
            DebridSettingsRepository.onProfileChanged()
        }
        section("tmdb") { value ->
            importTmdbSettings(value.jsonObject)
            TmdbSettingsRepository.onProfileChanged()
        }
        section("mdblist") { value ->
            importMdbListSettings(value.jsonObject)
            MdbListMetadataService.clearCache()
            MdbListSettingsRepository.onProfileChanged()
        }
        section("omdb") { value -> importOmdbSettings(value.jsonObject) }
        section("meta_screen") { value ->
            value.payloadOrNull()?.let(MetaScreenSettingsStorage::savePayload)
            MetaScreenSettingsRepository.onProfileChanged()
        }
        section("details") { value -> importDetailsSettings(value.jsonObject) }
        section("continue_watching") { value -> importContinueWatchingSettings(value) }
        section("library_display") { value ->
            value.payloadOrNull()?.let(LibraryDisplaySettingsStorage::savePayload)
            LibraryDisplaySettingsRepository.onProfileChanged()
        }
        section("general") { value -> importGeneralSettings(value.jsonObject) }
        section("p2p") { value -> importP2pSettings(value.jsonObject) }
        section("discord_rpc") { value -> importDiscordSettings(value.jsonObject) }
        section("live_tv") { value -> importLiveTvSettings(value.jsonObject) }
        section("addons") { value -> importAddons(value as JsonArray) }
        if (AppFeaturePolicy.pluginsEnabled) {
            section("plugins") { value -> importPlugins(value.jsonObject) }
        }
        section("home") { value -> importHomeSettings(value.jsonObject) }
        HomeRepository.applyCurrentSettings()
        return failures
    }

    private fun ensureRepositoriesLoaded() {
        ThemeSettingsRepository.ensureLoaded()
        PosterCardStyleRepository.ensureLoaded()
        CustomPosterUrlRepository.ensureLoaded()
        CardDepthStyleRepository.ensureLoaded()
        PlayerSettingsRepository.ensureLoaded()
        AutoSyncPreferencesRepository.ensureLoaded()
        StreamBadgeSettingsRepository.ensureLoaded()
        DebridSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.ensureLoaded()
        OmdbSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.ensureLoaded()
        EpisodeShuffleRepository.ensureLoaded()
        ContinueWatchingPreferencesRepository.ensureLoaded()
        LibraryDisplaySettingsRepository.ensureLoaded()
        EpisodeReleaseNotificationsRepository.ensureLoaded()
        DownloadsSettingsRepository.ensureLoaded()
        SearchHistoryRepository.ensureLoaded()
        DnsSettingsRepository.ensureLoaded()
        P2pSettingsRepository.ensureLoaded()
        DiscordRpcRepository.ensureLoaded()
        LiveTvRepository.ensureLoaded()
    }

    private fun exportPayload(payload: String?): JsonElement =
        JsonPrimitive(payload?.trim().orEmpty())

    private fun JsonElement.payloadOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.strings(key: String): List<String> =
        (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun exportCustomPosterSettings(): JsonObject = buildJsonObject {
        put("pattern", CustomPosterUrlStorage.loadPattern().orEmpty().trim())
        CustomPosterUrlStorage.loadEnabledScreens()?.let { screens ->
            put("enabled_screens", buildJsonArray { screens.sorted().forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun importCustomPosterSettings(payload: JsonObject) {
        CustomPosterUrlStorage.savePattern(payload.string("pattern")?.trim()?.ifBlank { null })
        CustomPosterUrlStorage.saveEnabledScreens(
            (payload["enabled_screens"] as? JsonArray)?.let { payload.strings("enabled_screens").toSet() },
        )
        CustomPosterUrlRepository.onProfileChanged()
    }

    private fun exportAutoSyncSettings(): JsonObject = buildJsonObject {
        put("start_on_playback", AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart.value)
        put("aggressive_mode", AutoSyncPreferencesRepository.aggressiveMode.value)
        put("tolerance_ms", AutoSyncPreferencesRepository.syncToleranceMs.value)
    }

    private fun importAutoSyncSettings(payload: JsonObject) {
        payload.boolean("start_on_playback")?.let(AutoSyncPreferencesRepository::setPreferredSubtitleAutoSyncOnStart)
        payload.boolean("aggressive_mode")?.let(AutoSyncPreferencesRepository::setAggressiveMode)
        payload.int("tolerance_ms")?.let(AutoSyncPreferencesRepository::setSyncToleranceMs)
    }

    private fun exportOmdbSettings(includeCredentials: Boolean): JsonObject = buildJsonObject {
        if (includeCredentials) {
            OmdbSettingsRepository.apiKey.value.takeIf(String::isNotBlank)?.let { put("api_key", it) }
        }
    }

    private fun importOmdbSettings(payload: JsonObject) {
        payload.string("api_key")?.let(OmdbSettingsRepository::setApiKey)
    }

    private fun exportDetailsSettings(): JsonObject = buildJsonObject {
        put("episode_shuffle_available", EpisodeShuffleRepository.uiState.value.available)
        SeasonViewModeStorage.load()?.let { put("season_view_mode", SeasonViewMode.persist(it)) }
    }

    private fun importDetailsSettings(payload: JsonObject) {
        payload.boolean("episode_shuffle_available")?.let { available ->
            if (EpisodeShuffleRepository.uiState.value.available != available) {
                EpisodeShuffleRepository.setAvailable(available)
            }
        }
        SeasonViewMode.parse(payload.string("season_view_mode"))?.let(SeasonViewModeStorage::save)
    }

    private fun exportContinueWatchingSettings(): JsonElement {
        val payload = ContinueWatchingPreferencesStorage.loadPayload()?.trim().orEmpty()
        if (payload.isEmpty()) return JsonPrimitive("")
        val parsed = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return JsonPrimitive(payload)
        return JsonPrimitive(json.encodeToString(JsonObject.serializer(), JsonObject(parsed - DISMISSED_NEXT_UP_KEYS)))
    }

    private fun importContinueWatchingSettings(value: JsonElement) {
        val imported = value.payloadOrNull() ?: return
        val importedObject = runCatching { json.parseToJsonElement(imported) as? JsonObject }.getOrNull()
        val merged = if (importedObject == null) {
            imported
        } else {
            val localDismissed = ContinueWatchingPreferencesStorage.loadPayload()
                ?.let { local -> runCatching { json.parseToJsonElement(local) as? JsonObject }.getOrNull() }
                ?.get(DISMISSED_NEXT_UP_KEYS)
            val result = importedObject.toMutableMap()
            if (localDismissed != null) result[DISMISSED_NEXT_UP_KEYS] = localDismissed else result.remove(DISMISSED_NEXT_UP_KEYS)
            json.encodeToString(JsonObject.serializer(), JsonObject(result))
        }
        ContinueWatchingPreferencesStorage.savePayload(merged)
        ContinueWatchingPreferencesRepository.onProfileChanged()
    }

    private fun exportHomeSettings(): JsonObject {
        val state = HomeCatalogSettingsRepository.uiState.value
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        val catalogs = HomeCatalogSettingsRepository.exportToSyncPayload().let { payload ->
            payload.copy(items = payload.items.filterNot { it.isCollection || it.key.startsWith(COLLECTION_KEY_PREFIX) })
        }
        return buildJsonObject {
            put("hero_enabled", snapshot.heroEnabled)
            put("hero_style", snapshot.heroStyle.storageValue)
            put("hero_trailer_playback_enabled", state.heroTrailerPlaybackEnabled)
            put("hero_trailer_start_delay_seconds", state.heroTrailerStartDelaySeconds)
            put("hero_trailer_start_unmuted", state.heroTrailerStartUnmuted)
            put(
                "hero_sources",
                buildJsonArray {
                    snapshot.preferences
                        .filter { (key, preference) -> preference.heroSourceEnabled && !key.startsWith(COLLECTION_KEY_PREFIX) }
                        .keys
                        .sorted()
                        .forEach { add(JsonPrimitive(it)) }
                },
            )
            put("catalogs", json.encodeToJsonElement(SyncHomeCatalogPayload.serializer(), catalogs))
        }
    }

    private fun importHomeSettings(payload: JsonObject) {
        (payload["catalogs"] as? JsonObject)?.let { catalogsJson ->
            val catalogs = json.decodeFromJsonElement(SyncHomeCatalogPayload.serializer(), catalogsJson)
            HomeCatalogSettingsRepository.applyFromRemote(
                catalogs.copy(items = catalogs.items.filterNot { it.isCollection || it.key.startsWith(COLLECTION_KEY_PREFIX) }),
            )
        }
        payload.boolean("hero_enabled")?.let(HomeCatalogSettingsRepository::setHeroEnabled)
        payload.string("hero_style")?.let { HomeCatalogSettingsRepository.setHeroStyle(HomeHeroStyle.fromStorageValue(it)) }
        payload.boolean("hero_trailer_playback_enabled")?.let(HomeCatalogSettingsRepository::setHeroTrailerPlaybackEnabled)
        payload.int("hero_trailer_start_delay_seconds")?.let(HomeCatalogSettingsRepository::setHeroTrailerStartDelaySeconds)
        payload.boolean("hero_trailer_start_unmuted")?.let(HomeCatalogSettingsRepository::setHeroTrailerStartUnmuted)
        if (payload["hero_sources"] is JsonArray) {
            val heroSources = payload.strings("hero_sources").toSet()
            HomeCatalogSettingsRepository.snapshot().preferences
                .filter { (key, preference) -> preference.heroSourceEnabled && key !in heroSources }
                .keys
                .forEach { HomeCatalogSettingsRepository.setHeroSourceEnabled(it, false) }
            heroSources.forEach { HomeCatalogSettingsRepository.setHeroSourceEnabled(it, true) }
        }
        HomeCatalogSettingsSyncService.triggerPush()
    }

    private fun exportGeneralSettings(): JsonObject = buildJsonObject {
        put("episode_release_alerts_enabled", EpisodeReleaseNotificationsRepository.uiState.value.isEnabled)
        put("allow_mobile_data_downloads", DownloadsSettingsRepository.allowMobileDataDownloads.value)
        put("show_download_button", DownloadsSettingsRepository.showDownloadButton.value)
        put("search_history_enabled", SearchHistoryRepository.enabled.value)
        if (DnsSettingsRepository.isSupported) {
            put("dns_provider", DnsSettingsRepository.provider.value.storageId)
        }
        if (AppUpdaterPlatform.isSupported) {
            put("update_channel", UpdatePreferences.shared.channel.value.storedValue)
        }
    }

    private fun importGeneralSettings(payload: JsonObject) {
        payload.boolean("episode_release_alerts_enabled")?.let(EpisodeReleaseNotificationsRepository::applyFromSyncEnabled)
        payload.boolean("allow_mobile_data_downloads")?.let(DownloadsSettingsRepository::setAllowMobileDataDownloads)
        payload.boolean("show_download_button")?.let(DownloadsSettingsRepository::setShowDownloadButton)
        payload.boolean("search_history_enabled")?.let(SearchHistoryRepository::setEnabled)
        if (DnsSettingsRepository.isSupported) {
            payload.string("dns_provider")?.let { DnsSettingsRepository.setProvider(DnsProvider.fromStorage(it)) }
        }
        if (AppUpdaterPlatform.isSupported) {
            UpdateChannel.fromStoredValue(payload.string("update_channel"))?.let(UpdatePreferences.shared::setChannel)
        }
    }

    private fun exportP2pSettings(): JsonObject = buildJsonObject {
        P2pSettingsStorage.loadP2pEnabled()?.let { put("p2p_enabled", it) }
        P2pSettingsStorage.loadEnableUpload()?.let { put("enable_upload", it) }
        P2pSettingsStorage.loadHideTorrentStats()?.let { put("hide_torrent_stats", it) }
        P2pSettingsStorage.loadTorrentProfile()?.let { put("torrent_profile", it) }
        P2pSettingsStorage.loadCacheSize()?.let { put("cache_size", it) }
    }

    private fun importP2pSettings(payload: JsonObject) {
        payload.boolean("p2p_enabled")?.let(P2pSettingsStorage::saveP2pEnabled)
        payload.boolean("enable_upload")?.let(P2pSettingsStorage::saveEnableUpload)
        payload.boolean("hide_torrent_stats")?.let(P2pSettingsStorage::saveHideTorrentStats)
        payload.string("torrent_profile")?.let(P2pSettingsStorage::saveTorrentProfile)
        payload.string("cache_size")?.let(P2pSettingsStorage::saveCacheSize)
        P2pSettingsRepository.onProfileChanged()
    }

    private fun exportDiscordSettings(includeCredentials: Boolean): JsonElement {
        val settings = json.encodeToJsonElement(DiscordRpcSettings.serializer(), DiscordRpcRepository.uiState.value.settings).jsonObject
        return if (includeCredentials) settings else JsonObject(settings - DISCORD_APPLICATION_ID_KEY)
    }

    private fun importDiscordSettings(payload: JsonObject) {
        val settings = json.decodeFromJsonElement(DiscordRpcSettings.serializer(), payload)
        DiscordRpcRepository.setActivityType(DiscordRpcActivityType.fromValue(settings.activityType))
        DiscordRpcRepository.setStatus(DiscordRpcStatus.fromValue(settings.status))
        DiscordRpcRepository.setShowTitleAsName(settings.showTitleAsName)
        DiscordRpcRepository.setShowPoster(settings.showPoster)
        DiscordRpcRepository.setShowPlaybackState(settings.showPlaybackState)
        DiscordRpcRepository.setShowTimestamps(settings.showTimestamps)
        DiscordRpcRepository.setHideOnPause(settings.hideOnPause)
        DiscordRpcRepository.setShowImdbButton(settings.showImdbButton)
        if (payload.containsKey(DISCORD_APPLICATION_ID_KEY)) {
            DiscordRpcRepository.setApplicationId(settings.applicationId)
        }
        DiscordRpcRepository.setEnabled(settings.enabled)
    }

    private fun exportLiveTvSettings(): JsonObject {
        val state = LiveTvRepository.uiState.value
        return buildJsonObject {
            put("navigation_enabled", state.isNavigationEnabled)
            put("epg_auto_detect_enabled", state.isEpgAutoDetectEnabled)
            put(
                "playlists",
                buildJsonArray {
                    state.playlists.forEach { playlist ->
                        add(
                            buildJsonObject {
                                put("name", playlist.name)
                                put("type", playlist.type.name)
                                put("source", playlist.source)
                                put("enabled", playlist.isEnabled)
                            },
                        )
                    }
                },
            )
            put(
                "epg_sources",
                buildJsonArray {
                    state.epgSources.forEach { source ->
                        add(
                            buildJsonObject {
                                put("name", source.name)
                                put("url", source.url)
                                put("enabled", source.isEnabled)
                            },
                        )
                    }
                },
            )
            state.stalkerSettings.takeIf { it.isConfigured }?.let { stalker ->
                put(
                    "stalker",
                    buildJsonObject {
                        put("portal_url", stalker.portalUrl)
                        put("mac_address", stalker.macAddress)
                        put("username", stalker.username)
                        put("password", stalker.password)
                        put("enabled", stalker.isEnabled)
                    },
                )
            }
            state.xtreamSettings.takeIf { it.isConfigured }?.let { xtream ->
                put(
                    "xtream",
                    buildJsonObject {
                        put("server_url", xtream.serverUrl)
                        put("username", xtream.username)
                        put("password", xtream.password)
                        put("enabled", xtream.isEnabled)
                    },
                )
            }
        }
    }

    private fun importLiveTvSettings(payload: JsonObject) {
        payload.objects("playlists").forEach { item ->
            val source = item.string("source")?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
            val type = LiveTvPlaylistType.entries.firstOrNull { it.name == item.string("type") } ?: LiveTvPlaylistType.Url
            val name = item.string("name")?.trim()?.takeIf(String::isNotEmpty)
            val matches: (LiveTvPlaylist) -> Boolean = { playlist ->
                playlist.type == type && playlist.source.trim().equals(source, ignoreCase = type == LiveTvPlaylistType.Url)
            }
            if (LiveTvRepository.uiState.value.playlists.none(matches)) {
                when (type) {
                    LiveTvPlaylistType.Url -> LiveTvRepository.addPlaylistUrl(name, source)
                    LiveTvPlaylistType.LocalFile -> LiveTvRepository.addLocalPlaylist(name, null, source)
                }
            }
            val enabled = item.boolean("enabled") ?: true
            LiveTvRepository.uiState.value.playlists.lastOrNull(matches)
                ?.takeIf { it.isEnabled != enabled }
                ?.let { LiveTvRepository.setPlaylistEnabled(it.id, enabled) }
        }
        payload.objects("epg_sources").forEach { item ->
            val url = item.string("url")?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
            val matches: (LiveTvEpgSource) -> Boolean = { it.url.equals(url, ignoreCase = true) }
            if (LiveTvRepository.uiState.value.epgSources.none(matches)) {
                LiveTvRepository.addEpgSource(item.string("name"), url)
            }
            val enabled = item.boolean("enabled") ?: true
            LiveTvRepository.uiState.value.epgSources.firstOrNull(matches)
                ?.takeIf { it.isEnabled != enabled }
                ?.let { LiveTvRepository.setEpgSourceEnabled(it.id, enabled) }
        }
        (payload["stalker"] as? JsonObject)?.let { stalker ->
            LiveTvStalkerSettings(
                portalUrl = stalker.string("portal_url").orEmpty(),
                macAddress = stalker.string("mac_address").orEmpty(),
                username = stalker.string("username").orEmpty(),
                password = stalker.string("password").orEmpty(),
                isEnabled = stalker.boolean("enabled") ?: true,
            ).takeIf { it.isConfigured }?.let(LiveTvRepository::saveStalkerSettings)
        }
        (payload["xtream"] as? JsonObject)?.let { xtream ->
            LiveTvXtreamSettings(
                serverUrl = xtream.string("server_url").orEmpty(),
                username = xtream.string("username").orEmpty(),
                password = xtream.string("password").orEmpty(),
                isEnabled = xtream.boolean("enabled") ?: true,
            ).takeIf { it.isConfigured }?.let(LiveTvRepository::saveXtreamSettings)
        }
        payload.boolean("epg_auto_detect_enabled")?.let(LiveTvRepository::setEpgAutoDetectEnabled)
        payload.boolean("navigation_enabled")?.let(LiveTvRepository::setNavigationEnabled)
    }

    private fun exportAddons(): JsonArray = buildJsonArray {
        AddonRepository.uiState.value.addons.distinctBy { it.manifestUrl }.forEach { addon ->
            add(
                buildJsonObject {
                    put("url", addon.manifestUrl)
                    put("enabled", addon.enabled)
                },
            )
        }
    }

    private suspend fun importAddons(items: JsonArray) {
        val entries = items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            url to (item.boolean("enabled") ?: true)
        }.distinctBy { it.first }
        entries.forEach { (url, _) ->
            if (AddonRepository.uiState.value.addons.none { it.manifestUrl == url }) {
                AddonRepository.addAddon(url)
            }
        }
        entries.forEach { (url, enabled) ->
            AddonRepository.uiState.value.addons.firstOrNull { it.manifestUrl == url }
                ?.takeIf { it.enabled != enabled }
                ?.let { AddonRepository.setAddonEnabled(url, enabled) }
        }
        var targetIndex = 0
        entries.forEach { (url, _) ->
            val currentIndex = AddonRepository.uiState.value.addons.indexOfFirst { it.manifestUrl == url }
            if (currentIndex < 0) return@forEach
            if (currentIndex != targetIndex) {
                AddonRepository.moveAddon(currentIndex, targetIndex)
            }
            targetIndex += 1
        }
    }

    private fun exportPlugins(): JsonObject {
        val state = PluginRepository.uiState.value
        return buildJsonObject {
            put("plugins_enabled", state.pluginsEnabled)
            put("group_streams_by_repository", state.groupStreamsByRepository)
            put(
                "repositories",
                buildJsonArray {
                    state.repositories.map { it.manifestUrl }.distinct().forEach { add(JsonPrimitive(it)) }
                },
            )
            put(
                "scrapers",
                buildJsonObject {
                    state.scrapers.forEach { scraper -> put(scraper.id, scraper.enabled) }
                },
            )
        }
    }

    private suspend fun importPlugins(payload: JsonObject) {
        payload.strings("repositories").map(String::trim).filter(String::isNotEmpty).distinct().forEach { url ->
            if (PluginRepository.uiState.value.repositories.none { it.manifestUrl == url }) {
                PluginRepository.addRepository(url)
            }
        }
        (payload["scrapers"] as? JsonObject)?.forEach { (id, value) ->
            val enabled = (value as? JsonPrimitive)?.booleanOrNull ?: return@forEach
            PluginRepository.uiState.value.scrapers.firstOrNull { it.id == id }
                ?.takeIf { it.enabled != enabled }
                ?.let { PluginRepository.toggleScraper(id, enabled) }
        }
        payload.boolean("group_streams_by_repository")?.let(PluginRepository::setGroupStreamsByRepository)
        payload.boolean("plugins_enabled")?.let(PluginRepository::setPluginsEnabled)
    }

    private fun exportPlayerSettings(includeCredentials: Boolean): JsonObject = buildJsonObject {
        PlayerSettingsStorage.loadUseLegacyPlayerLayout()?.let { put("use_legacy_player_layout", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadShowLoadingOverlay()?.let { put("show_loading_overlay", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadShowPlayerLoadingStatus()?.let { put("show_player_loading_status", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadPauseOverlayEnabled()?.let { put("pause_overlay_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadShowParentalGuide()?.let { put("show_parental_guide", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadResizeMode()?.let { put("resize_mode", encodeSyncString(it)) }
        PlayerSettingsStorage.loadHoldToSpeedEnabled()?.let { put("hold_to_speed_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadHoldToSpeedValue()?.let { put("hold_to_speed_value", encodeSyncFloat(it)) }
        PlayerSettingsStorage.loadSkipBackwardSeconds()?.let { put("skip_backward_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadSkipForwardSeconds()?.let { put("skip_forward_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadTouchGesturesEnabled()?.let { put("touch_gestures_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadSwipeToSeekEnabled()?.let { put("swipe_to_seek_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadSeekPreviewEnabled()?.let { put("seek_preview_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadMovieRecommendationsEnabled()?.let { put("movie_recommendations_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadAutoShowSubtitlesOnRewindEnabled()?.let { put("auto_show_subtitles_on_rewind_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadAutoShowSubtitlesOnMuteEnabled()?.let { put("auto_show_subtitles_on_mute_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadExternalPlayerEnabled()?.let { put("external_player_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadExternalPlayerForwardSubtitles()?.let { put("external_player_forward_subtitles", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadExternalPlayerSendSkipSegments()?.let { put("external_player_send_skip_segments", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadExternalPlayerId()?.let { put("external_player_id", encodeSyncString(it)) }
        PlayerSettingsStorage.loadPreferredAudioLanguage()?.let { put("preferred_audio_language", encodeSyncString(it)) }
        PlayerSettingsStorage.loadSecondaryPreferredAudioLanguage()?.let { put("secondary_preferred_audio_language", encodeSyncString(it)) }
        PlayerSettingsStorage.loadPreferredSubtitleLanguage()?.let { put("preferred_subtitle_language", encodeSyncString(it)) }
        PlayerSettingsStorage.loadSecondaryPreferredSubtitleLanguage()?.let { put("secondary_preferred_subtitle_language", encodeSyncString(it)) }
        PlayerSettingsStorage.loadSubtitleTextColor()?.let { put("subtitle_text_color", encodeSyncString(it)) }
        PlayerSettingsStorage.loadSubtitleBackgroundColor()?.let { put("subtitle_background_color", encodeSyncString(it)) }
        PlayerSettingsStorage.loadSubtitleOutlineColor()?.let { put("subtitle_outline_color", encodeSyncString(it)) }
        PlayerSettingsStorage.loadSubtitleOutlineEnabled()?.let { put("subtitle_outline_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadSubtitleOutlineWidth()?.let { put("subtitle_outline_width", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadSubtitleShadowEnabled()?.let { put("subtitle_shadow_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadSubtitleShadowStrength()?.let { put("subtitle_shadow_strength", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadSubtitleBold()?.let { put("subtitle_bold", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadSubtitleFontSizeSp()?.let { put("subtitle_font_size_sp", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadSubtitleBottomOffset()?.let { put("subtitle_bottom_offset", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadSubtitleStripSdh()?.let { put("subtitle_strip_sdh", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadSubtitleUseForcedSubtitles()?.let { put("subtitle_use_forced_subtitles", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadSubtitleShowOnlyPreferredLanguages()?.let { put("subtitle_show_only_preferred_languages", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadStreamReuseLastLinkEnabled()?.let { put("stream_reuse_last_link_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadStreamReuseLastLinkCacheHours()?.let { put("stream_reuse_last_link_cache_hours", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadAndroidPlaybackEngine()?.let { put("android_playback_engine", encodeSyncString(it)) }
        PlayerSettingsStorage.loadAndroidLibmpvVideoOutput()?.let { put("android_libmpv_video_output", encodeSyncString(it)) }
        PlayerSettingsStorage.loadAndroidLibmpvHardwareDecodingEnabled()?.let { put("android_libmpv_hardware_decoding_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadAndroidLibmpvYuv420pEnabled()?.let { put("android_libmpv_yuv420p_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadDecoderPriority()?.let { put("decoder_priority", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadMapDV7ToHevc()?.let { put("map_d_v7_to_hevc", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadTunnelingEnabled()?.let { put("tunneling_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadAndroidAudioPassthroughEnabled()?.let { put("android_audio_passthrough_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadExoNativeMemoryEnabled()?.let { put("exo_native_memory_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadCustomPlaybackBuffersEnabled()?.let { put("custom_playback_buffers_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadVodDiskCacheEnabled()?.let { put("vod_disk_cache_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadVodDiskCacheAutoSize()?.let { put("vod_disk_cache_auto_size", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadPlaybackMinBufferSeconds()?.let { put("playback_min_buffer_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadPlaybackMaxBufferSeconds()?.let { put("playback_max_buffer_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadPlaybackStartBufferSeconds()?.let { put("playback_start_buffer_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadPlaybackRebufferSeconds()?.let { put("playback_rebuffer_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadPlaybackBackBufferSeconds()?.let { put("playback_back_buffer_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadPlaybackTargetBufferMb()?.let { put("playback_target_buffer_mb", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadVodDiskCacheSizeMb()?.let { put("vod_disk_cache_size_mb", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadStreamAutoPlayMode()?.let { put("stream_auto_play_mode", encodeSyncString(it)) }
        PlayerSettingsStorage.loadStreamAutoPlaySource()?.let { put("stream_auto_play_source", encodeSyncString(it)) }
        PlayerSettingsStorage.loadStreamAutoPlaySelectedAddons()?.let { put("stream_auto_play_selected_addons", encodeSyncStringSet(it)) }
        PlayerSettingsStorage.loadStreamAutoPlaySelectedPlugins()?.let { put("stream_auto_play_selected_plugins", encodeSyncStringSet(it)) }
        PlayerSettingsStorage.loadStreamAutoPlayRegex()?.let { put("stream_auto_play_regex", encodeSyncString(it)) }
        PlayerSettingsStorage.loadStreamAutoPlayTimeoutSeconds()?.let { put("stream_auto_play_timeout_seconds", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadSkipIntroEnabled()?.let { put("skip_intro_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadAutoSkipMovieCredits()?.let { put("auto_skip_movie_credits", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadAutoSkipSegmentTypes()?.let { put("auto_skip_segment_types", encodeSyncStringSet(it)) }
        PlayerSettingsStorage.loadAutoSkipPostCredits()?.let { put("auto_skip_post_credits", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadAnimeSkipEnabled()?.let { put("anime_skip_enabled", encodeSyncBoolean(it)) }
        if (includeCredentials) PlayerSettingsStorage.loadAnimeSkipClientId()?.let { put("anime_skip_client_id", encodeSyncString(it)) }
        if (includeCredentials) PlayerSettingsStorage.loadIntroDbApiKey()?.let { put("intro_db_api_key", encodeSyncString(it)) }
        PlayerSettingsStorage.loadIntroSubmitEnabled()?.let { put("intro_submit_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadStreamAutoPlayNextEpisodeEnabled()?.let { put("stream_auto_play_next_episode_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadStreamAutoPlayNextEpisodeFallbackEnabled()?.let { put("stream_auto_play_next_episode_fallback_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadStreamAutoPlayPreferBingeGroup()?.let { put("stream_auto_play_prefer_binge_group", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadStreamAutoPlayReuseBingeGroup()?.let { put("stream_auto_play_reuse_binge_group", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadNextEpisodeThresholdMode()?.let { put("next_episode_threshold_mode", encodeSyncString(it)) }
        PlayerSettingsStorage.loadNextEpisodeThresholdPercent()?.let { put("next_episode_threshold_percent", encodeSyncFloat(it)) }
        PlayerSettingsStorage.loadNextEpisodeThresholdMinutesBeforeEnd()?.let { put("next_episode_threshold_minutes_before_end", encodeSyncFloat(it)) }
        PlayerSettingsStorage.loadPreloadNextEpisodeSources()?.let { put("preload_next_episode_sources", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadMiniPlayerEnabled()?.let { put("mini_player_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadPrivateViewingEnabled()?.let { put("private_viewing_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadUseLibass()?.let { put("use_libass", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadLibassRenderType()?.let { put("libass_render_type", encodeSyncString(it)) }
        PlayerSettingsStorage.loadUseLibmpvAss()?.let { put("use_libmpv_ass", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadIosVideoOutputPreset()?.let { put("ios_video_output_preset", encodeSyncString(it)) }
        PlayerSettingsStorage.loadIosToneMappingMode()?.let { put("ios_tone_mapping_mode", encodeSyncString(it)) }
        PlayerSettingsStorage.loadIosTargetPrimaries()?.let { put("ios_target_primaries", encodeSyncString(it)) }
        PlayerSettingsStorage.loadIosTargetTransfer()?.let { put("ios_target_transfer", encodeSyncString(it)) }
        PlayerSettingsStorage.loadIosHardwareDecoderMode()?.let { put("ios_hardware_decoder_mode", encodeSyncString(it)) }
        PlayerSettingsStorage.loadIosAudioOutputMode()?.let { put("ios_audio_output_mode", encodeSyncString(it)) }
        PlayerSettingsStorage.loadIosExtendedDynamicRangeEnabled()?.let { put("ios_extended_dynamic_range_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadIosTargetColorspaceHintEnabled()?.let { put("ios_target_colorspace_hint_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadIosHdrComputePeakEnabled()?.let { put("ios_hdr_compute_peak_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadIosDebandEnabled()?.let { put("ios_deband_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadIosInterpolationEnabled()?.let { put("ios_interpolation_enabled", encodeSyncBoolean(it)) }
        PlayerSettingsStorage.loadIosBrightness()?.let { put("ios_brightness", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadIosContrast()?.let { put("ios_contrast", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadIosSaturation()?.let { put("ios_saturation", encodeSyncInt(it)) }
        PlayerSettingsStorage.loadIosGamma()?.let { put("ios_gamma", encodeSyncInt(it)) }
    }

    private fun importPlayerSettings(payload: JsonObject) {
        payload.decodeSyncBoolean("use_legacy_player_layout")?.let { PlayerSettingsStorage.saveUseLegacyPlayerLayout(it) }
        payload.decodeSyncBoolean("show_loading_overlay")?.let { PlayerSettingsStorage.saveShowLoadingOverlay(it) }
        payload.decodeSyncBoolean("show_player_loading_status")?.let { PlayerSettingsStorage.saveShowPlayerLoadingStatus(it) }
        payload.decodeSyncBoolean("pause_overlay_enabled")?.let { PlayerSettingsStorage.savePauseOverlayEnabled(it) }
        payload.decodeSyncBoolean("show_parental_guide")?.let { PlayerSettingsStorage.saveShowParentalGuide(it) }
        payload.decodeSyncString("resize_mode")?.let { PlayerSettingsStorage.saveResizeMode(it) }
        payload.decodeSyncBoolean("hold_to_speed_enabled")?.let { PlayerSettingsStorage.saveHoldToSpeedEnabled(it) }
        payload.decodeSyncFloat("hold_to_speed_value")?.let { PlayerSettingsStorage.saveHoldToSpeedValue(it) }
        payload.decodeSyncInt("skip_backward_seconds")?.let { PlayerSettingsStorage.saveSkipBackwardSeconds(it) }
        payload.decodeSyncInt("skip_forward_seconds")?.let { PlayerSettingsStorage.saveSkipForwardSeconds(it) }
        payload.decodeSyncBoolean("touch_gestures_enabled")?.let { PlayerSettingsStorage.saveTouchGesturesEnabled(it) }
        payload.decodeSyncBoolean("swipe_to_seek_enabled")?.let { PlayerSettingsStorage.saveSwipeToSeekEnabled(it) }
        payload.decodeSyncBoolean("seek_preview_enabled")?.let { PlayerSettingsStorage.saveSeekPreviewEnabled(it) }
        payload.decodeSyncBoolean("movie_recommendations_enabled")?.let { PlayerSettingsStorage.saveMovieRecommendationsEnabled(it) }
        payload.decodeSyncBoolean("auto_show_subtitles_on_rewind_enabled")?.let { PlayerSettingsStorage.saveAutoShowSubtitlesOnRewindEnabled(it) }
        payload.decodeSyncBoolean("auto_show_subtitles_on_mute_enabled")?.let { PlayerSettingsStorage.saveAutoShowSubtitlesOnMuteEnabled(it) }
        payload.decodeSyncBoolean("external_player_enabled")?.let { PlayerSettingsStorage.saveExternalPlayerEnabled(it) }
        payload.decodeSyncBoolean("external_player_forward_subtitles")?.let { PlayerSettingsStorage.saveExternalPlayerForwardSubtitles(it) }
        payload.decodeSyncBoolean("external_player_send_skip_segments")?.let { PlayerSettingsStorage.saveExternalPlayerSendSkipSegments(it) }
        payload.decodeSyncString("external_player_id")?.let { PlayerSettingsStorage.saveExternalPlayerId(it) }
        payload.decodeSyncString("preferred_audio_language")?.let { PlayerSettingsStorage.savePreferredAudioLanguage(it) }
        payload.decodeSyncString("secondary_preferred_audio_language")?.let { PlayerSettingsStorage.saveSecondaryPreferredAudioLanguage(it) }
        payload.decodeSyncString("preferred_subtitle_language")?.let { PlayerSettingsStorage.savePreferredSubtitleLanguage(it) }
        payload.decodeSyncString("secondary_preferred_subtitle_language")?.let { PlayerSettingsStorage.saveSecondaryPreferredSubtitleLanguage(it) }
        payload.decodeSyncString("subtitle_text_color")?.let { PlayerSettingsStorage.saveSubtitleTextColor(it) }
        payload.decodeSyncString("subtitle_background_color")?.let { PlayerSettingsStorage.saveSubtitleBackgroundColor(it) }
        payload.decodeSyncString("subtitle_outline_color")?.let { PlayerSettingsStorage.saveSubtitleOutlineColor(it) }
        payload.decodeSyncBoolean("subtitle_outline_enabled")?.let { PlayerSettingsStorage.saveSubtitleOutlineEnabled(it) }
        payload.decodeSyncInt("subtitle_outline_width")?.let { PlayerSettingsStorage.saveSubtitleOutlineWidth(it) }
        payload.decodeSyncBoolean("subtitle_shadow_enabled")?.let { PlayerSettingsStorage.saveSubtitleShadowEnabled(it) }
        payload.decodeSyncInt("subtitle_shadow_strength")?.let { PlayerSettingsStorage.saveSubtitleShadowStrength(it) }
        payload.decodeSyncBoolean("subtitle_bold")?.let { PlayerSettingsStorage.saveSubtitleBold(it) }
        payload.decodeSyncInt("subtitle_font_size_sp")?.let { PlayerSettingsStorage.saveSubtitleFontSizeSp(it) }
        payload.decodeSyncInt("subtitle_bottom_offset")?.let { PlayerSettingsStorage.saveSubtitleBottomOffset(it) }
        payload.decodeSyncBoolean("subtitle_strip_sdh")?.let { PlayerSettingsStorage.saveSubtitleStripSdh(it) }
        payload.decodeSyncBoolean("subtitle_use_forced_subtitles")?.let { PlayerSettingsStorage.saveSubtitleUseForcedSubtitles(it) }
        payload.decodeSyncBoolean("subtitle_show_only_preferred_languages")?.let { PlayerSettingsStorage.saveSubtitleShowOnlyPreferredLanguages(it) }
        payload.decodeSyncBoolean("stream_reuse_last_link_enabled")?.let { PlayerSettingsStorage.saveStreamReuseLastLinkEnabled(it) }
        payload.decodeSyncInt("stream_reuse_last_link_cache_hours")?.let { PlayerSettingsStorage.saveStreamReuseLastLinkCacheHours(it) }
        payload.decodeSyncString("android_playback_engine")?.let { PlayerSettingsStorage.saveAndroidPlaybackEngine(it) }
        payload.decodeSyncString("android_libmpv_video_output")?.let { PlayerSettingsStorage.saveAndroidLibmpvVideoOutput(it) }
        payload.decodeSyncBoolean("android_libmpv_hardware_decoding_enabled")?.let { PlayerSettingsStorage.saveAndroidLibmpvHardwareDecodingEnabled(it) }
        payload.decodeSyncBoolean("android_libmpv_yuv420p_enabled")?.let { PlayerSettingsStorage.saveAndroidLibmpvYuv420pEnabled(it) }
        payload.decodeSyncInt("decoder_priority")?.let { PlayerSettingsStorage.saveDecoderPriority(it) }
        payload.decodeSyncBoolean("map_d_v7_to_hevc")?.let { PlayerSettingsStorage.saveMapDV7ToHevc(it) }
        payload.decodeSyncBoolean("tunneling_enabled")?.let { PlayerSettingsStorage.saveTunnelingEnabled(it) }
        payload.decodeSyncBoolean("android_audio_passthrough_enabled")?.let { PlayerSettingsStorage.saveAndroidAudioPassthroughEnabled(it) }
        payload.decodeSyncBoolean("exo_native_memory_enabled")?.let { PlayerSettingsStorage.saveExoNativeMemoryEnabled(it) }
        payload.decodeSyncBoolean("custom_playback_buffers_enabled")?.let { PlayerSettingsStorage.saveCustomPlaybackBuffersEnabled(it) }
        payload.decodeSyncBoolean("vod_disk_cache_enabled")?.let { PlayerSettingsStorage.saveVodDiskCacheEnabled(it) }
        payload.decodeSyncBoolean("vod_disk_cache_auto_size")?.let { PlayerSettingsStorage.saveVodDiskCacheAutoSize(it) }
        payload.decodeSyncInt("playback_min_buffer_seconds")?.let { PlayerSettingsStorage.savePlaybackMinBufferSeconds(it) }
        payload.decodeSyncInt("playback_max_buffer_seconds")?.let { PlayerSettingsStorage.savePlaybackMaxBufferSeconds(it) }
        payload.decodeSyncInt("playback_start_buffer_seconds")?.let { PlayerSettingsStorage.savePlaybackStartBufferSeconds(it) }
        payload.decodeSyncInt("playback_rebuffer_seconds")?.let { PlayerSettingsStorage.savePlaybackRebufferSeconds(it) }
        payload.decodeSyncInt("playback_back_buffer_seconds")?.let { PlayerSettingsStorage.savePlaybackBackBufferSeconds(it) }
        payload.decodeSyncInt("playback_target_buffer_mb")?.let { PlayerSettingsStorage.savePlaybackTargetBufferMb(it) }
        payload.decodeSyncInt("vod_disk_cache_size_mb")?.let { PlayerSettingsStorage.saveVodDiskCacheSizeMb(it) }
        payload.decodeSyncString("stream_auto_play_mode")?.let { PlayerSettingsStorage.saveStreamAutoPlayMode(it) }
        payload.decodeSyncString("stream_auto_play_source")?.let { PlayerSettingsStorage.saveStreamAutoPlaySource(it) }
        payload.decodeSyncStringSet("stream_auto_play_selected_addons")?.let { PlayerSettingsStorage.saveStreamAutoPlaySelectedAddons(it) }
        payload.decodeSyncStringSet("stream_auto_play_selected_plugins")?.let { PlayerSettingsStorage.saveStreamAutoPlaySelectedPlugins(it) }
        payload.decodeSyncString("stream_auto_play_regex")?.let { PlayerSettingsStorage.saveStreamAutoPlayRegex(it) }
        payload.decodeSyncInt("stream_auto_play_timeout_seconds")?.let { PlayerSettingsStorage.saveStreamAutoPlayTimeoutSeconds(it) }
        payload.decodeSyncBoolean("skip_intro_enabled")?.let { PlayerSettingsStorage.saveSkipIntroEnabled(it) }
        payload.decodeSyncBoolean("auto_skip_movie_credits")?.let { PlayerSettingsStorage.saveAutoSkipMovieCredits(it) }
        payload.decodeSyncStringSet("auto_skip_segment_types")?.let { PlayerSettingsStorage.saveAutoSkipSegmentTypes(it) }
        payload.decodeSyncBoolean("auto_skip_post_credits")?.let { PlayerSettingsStorage.saveAutoSkipPostCredits(it) }
        payload.decodeSyncBoolean("anime_skip_enabled")?.let { PlayerSettingsStorage.saveAnimeSkipEnabled(it) }
        payload.decodeSyncString("anime_skip_client_id")?.let { PlayerSettingsStorage.saveAnimeSkipClientId(it) }
        payload.decodeSyncString("intro_db_api_key")?.let { PlayerSettingsStorage.saveIntroDbApiKey(it) }
        payload.decodeSyncBoolean("intro_submit_enabled")?.let { PlayerSettingsStorage.saveIntroSubmitEnabled(it) }
        payload.decodeSyncBoolean("stream_auto_play_next_episode_enabled")?.let { PlayerSettingsStorage.saveStreamAutoPlayNextEpisodeEnabled(it) }
        payload.decodeSyncBoolean("stream_auto_play_next_episode_fallback_enabled")?.let { PlayerSettingsStorage.saveStreamAutoPlayNextEpisodeFallbackEnabled(it) }
        payload.decodeSyncBoolean("stream_auto_play_prefer_binge_group")?.let { PlayerSettingsStorage.saveStreamAutoPlayPreferBingeGroup(it) }
        payload.decodeSyncBoolean("stream_auto_play_reuse_binge_group")?.let { PlayerSettingsStorage.saveStreamAutoPlayReuseBingeGroup(it) }
        payload.decodeSyncString("next_episode_threshold_mode")?.let { PlayerSettingsStorage.saveNextEpisodeThresholdMode(it) }
        payload.decodeSyncFloat("next_episode_threshold_percent")?.let { PlayerSettingsStorage.saveNextEpisodeThresholdPercent(it) }
        payload.decodeSyncFloat("next_episode_threshold_minutes_before_end")?.let { PlayerSettingsStorage.saveNextEpisodeThresholdMinutesBeforeEnd(it) }
        payload.decodeSyncBoolean("preload_next_episode_sources")?.let { PlayerSettingsStorage.savePreloadNextEpisodeSources(it) }
        payload.decodeSyncBoolean("mini_player_enabled")?.let { PlayerSettingsStorage.saveMiniPlayerEnabled(it) }
        payload.decodeSyncBoolean("private_viewing_enabled")?.let { PlayerSettingsStorage.savePrivateViewingEnabled(it) }
        payload.decodeSyncBoolean("use_libass")?.let { PlayerSettingsStorage.saveUseLibass(it) }
        payload.decodeSyncString("libass_render_type")?.let { PlayerSettingsStorage.saveLibassRenderType(it) }
        payload.decodeSyncBoolean("use_libmpv_ass")?.let { PlayerSettingsStorage.saveUseLibmpvAss(it) }
        payload.decodeSyncString("ios_video_output_preset")?.let { PlayerSettingsStorage.saveIosVideoOutputPreset(it) }
        payload.decodeSyncString("ios_tone_mapping_mode")?.let { PlayerSettingsStorage.saveIosToneMappingMode(it) }
        payload.decodeSyncString("ios_target_primaries")?.let { PlayerSettingsStorage.saveIosTargetPrimaries(it) }
        payload.decodeSyncString("ios_target_transfer")?.let { PlayerSettingsStorage.saveIosTargetTransfer(it) }
        payload.decodeSyncString("ios_hardware_decoder_mode")?.let { PlayerSettingsStorage.saveIosHardwareDecoderMode(it) }
        payload.decodeSyncString("ios_audio_output_mode")?.let { PlayerSettingsStorage.saveIosAudioOutputMode(it) }
        payload.decodeSyncBoolean("ios_extended_dynamic_range_enabled")?.let { PlayerSettingsStorage.saveIosExtendedDynamicRangeEnabled(it) }
        payload.decodeSyncBoolean("ios_target_colorspace_hint_enabled")?.let { PlayerSettingsStorage.saveIosTargetColorspaceHintEnabled(it) }
        payload.decodeSyncBoolean("ios_hdr_compute_peak_enabled")?.let { PlayerSettingsStorage.saveIosHdrComputePeakEnabled(it) }
        payload.decodeSyncBoolean("ios_deband_enabled")?.let { PlayerSettingsStorage.saveIosDebandEnabled(it) }
        payload.decodeSyncBoolean("ios_interpolation_enabled")?.let { PlayerSettingsStorage.saveIosInterpolationEnabled(it) }
        payload.decodeSyncInt("ios_brightness")?.let { PlayerSettingsStorage.saveIosBrightness(it) }
        payload.decodeSyncInt("ios_contrast")?.let { PlayerSettingsStorage.saveIosContrast(it) }
        payload.decodeSyncInt("ios_saturation")?.let { PlayerSettingsStorage.saveIosSaturation(it) }
        payload.decodeSyncInt("ios_gamma")?.let { PlayerSettingsStorage.saveIosGamma(it) }
    }

    private fun exportThemeSettings(): JsonObject = buildJsonObject {
        ThemeSettingsStorage.loadSelectedTheme()?.let { put("selected_theme", encodeSyncString(it)) }
        ThemeSettingsStorage.loadCustomThemeColors()?.let { put("custom_theme_colors", encodeSyncString(it)) }
        ThemeSettingsStorage.loadAmoledEnabled()?.let { put("amoled_enabled", encodeSyncBoolean(it)) }
        ThemeSettingsStorage.loadLiquidGlassNativeTabBarEnabled()?.let { put("liquid_glass_native_tab_bar_enabled", encodeSyncBoolean(it)) }
        ThemeSettingsStorage.loadTabBarBehavior()?.let { put("tab_bar_behavior", encodeSyncString(it)) }
        ThemeSettingsStorage.loadDynamicArtworkBackgroundEnabled()?.let { put("dynamic_artwork_background_enabled", encodeSyncBoolean(it)) }
        ThemeSettingsStorage.loadShowCatalogAccentEnabled()?.let { put("show_catalog_accent_enabled", encodeSyncBoolean(it)) }
        ThemeSettingsStorage.loadSelectedAppLanguage()?.let { put("selected_app_language", encodeSyncString(it)) }
        ThemeSettingsStorage.loadNavBarStyle()?.let { put("nav_bar_style", encodeSyncString(it)) }
        ThemeSettingsStorage.loadNavBarPosition()?.let { put("nav_bar_position", encodeSyncString(it)) }
        ThemeSettingsStorage.loadNavBarGlowEnabled()?.let { put("nav_bar_glow_enabled", encodeSyncBoolean(it)) }
        ThemeSettingsStorage.loadNavBarDefaultIconColorEnabled()?.let { put("nav_bar_default_icon_color_enabled", encodeSyncBoolean(it)) }
        ThemeSettingsStorage.loadNavBarDefaultIconColor()?.let { put("nav_bar_default_icon_color", encodeSyncString(it)) }
        ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects()?.let { put("nav_bar_liquid_glass_bar_effects", encodeSyncString(it)) }
        ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects()?.let { put("nav_bar_liquid_glass_selection_effects", encodeSyncString(it)) }
        ThemeSettingsStorage.loadNavBarGlass()?.let { put("nav_bar_glass", encodeSyncString(it)) }
        ThemeSettingsStorage.loadLiquidGlassEffectsEnabled()?.let { put("liquid_glass_effects_enabled", encodeSyncBoolean(it)) }
    }

    private fun importThemeSettings(payload: JsonObject) {
        payload.decodeSyncString("selected_theme")?.let { ThemeSettingsStorage.saveSelectedTheme(it) }
        payload.decodeSyncString("custom_theme_colors")?.let { ThemeSettingsStorage.saveCustomThemeColors(it) }
        payload.decodeSyncBoolean("amoled_enabled")?.let { ThemeSettingsStorage.saveAmoledEnabled(it) }
        payload.decodeSyncBoolean("liquid_glass_native_tab_bar_enabled")?.let { ThemeSettingsStorage.saveLiquidGlassNativeTabBarEnabled(it) }
        payload.decodeSyncString("tab_bar_behavior")?.let { ThemeSettingsStorage.saveTabBarBehavior(it) }
        payload.decodeSyncBoolean("dynamic_artwork_background_enabled")?.let { ThemeSettingsStorage.saveDynamicArtworkBackgroundEnabled(it) }
        payload.decodeSyncBoolean("show_catalog_accent_enabled")?.let { ThemeSettingsStorage.saveShowCatalogAccentEnabled(it) }
        payload.decodeSyncString("selected_app_language")?.let { ThemeSettingsStorage.saveSelectedAppLanguage(it) }
        payload.decodeSyncString("nav_bar_style")?.let { ThemeSettingsStorage.saveNavBarStyle(it) }
        payload.decodeSyncString("nav_bar_position")?.let { ThemeSettingsStorage.saveNavBarPosition(it) }
        payload.decodeSyncBoolean("nav_bar_glow_enabled")?.let { ThemeSettingsStorage.saveNavBarGlowEnabled(it) }
        payload.decodeSyncBoolean("nav_bar_default_icon_color_enabled")?.let { ThemeSettingsStorage.saveNavBarDefaultIconColorEnabled(it) }
        payload.decodeSyncString("nav_bar_default_icon_color")?.let { ThemeSettingsStorage.saveNavBarDefaultIconColor(it) }
        payload.decodeSyncString("nav_bar_liquid_glass_bar_effects")?.let { ThemeSettingsStorage.saveNavBarLiquidGlassBarEffects(it) }
        payload.decodeSyncString("nav_bar_liquid_glass_selection_effects")?.let { ThemeSettingsStorage.saveNavBarLiquidGlassSelectionEffects(it) }
        payload.decodeSyncString("nav_bar_glass")?.let { ThemeSettingsStorage.saveNavBarGlass(it) }
        payload.decodeSyncBoolean("liquid_glass_effects_enabled")?.let { ThemeSettingsStorage.saveLiquidGlassEffectsEnabled(it) }
    }

    private fun exportStreamBadgeSettings(): JsonObject = buildJsonObject {
        StreamBadgeSettingsStorage.loadStreamBadgeRules()?.let { put("stream_badge_rules", encodeSyncString(it)) }
        StreamBadgeSettingsStorage.loadShowFileSizeBadges()?.let { put("show_file_size_badges", encodeSyncBoolean(it)) }
        StreamBadgeSettingsStorage.loadShowAddonLogo()?.let { put("show_addon_logo", encodeSyncBoolean(it)) }
        StreamBadgeSettingsStorage.loadShowStreamSearch()?.let { put("show_stream_search", encodeSyncBoolean(it)) }
        StreamBadgeSettingsStorage.loadShowInPlayer()?.let { put("show_in_player", encodeSyncBoolean(it)) }
        StreamBadgeSettingsStorage.loadStreamBackgroundMode()?.let { put("stream_background_mode", encodeSyncString(it)) }
        StreamBadgeSettingsStorage.loadStreamBadgePlacement()?.let { put("stream_badge_placement", encodeSyncString(it)) }
        StreamBadgeSettingsStorage.loadPinnedStreamSources()?.let { put("pinned_stream_sources", encodeSyncString(it)) }
    }

    private fun importStreamBadgeSettings(payload: JsonObject) {
        payload.decodeSyncString("stream_badge_rules")?.let { StreamBadgeSettingsStorage.saveStreamBadgeRules(it) }
        payload.decodeSyncBoolean("show_file_size_badges")?.let { StreamBadgeSettingsStorage.saveShowFileSizeBadges(it) }
        payload.decodeSyncBoolean("show_addon_logo")?.let { StreamBadgeSettingsStorage.saveShowAddonLogo(it) }
        payload.decodeSyncBoolean("show_stream_search")?.let { StreamBadgeSettingsStorage.saveShowStreamSearch(it) }
        payload.decodeSyncBoolean("show_in_player")?.let { StreamBadgeSettingsStorage.saveShowInPlayer(it) }
        payload.decodeSyncString("stream_background_mode")?.let { StreamBadgeSettingsStorage.saveStreamBackgroundMode(it) }
        payload.decodeSyncString("stream_badge_placement")?.let { StreamBadgeSettingsStorage.saveStreamBadgePlacement(it) }
        payload.decodeSyncString("pinned_stream_sources")?.let { StreamBadgeSettingsStorage.savePinnedStreamSources(it) }
    }

    private fun exportDebridSettings(includeCredentials: Boolean): JsonObject = buildJsonObject {
        if (includeCredentials) {
            DebridProviders.all().forEach { provider ->
                DebridSettingsStorage.loadProviderApiKey(provider.id)?.let { put("api_key_${provider.id}", encodeSyncString(it)) }
            }
        }
        DebridSettingsStorage.loadEnabled()?.let { put("enabled", encodeSyncBoolean(it)) }
        DebridSettingsStorage.loadCloudLibraryEnabled()?.let { put("cloud_library_enabled", encodeSyncBoolean(it)) }
        DebridSettingsStorage.loadPreferredResolverProviderId()?.let { put("preferred_resolver_provider_id", encodeSyncString(it)) }
        DebridSettingsStorage.loadInstantPlaybackPreparationLimit()?.let { put("instant_playback_preparation_limit", encodeSyncInt(it)) }
        DebridSettingsStorage.loadStreamMaxResults()?.let { put("stream_max_results", encodeSyncInt(it)) }
        DebridSettingsStorage.loadStreamSortMode()?.let { put("stream_sort_mode", encodeSyncString(it)) }
        DebridSettingsStorage.loadStreamMinimumQuality()?.let { put("stream_minimum_quality", encodeSyncString(it)) }
        DebridSettingsStorage.loadStreamDolbyVisionFilter()?.let { put("stream_dolby_vision_filter", encodeSyncString(it)) }
        DebridSettingsStorage.loadStreamHdrFilter()?.let { put("stream_hdr_filter", encodeSyncString(it)) }
        DebridSettingsStorage.loadStreamCodecFilter()?.let { put("stream_codec_filter", encodeSyncString(it)) }
        DebridSettingsStorage.loadStreamPreferences()?.let { put("stream_preferences", encodeSyncString(it)) }
        DebridSettingsStorage.loadStreamNameTemplate()?.let { put("stream_name_template", encodeSyncString(it)) }
        DebridSettingsStorage.loadStreamDescriptionTemplate()?.let { put("stream_description_template", encodeSyncString(it)) }
    }

    private fun importDebridSettings(payload: JsonObject) {
        DebridProviders.all().forEach { provider ->
            payload.decodeSyncString("api_key_${provider.id}")?.let { DebridSettingsStorage.saveProviderApiKey(provider.id, it) }
        }
        payload.decodeSyncBoolean("enabled")?.let { DebridSettingsStorage.saveEnabled(it) }
        payload.decodeSyncBoolean("cloud_library_enabled")?.let { DebridSettingsStorage.saveCloudLibraryEnabled(it) }
        payload.decodeSyncString("preferred_resolver_provider_id")?.let { DebridSettingsStorage.savePreferredResolverProviderId(it) }
        payload.decodeSyncInt("instant_playback_preparation_limit")?.let { DebridSettingsStorage.saveInstantPlaybackPreparationLimit(it) }
        payload.decodeSyncInt("stream_max_results")?.let { DebridSettingsStorage.saveStreamMaxResults(it) }
        payload.decodeSyncString("stream_sort_mode")?.let { DebridSettingsStorage.saveStreamSortMode(it) }
        payload.decodeSyncString("stream_minimum_quality")?.let { DebridSettingsStorage.saveStreamMinimumQuality(it) }
        payload.decodeSyncString("stream_dolby_vision_filter")?.let { DebridSettingsStorage.saveStreamDolbyVisionFilter(it) }
        payload.decodeSyncString("stream_hdr_filter")?.let { DebridSettingsStorage.saveStreamHdrFilter(it) }
        payload.decodeSyncString("stream_codec_filter")?.let { DebridSettingsStorage.saveStreamCodecFilter(it) }
        payload.decodeSyncString("stream_preferences")?.let { DebridSettingsStorage.saveStreamPreferences(it) }
        payload.decodeSyncString("stream_name_template")?.let { DebridSettingsStorage.saveStreamNameTemplate(it) }
        payload.decodeSyncString("stream_description_template")?.let { DebridSettingsStorage.saveStreamDescriptionTemplate(it) }
    }

    private fun exportTmdbSettings(includeCredentials: Boolean): JsonObject = buildJsonObject {
        TmdbSettingsStorage.loadEnabled()?.let { put("enabled", encodeSyncBoolean(it)) }
        if (includeCredentials) TmdbSettingsStorage.loadApiKey()?.let { put("api_key", encodeSyncString(it)) }
        TmdbSettingsStorage.loadLanguage()?.let { put("language", encodeSyncString(it)) }
        TmdbSettingsStorage.loadUseTrailers()?.let { put("use_trailers", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseArtwork()?.let { put("use_artwork", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseBasicInfo()?.let { put("use_basic_info", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseDetails()?.let { put("use_details", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseCredits()?.let { put("use_credits", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseProductions()?.let { put("use_productions", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseNetworks()?.let { put("use_networks", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseEpisodes()?.let { put("use_episodes", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseEpisodeRatings()?.let { put("use_episode_ratings", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseSeasonPosters()?.let { put("use_season_posters", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseMoreLikeThis()?.let { put("use_more_like_this", encodeSyncBoolean(it)) }
        TmdbSettingsStorage.loadUseCollections()?.let { put("use_collections", encodeSyncBoolean(it)) }
    }

    private fun importTmdbSettings(payload: JsonObject) {
        payload.decodeSyncBoolean("enabled")?.let { TmdbSettingsStorage.saveEnabled(it) }
        payload.decodeSyncString("api_key")?.let { TmdbSettingsStorage.saveApiKey(it) }
        payload.decodeSyncString("language")?.let { TmdbSettingsStorage.saveLanguage(it) }
        payload.decodeSyncBoolean("use_trailers")?.let { TmdbSettingsStorage.saveUseTrailers(it) }
        payload.decodeSyncBoolean("use_artwork")?.let { TmdbSettingsStorage.saveUseArtwork(it) }
        payload.decodeSyncBoolean("use_basic_info")?.let { TmdbSettingsStorage.saveUseBasicInfo(it) }
        payload.decodeSyncBoolean("use_details")?.let { TmdbSettingsStorage.saveUseDetails(it) }
        payload.decodeSyncBoolean("use_credits")?.let { TmdbSettingsStorage.saveUseCredits(it) }
        payload.decodeSyncBoolean("use_productions")?.let { TmdbSettingsStorage.saveUseProductions(it) }
        payload.decodeSyncBoolean("use_networks")?.let { TmdbSettingsStorage.saveUseNetworks(it) }
        payload.decodeSyncBoolean("use_episodes")?.let { TmdbSettingsStorage.saveUseEpisodes(it) }
        payload.decodeSyncBoolean("use_episode_ratings")?.let { TmdbSettingsStorage.saveUseEpisodeRatings(it) }
        payload.decodeSyncBoolean("use_season_posters")?.let { TmdbSettingsStorage.saveUseSeasonPosters(it) }
        payload.decodeSyncBoolean("use_more_like_this")?.let { TmdbSettingsStorage.saveUseMoreLikeThis(it) }
        payload.decodeSyncBoolean("use_collections")?.let { TmdbSettingsStorage.saveUseCollections(it) }
    }

    private fun exportMdbListSettings(includeCredentials: Boolean): JsonObject = buildJsonObject {
        MdbListSettingsStorage.loadEnabled()?.let { put("enabled", encodeSyncBoolean(it)) }
        if (includeCredentials) MdbListSettingsStorage.loadApiKey()?.let { put("api_key", encodeSyncString(it)) }
        MdbListSettingsStorage.loadUseImdb()?.let { put("use_imdb", encodeSyncBoolean(it)) }
        MdbListSettingsStorage.loadUseTmdb()?.let { put("use_tmdb", encodeSyncBoolean(it)) }
        MdbListSettingsStorage.loadUseTomatoes()?.let { put("use_tomatoes", encodeSyncBoolean(it)) }
        MdbListSettingsStorage.loadUseMetacritic()?.let { put("use_metacritic", encodeSyncBoolean(it)) }
        MdbListSettingsStorage.loadUseTrakt()?.let { put("use_trakt", encodeSyncBoolean(it)) }
        MdbListSettingsStorage.loadUseLetterboxd()?.let { put("use_letterboxd", encodeSyncBoolean(it)) }
        MdbListSettingsStorage.loadUseAudience()?.let { put("use_audience", encodeSyncBoolean(it)) }
        MdbListSettingsStorage.loadUseMal()?.let { put("use_mal", encodeSyncBoolean(it)) }
    }

    private fun importMdbListSettings(payload: JsonObject) {
        payload.decodeSyncBoolean("enabled")?.let { MdbListSettingsStorage.saveEnabled(it) }
        payload.decodeSyncString("api_key")?.let { MdbListSettingsStorage.saveApiKey(it) }
        payload.decodeSyncBoolean("use_imdb")?.let { MdbListSettingsStorage.saveUseImdb(it) }
        payload.decodeSyncBoolean("use_tmdb")?.let { MdbListSettingsStorage.saveUseTmdb(it) }
        payload.decodeSyncBoolean("use_tomatoes")?.let { MdbListSettingsStorage.saveUseTomatoes(it) }
        payload.decodeSyncBoolean("use_metacritic")?.let { MdbListSettingsStorage.saveUseMetacritic(it) }
        payload.decodeSyncBoolean("use_trakt")?.let { MdbListSettingsStorage.saveUseTrakt(it) }
        payload.decodeSyncBoolean("use_letterboxd")?.let { MdbListSettingsStorage.saveUseLetterboxd(it) }
        payload.decodeSyncBoolean("use_audience")?.let { MdbListSettingsStorage.saveUseAudience(it) }
        payload.decodeSyncBoolean("use_mal")?.let { MdbListSettingsStorage.saveUseMal(it) }
    }
}

internal class SettingsConfigImport(
    val settings: JsonObject,
    val includesSensitiveData: Boolean,
)
