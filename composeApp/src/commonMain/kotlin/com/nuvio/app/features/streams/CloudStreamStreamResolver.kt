package com.nuvio.app.features.streams

import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.cloudstream.CloudStreamRepository
import com.nuvio.app.features.cloudstream.CloudStreamSearchItem
import com.nuvio.app.features.cloudstream.CloudStreamTvType
import com.nuvio.app.features.cloudstream.sha256Hex
import com.nuvio.app.features.cloudstream.toStreamItem
import com.nuvio.app.features.details.MetaDetailsRepository

internal data class CloudStreamSearchRequest(
    val title: String,
    val type: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val aliases: List<String> = emptyList(),
    val episodeTitle: String? = null,
)

internal data class CloudStreamProviderTarget(
    val id: String,
    val name: String,
)

internal data class CloudStreamProviderGroup(
    val addonId: String,
    val addonName: String,
    val providers: List<CloudStreamProviderTarget>,
)

internal data class CloudStreamProviderStreams(
    val streams: List<StreamItem>,
    val error: String? = null,
)

internal fun cloudStreamAddonId(providerId: String): String =
    "cloudstream:${sha256Hex(providerId.encodeToByteArray()).take(16)}"

internal fun buildCloudStreamSearchRequest(
    type: String,
    videoId: String,
    parentMetaId: String?,
    parentMetaType: String?,
    season: Int?,
    episode: Int?,
    searchTitle: String?,
): CloudStreamSearchRequest {
    @Suppress("UNUSED_VARIABLE")
    val ignoredParentType = parentMetaType
    val details = MetaDetailsRepository.peek(type, parentMetaId ?: videoId)
        ?: MetaDetailsRepository.peek(type, videoId)
    val title = searchTitle?.trim()?.takeIf(String::isNotBlank)
        ?: details?.name?.trim()?.takeIf(String::isNotBlank)
        ?: videoId
    val year = details?.releaseInfo
        ?.let { Regex("\\b(19\\d{2}|20\\d{2})\\b").find(it)?.value?.toIntOrNull() }
    val episodeTitle = details?.videos
        ?.firstOrNull { it.season == season && it.episode == episode }
        ?.title
        ?.takeIf(String::isNotBlank)
    return CloudStreamSearchRequest(
        title = title,
        type = type,
        year = year,
        season = season,
        episode = episode,
        episodeTitle = episodeTitle,
    )
}

internal fun cloudStreamProviderGroupsForRequest(
    type: String,
    request: CloudStreamSearchRequest,
): List<CloudStreamProviderGroup> {
    val state = CloudStreamRepository.uiState.value
    val providers = state.plugins
        .asSequence()
        .filter { it.enabled && it.isRunnable }
        .filter { providerSupportsType(it, type.ifBlank { request.type }) }
        .map { item ->
            CloudStreamProviderTarget(
                id = item.metadata.id.value,
                name = item.metadata.name,
            ) to item
        }
        .toList()
    if (providers.isEmpty()) return emptyList()

    if (!state.groupStreamsByRepository) {
        return providers.map { (target, _) ->
            CloudStreamProviderGroup(
                addonId = cloudStreamAddonId(target.id),
                addonName = target.name,
                providers = listOf(target),
            )
        }
    }

    val repositoryNames = state.repositories.associate { it.manifest.sourceUrl to it.manifest.name }
    return providers
        .groupBy { (_, item) -> item.metadata.repositoryManifestUrl }
        .map { (repositoryUrl, entries) ->
            val name = repositoryNames[repositoryUrl]
                ?.takeIf(String::isNotBlank)
                ?: entries.first().second.metadata.name
            CloudStreamProviderGroup(
                addonId = "cloudstream-repository:${sha256Hex(repositoryUrl.encodeToByteArray()).take(16)}",
                addonName = name,
                providers = entries.map { it.first },
            )
        }
        .sortedBy { it.addonName.lowercase() }
}

private fun providerSupportsType(item: CloudStreamPluginItem, type: String): Boolean {
    val requested = normalizeCloudStreamType(type)
    val supported = item.metadata.tvTypes
    return supported.isEmpty() || supported.any { it == CloudStreamTvType.Other || it.nuvioType == requested }
}

private fun normalizeCloudStreamType(type: String): String = when (type.trim().lowercase()) {
    "tv", "show", "series" -> "series"
    "movie", "film" -> "movie"
    "live", "livetv", "channel" -> "live"
    else -> type.trim().lowercase()
}

