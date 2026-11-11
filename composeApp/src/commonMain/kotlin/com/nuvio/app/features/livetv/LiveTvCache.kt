package com.nuvio.app.features.livetv

import com.nuvio.app.core.storage.AppFileCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ChannelsCachePath = "live_tv/channels.json"
private const val EpgCachePath = "live_tv/epg.json"

private val liveTvCacheJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
private data class CachedLiveTvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val streamType: String? = null,
    val stalkerCommand: String? = null,
    val epgId: String? = null,
)

@Serializable
private data class CachedLiveTvChannelsPayload(
    val signature: String,
    val savedAtMillis: Long,
    val epgUrls: List<String> = emptyList(),
    val channels: List<CachedLiveTvChannel> = emptyList(),
)

@Serializable
private data class CachedLiveTvProgram(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String? = null,
    val category: String? = null,
)

@Serializable
private data class CachedLiveTvEpgPayload(
    val signature: String,
    val savedAtMillis: Long,
    val programs: Map<String, List<CachedLiveTvProgram>> = emptyMap(),
)

internal class LiveTvCachedChannels(
    val signature: String,
    val savedAtMillis: Long,
    val epgUrls: List<String>,
    val channels: List<LiveTvChannel>,
)

internal class LiveTvCachedEpg(
    val signature: String,
    val savedAtMillis: Long,
    val programs: Map<String, List<LiveTvEpgProgram>>,
)

internal object LiveTvCacheStore {
    fun loadChannels(): LiveTvCachedChannels? = runCatching {
        val payload = AppFileCache.read(ChannelsCachePath)?.takeIf(String::isNotBlank) ?: return null
        val decoded = liveTvCacheJson.decodeFromString<CachedLiveTvChannelsPayload>(payload)
        LiveTvCachedChannels(
            signature = decoded.signature,
            savedAtMillis = decoded.savedAtMillis,
            epgUrls = decoded.epgUrls,
            channels = decoded.channels.map { channel ->
                LiveTvChannel(
                    id = channel.id,
                    name = channel.name,
                    streamUrl = channel.streamUrl,
                    logoUrl = channel.logoUrl,
                    group = channel.group,
                    playlistId = channel.playlistId,
                    playlistName = channel.playlistName,
                    headers = channel.headers,
                    streamType = channel.streamType,
                    stalkerCommand = channel.stalkerCommand,
                    epgId = channel.epgId,
                )
            },
        )
    }.getOrNull()

    fun saveChannels(signature: String, savedAtMillis: Long, epgUrls: List<String>, channels: List<LiveTvChannel>) {
        runCatching {
            val payload = CachedLiveTvChannelsPayload(
                signature = signature,
                savedAtMillis = savedAtMillis,
                epgUrls = epgUrls,
                channels = channels.map { channel ->
                    CachedLiveTvChannel(
                        id = channel.id,
                        name = channel.name,
                        streamUrl = channel.streamUrl,
                        logoUrl = channel.logoUrl,
                        group = channel.group,
                        playlistId = channel.playlistId,
                        playlistName = channel.playlistName,
                        headers = channel.headers,
                        streamType = channel.streamType,
                        stalkerCommand = channel.stalkerCommand,
                        epgId = channel.epgId,
                    )
                },
            )
            AppFileCache.write(ChannelsCachePath, liveTvCacheJson.encodeToString(payload))
        }
    }

    fun loadEpg(): LiveTvCachedEpg? = runCatching {
        val payload = AppFileCache.read(EpgCachePath)?.takeIf(String::isNotBlank) ?: return null
        val decoded = liveTvCacheJson.decodeFromString<CachedLiveTvEpgPayload>(payload)
        LiveTvCachedEpg(
            signature = decoded.signature,
            savedAtMillis = decoded.savedAtMillis,
            programs = decoded.programs.mapValues { (_, programs) ->
                programs.map { program ->
                    LiveTvEpgProgram(
                        title = program.title,
                        startMillis = program.startMillis,
                        endMillis = program.endMillis,
                        description = program.description,
                        category = program.category,
                    )
                }
            },
        )
    }.getOrNull()

    fun saveEpg(signature: String, savedAtMillis: Long, programs: Map<String, List<LiveTvEpgProgram>>) {
        runCatching {
            val payload = CachedLiveTvEpgPayload(
                signature = signature,
                savedAtMillis = savedAtMillis,
                programs = programs.mapValues { (_, list) ->
                    list.map { program ->
                        CachedLiveTvProgram(
                            title = program.title,
                            startMillis = program.startMillis,
                            endMillis = program.endMillis,
                            description = program.description,
                            category = program.category,
                        )
                    }
                },
            )
            AppFileCache.write(EpgCachePath, liveTvCacheJson.encodeToString(payload))
        }
    }
}

/**
 * Bumped when the channel list is read differently, so lists saved by an older version are
 * loaded again (3: VLC's user agent by default, Xtream panel links in the account's format).
 */
private const val ChannelsCacheFormat = 3

internal fun liveTvChannelSourceSignature(state: LiveTvUiState): String = buildString {
    append("v").append(ChannelsCacheFormat).append(';')
    state.playlists.filter { it.isEnabled }.forEach { playlist ->
        append(playlist.id).append('|').append(playlist.type.name).append('|')
        append(playlist.source.length).append(':').append(playlist.source.hashCode()).append(';')
    }
    val xtream = state.xtreamSettings
    if (xtream.isConfigured && xtream.isEnabled) {
        append("x|").append(xtream.serverUrl).append('|').append(xtream.username).append('|')
        append(xtream.password.hashCode()).append(';')
    }
    val stalker = state.stalkerSettings
    if (stalker.isConfigured && stalker.isEnabled) {
        append("s|").append(stalker.portalUrl).append('|').append(stalker.macAddress).append('|')
        append(stalker.username).append(';')
    }
}.let { raw -> "${raw.length}:${raw.hashCode()}" }
