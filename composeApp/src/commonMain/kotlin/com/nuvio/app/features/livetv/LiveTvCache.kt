package com.nuvio.app.features.livetv

import com.nuvio.app.core.storage.AppFileCache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ChannelsCachePath = "live_tv/channels.json"
// The first guide cache repeated every programme list per channel and grew past 100 MB; it is never read again.
private const val LegacyEpgCachePath = "live_tv/epg.json"
private const val EpgCachePath = "live_tv/epg_v2.json"

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
    @SerialName("t") val title: String,
    @SerialName("s") val startMillis: Long,
    @SerialName("e") val endMillis: Long,
    @SerialName("d") val description: String? = null,
    @SerialName("c") val category: String? = null,
)

/** Channels that share a guide point at the same entry in [lists], so each programme list is stored once. */
@Serializable
private data class CachedLiveTvEpgPayload(
    val signature: String,
    val savedAtMillis: Long,
    val lists: List<List<CachedLiveTvProgram>> = emptyList(),
    val channels: Map<String, Int> = emptyMap(),
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
        clearLegacyEpg()
        val payload = AppFileCache.read(EpgCachePath)?.takeIf(String::isNotBlank) ?: return null
        val decoded = liveTvCacheJson.decodeFromString<CachedLiveTvEpgPayload>(payload)
        val lists = decoded.lists.map { programs ->
            programs.map { program ->
                LiveTvEpgProgram(
                    title = program.title,
                    startMillis = program.startMillis,
                    endMillis = program.endMillis,
                    description = program.description,
                    category = program.category,
                )
            }
        }
        val programs = HashMap<String, List<LiveTvEpgProgram>>(decoded.channels.size)
        decoded.channels.forEach { (channelId, index) ->
            lists.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { programs[channelId] = it }
        }
        LiveTvCachedEpg(
            signature = decoded.signature,
            savedAtMillis = decoded.savedAtMillis,
            programs = programs,
        )
    }.getOrNull()

    fun saveEpg(signature: String, savedAtMillis: Long, programs: Map<String, List<LiveTvEpgProgram>>) {
        runCatching {
            // Matching hands the same list instance to every channel on one guide entry, so identity finds the repeats.
            val lists = mutableListOf<List<LiveTvEpgProgram>>()
            val channels = HashMap<String, Int>(programs.size)
            programs.forEach { (channelId, list) ->
                var index = lists.indexOfFirst { it === list }
                if (index < 0) {
                    index = lists.size
                    lists += list
                }
                channels[channelId] = index
            }
            val payload = CachedLiveTvEpgPayload(
                signature = signature,
                savedAtMillis = savedAtMillis,
                lists = lists.map { list ->
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
                channels = channels,
            )
            AppFileCache.write(EpgCachePath, liveTvCacheJson.encodeToString(payload))
            clearLegacyEpg()
        }
    }

    private var legacyEpgCleared = false

    /** Frees the space the old guide cache took. The old file is only ever emptied, never read. */
    private fun clearLegacyEpg() {
        if (legacyEpgCleared) return
        legacyEpgCleared = true
        runCatching { AppFileCache.write(LegacyEpgCachePath, "") }
    }
}

internal fun liveTvChannelSourceSignature(state: LiveTvUiState): String = buildString {
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