internal suspend fun resolveCloudStreamProviderStreams(
    providerGroup: CloudStreamProviderGroup,
    request: CloudStreamSearchRequest,
): CloudStreamProviderStreams {
    val streams = mutableListOf<StreamItem>()
    var firstError: String? = null
    for (provider in providerGroup.providers) {
        val searchAttempt = runCatchingUnlessCancelled {
            CloudStreamRepository.search(request.title, provider.id)
        }
        if (searchAttempt.isFailure) {
            if (firstError == null) firstError = searchAttempt.exceptionOrNull()?.message ?: "CloudStream search failed"
            continue
        }
        val searchResults = searchAttempt.getOrNull().orEmpty()
        val searchResult = searchResults.firstOrNull()
        if (searchResult == null) continue
        if (searchResult.isFailure) {
            if (firstError == null) firstError = searchResult.exceptionOrNull()?.message ?: "CloudStream search failed"
            continue
        }
        val searchItems = searchResult.getOrNull().orEmpty()
        for (item in searchItems.bestCloudStreamMatches(request)) {
            val loadAttempt = runCatchingUnlessCancelled {
                CloudStreamRepository.load(provider.id, item.data)
            }
            if (loadAttempt.isFailure) {
                if (firstError == null) firstError = loadAttempt.exceptionOrNull()?.message ?: "CloudStream title lookup failed"
                continue
            }
            val loadedResult = loadAttempt.getOrNull()
            if (loadedResult == null || loadedResult.isFailure) {
                if (firstError == null) firstError = loadedResult?.exceptionOrNull()?.message ?: "CloudStream title lookup failed"
                continue
            }
            val loaded = loadedResult.getOrNull() ?: continue
            if (!loaded.matchesCloudStreamRequest(request, item, allowProviderRankedFallback = true)) continue
            val linkData = loaded.linkDataForCloudStreamRequest(request) ?: continue
            val linksAttempt = runCatchingUnlessCancelled {
                CloudStreamRepository.loadLinks(provider.id, linkData)
            }
            if (linksAttempt.isFailure) {
                if (firstError == null) firstError = linksAttempt.exceptionOrNull()?.message ?: "CloudStream link lookup failed"
                continue
            }
            val linksResult = linksAttempt.getOrNull()
            if (linksResult == null || linksResult.isFailure) {
                if (firstError == null) firstError = linksResult?.exceptionOrNull()?.message ?: "CloudStream link lookup failed"
                continue
            }
            streams += linksResult.getOrNull().orEmpty().map { it.toStreamItem(provider.id, provider.name) }
        }
    }
    return CloudStreamProviderStreams(streams = streams, error = firstError)
}

internal suspend fun resolveCloudStreamDirectRoute(
    providerId: String,
    providerName: String,
    data: String,
): Result<List<StreamItem>> {
    val loaded = CloudStreamRepository.load(providerId, data)
    if (loaded.isFailure) return Result.failure(loaded.exceptionOrNull() ?: IllegalStateException("CloudStream title lookup failed"))
    val item = loaded.getOrNull() ?: return Result.success(emptyList())
    return CloudStreamRepository.loadLinks(providerId, item.data).map { links ->
        links.map { it.toStreamItem(providerId, providerName) }
    }
}

internal fun List<CloudStreamSearchItem>.bestCloudStreamMatch(
    searchTitle: String,
    type: String,
): CloudStreamSearchItem? =
    bestCloudStreamMatches(
        CloudStreamSearchRequest(title = searchTitle, type = type),
    ).firstOrNull()

internal fun List<CloudStreamSearchItem>.bestCloudStreamMatches(
    request: CloudStreamSearchRequest,
): List<CloudStreamSearchItem> {
    val requestedTitles = (listOf(request.title) + request.aliases)
        .map(::normalizeCloudStreamTitle)
        .filter(String::isNotBlank)
        .toSet()
    if (requestedTitles.isEmpty()) return emptyList()
    return asSequence()
        .filter { normalizeCloudStreamType(it.type.nuvioType) == normalizeCloudStreamType(request.type) }
        .filter { item ->
            val candidateYear = item.year ?: cloudStreamYear(item.name)
            request.year == null || candidateYear == null || candidateYear == request.year
        }
        .filter { item -> normalizeCloudStreamTitle(item.name) in requestedTitles }
        .sortedByDescending { item ->
            request.year != null && (item.year ?: cloudStreamYear(item.name)) == request.year
        }
        .distinctBy(CloudStreamSearchItem::data)
        .toList()
}

internal fun com.nuvio.app.features.cloudstream.CloudStreamLoadItem.matchesCloudStreamRequest(
    request: CloudStreamSearchRequest,
    searchItem: CloudStreamSearchItem,
    allowProviderRankedFallback: Boolean = false,
): Boolean {
    if (normalizeCloudStreamType(type.nuvioType) != normalizeCloudStreamType(request.type)) return false
    val requestedTitles = (listOf(request.title) + request.aliases)
        .map(::normalizeCloudStreamTitle)
        .filter(String::isNotBlank)
        .toSet()
    val titleMatches = normalizeCloudStreamTitle(name) in requestedTitles ||
        normalizeCloudStreamTitle(searchItem.name) in requestedTitles
    val loadedYear = year ?: cloudStreamYear(name)
    val searchedYear = searchItem.year ?: cloudStreamYear(searchItem.name)
    if (request.year != null) {
        if (loadedYear != null && loadedYear != request.year) return false
        if (searchedYear != null && searchedYear != request.year) return false
        if (loadedYear != request.year && searchedYear != request.year) return false
    }
    val safeProviderRankedFallback = allowProviderRankedFallback &&
        normalizeCloudStreamType(request.type) == "movie" &&
        request.year != null && loadedYear == request.year
    return titleMatches || safeProviderRankedFallback
}

internal fun com.nuvio.app.features.cloudstream.CloudStreamLoadItem.matchesCloudStreamExternalIdRequest(
    request: CloudStreamSearchRequest,
): Boolean {
    if (normalizeCloudStreamType(type.nuvioType) != normalizeCloudStreamType(request.type)) return false
    val requestedTitles = (listOf(request.title) + request.aliases)
        .map(::normalizeCloudStreamTitle)
        .filter(String::isNotBlank)
        .toSet()
    val titleMatches = normalizeCloudStreamTitle(name) in requestedTitles
    val loadedYear = year ?: cloudStreamYear(name)
    if (request.year != null && loadedYear != request.year) return false
    val safeLocalizedMovieFallback = normalizeCloudStreamType(request.type) == "movie" &&
        request.year != null && loadedYear == request.year
    return titleMatches || safeLocalizedMovieFallback
}

internal fun com.nuvio.app.features.cloudstream.CloudStreamLoadItem.linkDataForCloudStreamRequest(
    request: CloudStreamSearchRequest,
): String? {
    if (request.season == null && request.episode == null && request.episodeTitle.isNullOrBlank()) return data
    if (episodes.isEmpty()) return null
    val seasonCandidates = episodes.filter { episode ->
        request.season == null || episode.season == request.season ||
            (request.season == 1 && episode.season == null)
    }
    val episodeTitle = request.episodeTitle?.let(::normalizeCloudStreamTitle)
    val numberedCandidates = if (request.episode == null) {
        emptyList()
    } else {
        seasonCandidates.filter { it.episode == request.episode }
    }
    if (request.season == null && numberedCandidates.map { it.season }.distinct().size > 1) {
        return null
    }
    val numbered = numberedCandidates.firstOrNull()
    if (numbered != null) {
        if (episodeTitle.isNullOrBlank()) return numbered.data
        val actualTitle = normalizeCloudStreamTitle(numbered.name)
        val isGenericEpisodeTitle = actualTitle.matches(Regex("episode \\d+"))
        return numbered.data.takeIf { actualTitle == episodeTitle || isGenericEpisodeTitle }
    }
    if (!episodeTitle.isNullOrBlank()) {
        return seasonCandidates.firstOrNull {
            normalizeCloudStreamTitle(it.name) == episodeTitle
        }?.data
    }
    return null
}

private fun normalizeCloudStreamTitle(value: String): String {
    val words = value.lowercase()
        .replace(Regex("\\b(19\\d{2}|20\\d{2})\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .toMutableList()
    if (words.firstOrNull() in setOf("the", "a", "an")) words.removeAt(0)
    return words.joinToString(" ")
}

private fun cloudStreamYear(value: String): Int? =
    Regex("\\b(19\\d{2}|20\\d{2})\\b").find(value)?.value?.toIntOrNull()
