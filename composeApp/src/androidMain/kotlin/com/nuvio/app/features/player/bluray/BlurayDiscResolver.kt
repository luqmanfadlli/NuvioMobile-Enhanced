@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class BlurayTitle(
    val name: String,
    val clipUris: List<String>,
    val durationsMs: List<Long>,
) {
    val durationMs: Long get() = durationsMs.sum()
}

internal class BlurayDisc(
    val titles: List<BlurayTitle>,
    val featureIndex: Int,
    val bdavUris: Set<String> = emptySet(),
) {
    val feature: BlurayTitle get() = titles[featureIndex]

    fun isBdavUri(uri: Uri): Boolean {
        val value = uri.toString()
        return value in bdavUris || BlurayDiscResolver.isLikelyBdavM2tsUrl(value)
    }
}

internal object BlurayDiscResolver {
    private const val TAG = "BlurayDiscResolver"
    private const val BDAV_PACKET_SIZE = 192
    private const val TS_SYNC_BYTE = 0x47
    private const val PROBE_BYTES = 256 * 1024
    private const val LISTING_BYTES = 512 * 1024
    private const val MPLS_BYTES = 1024 * 1024
    private const val MAX_CLIP_REPEATS = 2
    private const val MAX_PLAYLISTS = 1000
    private const val PLAYLIST_THREADS = 8
    private const val MIN_TITLE_MS = 60_000L
    private const val MAX_TITLES = 30
    private val CLIP_PATH = Regex("(?i)/BDMV/(STREAM)/(\\d{5})\\.(m2ts)$")
    private val CLIP_NAME = Regex("(?i)^(\\d{5})\\.m2ts$")
    private val BDMV_FILE_PATH = Regex("(?i)/(BDMV)/(index\\.bdmv|movieobject\\.bdmv)$")
    private val MPLS_PATH = Regex("(?i)/(BDMV)/(PLAYLIST)/\\d{5}\\.mpls$")
    private val CANDIDATE_PLAYLISTS = ((0..19) + (800..819) + (1..9).map { it * 100 }).distinct()

    data class ParsedMpls(
        val name: String,
        val clipIds: List<String>,
        val duration90kHz: Long,
        val itemTicks: List<Long> = emptyList(),
    ) {
        fun itemDurationMs(index: Int): Long = (itemTicks.getOrNull(index) ?: 0L) / 45

        val durationMs: Long get() = duration90kHz / 45
    }

    fun isLikelyBdavM2tsUrl(url: String): Boolean {
        val path = extractPath(url).lowercase(Locale.US)
        return path.endsWith(".m2ts") || path.endsWith(".mts")
    }

    fun isDiscEntryUrl(url: String): Boolean {
        val path = extractPath(url)
        return BDMV_FILE_PATH.containsMatchIn(path) || MPLS_PATH.containsMatchIn(path)
    }

    fun singleClipDisc(url: String): BlurayDisc = singleClip(url)

    fun isCandidate(url: String): Boolean =
        BlurayIsoImage.isLikelyIsoUrl(url) || isLikelyBdavM2tsUrl(url) || looksLikeBlurayPath(url)

    fun resolveBeforePlayback(url: String, metadataFactory: DataSource.Factory): BlurayDisc? {
        if (BlurayIsoImage.isLikelyIsoUrl(url)) {
            fromIso(url, metadataFactory)?.let { return it }
        }
        toLocalFile(url)?.let { local ->
            resolveLocal(local, url)?.let { return it }
        }
        if (isHttp(url)) {
            val path = extractPath(url)
            when {
                CLIP_PATH.containsMatchIn(path) -> probeHttpClipFolder(url, metadataFactory)
                BDMV_FILE_PATH.containsMatchIn(path) -> probeHttpBdmvFile(url, metadataFactory)
                MPLS_PATH.containsMatchIn(path) -> probeHttpPlaylistFile(url, metadataFactory)
                else -> null
            }?.let { return it }
        }
        if (isLikelyBdavM2tsUrl(url)) return singleClip(url)
        return null
    }

    fun probeAfterFailure(url: String, metadataFactory: DataSource.Factory): BlurayDisc? {
        val head = readBytes(metadataFactory, Uri.parse(url), PROBE_BYTES) ?: return null
        if (BlurayIsoImage.looksLikeUdfImage(head)) return fromIso(url, metadataFactory)
        if (isBdavPacketPattern(head)) return singleClip(url, explicitBdav = true)
        if (isHttp(url) && looksLikeBlurayDirectoryHtml(String(head, Charsets.UTF_8))) {
            return probeHttpDirectory(url, metadataFactory)
        }
        return null
    }

    fun titleLanguages(title: BlurayTitle, metadataFactory: DataSource.Factory): Pair<List<String>, List<String>> {
        val empty = emptyList<String>() to emptyList<String>()
        val clip = title.clipUris.firstOrNull()?.let(Uri::parse) ?: return empty
        val infoPath = BlurayClipInfo.clipInfoPath(clip.path.orEmpty()) ?: return empty
        val bytes = readBytes(BlurayIsoDataSourceFactory(metadataFactory), clip.buildUpon().path(infoPath).build(), MPLS_BYTES)
            ?: return empty
        val languages = BlurayClipInfo.parseLanguages(bytes)
        fun among(vararg pids: IntRange) =
            languages.filterKeys { pid -> pids.any { pid in it } }.toSortedMap().values.distinct()
        return among(0x1100..0x111F) to among(0x1200..0x12FF, 0x1800..0x181F)
    }

    private fun singleClip(url: String, explicitBdav: Boolean = false): BlurayDisc =
        BlurayDisc(
            titles = listOf(BlurayTitle(name = clipName(url), clipUris = listOf(url), durationsMs = listOf(0L))),
            featureIndex = 0,
            bdavUris = if (explicitBdav) setOf(url) else emptySet(),
        )

    private fun clipName(url: String): String =
        extractPath(url).substringAfterLast('/').ifBlank { url }

    private fun fromIso(url: String, metadataFactory: DataSource.Factory): BlurayDisc? {
        val image = BlurayIsoImage.open(Uri.parse(url), metadataFactory) ?: return null
        try {
            val clips = image.list("BDMV/STREAM")
                .mapNotNull { name -> CLIP_NAME.find(name)?.groupValues?.get(1)?.let { it to name } }
                .toMap()
            val names = image.list("BDMV/PLAYLIST")
                .filter { it.endsWith(".mpls", ignoreCase = true) }
                .sorted()
                .take(MAX_PLAYLISTS)
            val playlists = parallel(names) { name ->
                image.readFile("BDMV/PLAYLIST/$name", MPLS_BYTES)?.let { parseMpls(name, it) }
            }
            return buildDisc(playlists, currentClipId = null) { clipId ->
                clips[clipId]?.let { image.clipUri("BDMV/STREAM/$it") }
            }
        } finally {
            image.releaseReadCache()
        }
    }

    private fun resolveLocal(input: File, url: String): BlurayDisc? {
        val clipMatch = CLIP_PATH.find(input.absolutePath)
        val bdmvDir = when {
            input.isDirectory && input.name.equals("BDMV", ignoreCase = true) -> input
            input.isDirectory -> findChildDirectory(input, "BDMV")
            input.name.equals("index.bdmv", ignoreCase = true) || input.name.equals("movieobject.bdmv", ignoreCase = true) ->
                input.parentFile?.takeIf { it.name.equals("BDMV", ignoreCase = true) }
            input.extension.equals("mpls", ignoreCase = true) -> input.parentFile?.parentFile
                ?.takeIf { it.name.equals("BDMV", ignoreCase = true) }
            clipMatch != null -> input.parentFile?.parentFile
            else -> null
        } ?: return null
        val playlistDir = findChildDirectory(bdmvDir, "PLAYLIST") ?: return null
        val streamDir = findChildDirectory(bdmvDir, "STREAM") ?: return null
        val streams = streamDir.listFiles()
            ?.filter { it.isFile }
            ?.associateBy { it.name.lowercase(Locale.US) }
            .orEmpty()
        val playlistFiles = playlistDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("mpls", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        val playlists = playlistFiles.mapNotNull { file ->
            runCatching { file.readBytes() }.getOrNull()?.let { parseMpls(file.name, it) }
        }
        if (playlists.isEmpty()) {
            Log.w(TAG, "no readable playlists in ${bdmvDir.absolutePath}")
            return if (clipMatch != null) singleClip(url) else null
        }
        val explicitPlaylist = input.takeIf { it.extension.equals("mpls", ignoreCase = true) }?.name
        val disc = buildDisc(
            playlists = playlists,
            currentClipId = clipMatch?.groupValues?.get(2),
            preferredFeature = explicitPlaylist,
        ) { clipId ->
            streams["$clipId.m2ts"]?.let { Uri.fromFile(it).toString() }
        }
        return disc ?: if (clipMatch != null) singleClip(url) else null
    }

    private class HttpBdmv(private val bdmv: HttpUrl, val upper: Boolean, private val query: String?) {
        fun child(path: String): HttpUrl? =
            bdmv.resolve(path)?.let { url -> if (query == null) url else url.newBuilder().encodedQuery(query).build() }

        val playlistDir: HttpUrl? get() = child(if (upper) "PLAYLIST/" else "playlist/")

        fun playlist(name: String): HttpUrl? = child((if (upper) "PLAYLIST/" else "playlist/") + name)

        fun clip(id: String, extension: String = "m2ts"): HttpUrl? = child((if (upper) "STREAM/" else "stream/") + "$id.$extension")

        val movieObjects: List<HttpUrl>
            get() = listOf("MovieObject.bdmv", "MOVIEOBJECT.BDMV", "movieobject.bdmv").mapNotNull(::child)
    }

    private fun probeHttpClipFolder(url: String, metadataFactory: DataSource.Factory): BlurayDisc? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val match = CLIP_PATH.find(httpUrl.encodedPath) ?: return null
        val (stream, clipId, extension) = match.destructured
        val bdmv = HttpBdmv(httpUrl.resolve("../") ?: return null, stream != stream.lowercase(Locale.US), httpUrl.encodedQuery)
        val playlists = fetchHttpPlaylists(bdmv, metadataFactory)
        if (playlists.isEmpty()) return null
        return buildDisc(playlists, currentClipId = clipId, currentClipUri = url) { id ->
            if (id == clipId) url else bdmv.clip(id, extension)?.toString()
        }
    }

    private fun probeHttpBdmvFile(url: String, metadataFactory: DataSource.Factory): BlurayDisc? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val match = BDMV_FILE_PATH.find(httpUrl.encodedPath) ?: return null
        val folder = match.groupValues[1]
        val bdmv = HttpBdmv(httpUrl.resolve("./") ?: return null, folder != folder.lowercase(Locale.US), httpUrl.encodedQuery)
        val playlists = fetchHttpPlaylists(bdmv, metadataFactory)
        if (playlists.isEmpty()) {
            Log.w(TAG, "no playlists found beside ${httpUrl.encodedPath}")
            return null
        }
        return buildDisc(playlists, currentClipId = null) { id -> bdmv.clip(id)?.toString() }
    }

    private fun probeHttpPlaylistFile(url: String, metadataFactory: DataSource.Factory): BlurayDisc? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val match = MPLS_PATH.find(httpUrl.encodedPath) ?: return null
        val folder = match.groupValues[2]
        val name = httpUrl.pathSegments.lastOrNull().orEmpty()
        val bdmv = HttpBdmv(httpUrl.resolve("../") ?: return null, folder != folder.lowercase(Locale.US), httpUrl.encodedQuery)
        val selected = readBytes(metadataFactory, Uri.parse(url), MPLS_BYTES)?.let { parseMpls(name, it) } ?: return null
        val others = fetchHttpPlaylists(bdmv, metadataFactory, probeCandidates = false)
            .filterNot { it.name.equals(name, ignoreCase = true) }
        return buildDisc(listOf(selected) + others, currentClipId = null, preferredFeature = name) { id ->
            bdmv.clip(id)?.toString()
        }
    }

    private fun probeHttpDirectory(url: String, metadataFactory: DataSource.Factory): BlurayDisc? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val rootUrl = if (httpUrl.encodedPath.endsWith("/")) httpUrl else httpUrl.newBuilder().addPathSegment("").build()
        val bdmv = HttpBdmv(rootUrl.resolve("BDMV/") ?: return null, upper = true, query = httpUrl.encodedQuery)
        val playlists = fetchHttpPlaylists(bdmv, metadataFactory)
        if (playlists.isEmpty()) {
            Log.w(TAG, "HTTP Blu-ray folder has no .mpls at ${bdmv.playlistDir?.encodedPath}")
            return null
        }
        return buildDisc(playlists, currentClipId = null) { id -> bdmv.clip(id)?.toString() }
    }

    private fun fetchHttpPlaylists(
        bdmv: HttpBdmv,
        metadataFactory: DataSource.Factory,
        probeCandidates: Boolean = true,
    ): List<ParsedMpls> {
        val listed = bdmv.playlistDir
            ?.let { readBytes(metadataFactory, Uri.parse(it.toString()), LISTING_BYTES, quiet = true) }
            ?.let { extractPlaylistNames(String(it, Charsets.UTF_8)) }
            .orEmpty()
        fetchPlaylistNames(bdmv, listed.take(MAX_PLAYLISTS), metadataFactory).takeIf { it.isNotEmpty() }?.let { return it }
        val fromMovieObjects = bdmv.movieObjects.firstNotNullOfOrNull { url ->
            readBytes(metadataFactory, Uri.parse(url.toString()), MPLS_BYTES, quiet = true)
                ?.let(::parseMovieObjectPlaylists)
                ?.takeIf { it.isNotEmpty() }
        }.orEmpty()
        if (fromMovieObjects.isNotEmpty()) {
            Log.i(TAG, "playlists from movie objects: ${fromMovieObjects.size}")
            fetchPlaylistNames(bdmv, fromMovieObjects.map(::playlistName), metadataFactory)
                .takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        if (!probeCandidates) return emptyList()
        return fetchPlaylistNames(bdmv, CANDIDATE_PLAYLISTS.map(::playlistName), metadataFactory)
    }

    private fun fetchPlaylistNames(bdmv: HttpBdmv, names: List<String>, metadataFactory: DataSource.Factory): List<ParsedMpls> {
        if (names.isEmpty()) return emptyList()
        return parallel(names) { name ->
            bdmv.playlist(name)
                ?.let { readBytes(metadataFactory, Uri.parse(it.toString()), MPLS_BYTES, quiet = true) }
                ?.let { parseMpls(name, it) }
        }
    }

    private fun playlistName(number: Int): String = "%05d.mpls".format(Locale.US, number)

    fun parseMovieObjectPlaylists(data: ByteArray): List<Int> {
        if (data.size < 46 || readAscii(data, 0, 4) != "MOBJ") return emptyList()
        val objectCount = readUInt16(data, 48)
        var cursor = 50
        val out = LinkedHashSet<Int>()
        repeat(objectCount) {
            if (cursor + 4 > data.size) return out.toList()
            val commandCount = readUInt16(data, cursor + 2)
            cursor += 4
            repeat(commandCount) {
                if (cursor + 12 > data.size) return out.toList()
                val head = data[cursor].toInt() and 0xFF
                val options = data[cursor + 1].toInt() and 0xFF
                val operands = head ushr 5
                val group = (head ushr 3) and 0x3
                val subGroup = head and 0x7
                val immediate = options and 0x80 != 0
                val branch = options and 0x0F
                if (operands >= 1 && group == 0 && subGroup == 2 && immediate && branch <= 2) {
                    val playlist = readUInt32(data, cursor + 4)
                    if (playlist in 0..99_999) out += playlist.toInt()
                }
                cursor += 12
            }
        }
        return out.toList()
    }

    private fun buildDisc(
        playlists: List<ParsedMpls>,
        currentClipId: String?,
        currentClipUri: String? = null,
        preferredFeature: String? = null,
        clipUri: (String) -> String?,
    ): BlurayDisc? {
        fun toTitle(playlist: ParsedMpls): BlurayTitle? {
            val clips = playlist.clipIds.withIndex().mapNotNull { (index, id) ->
                clipUri(id)?.let { it to playlist.itemDurationMs(index) }
            }
            if (clips.isEmpty()) return null
            return BlurayTitle(playlist.name, clips.map { it.first }, clips.map { it.second })
        }
        val main = playlists.firstOrNull { it.name.equals(preferredFeature, ignoreCase = true) }
            ?: selectMainPlaylist(if (currentClipId != null) playlists.filter { currentClipId in it.clipIds } else playlists)
        val mainTitle = main?.let(::toTitle)
        if (mainTitle == null) {
            return currentClipUri?.let { singleClip(it) }
        }
        val titles = selectTitles(playlists).mapNotNull(::toTitle).toMutableList()
        var featureIndex = titles.indexOfFirst { it.name == mainTitle.name }
        if (featureIndex < 0) featureIndex = titles.indexOfFirst { it.clipUris == mainTitle.clipUris }
        if (featureIndex < 0) {
            titles.add(0, mainTitle)
            featureIndex = 0
        }
        Log.i(TAG, "Blu-ray feature ${titles[featureIndex].name}: ${titles[featureIndex].clipUris.size} clips, ${titles.size} titles")
        return BlurayDisc(titles = titles, featureIndex = featureIndex)
    }

    fun selectTitles(playlists: List<ParsedMpls>): List<ParsedMpls> = playlists
        .filter { !it.isLoop() && it.durationMs >= MIN_TITLE_MS }
        .sortedWith(compareByDescending<ParsedMpls> { it.duration90kHz }.thenBy { it.name })
        .distinctBy { it.clipIds to it.durationMs / 1000 }
        .take(MAX_TITLES)

    private fun ParsedMpls.isLoop() = clipIds.groupingBy { it }.eachCount().values.any { it > MAX_CLIP_REPEATS }

    fun selectMainPlaylist(playlists: List<ParsedMpls>): ParsedMpls? = playlists
        .filter { !it.isLoop() }
        .maxWithOrNull(compareBy<ParsedMpls> { it.duration90kHz }.thenBy { it.clipIds.size }.thenBy { it.name })

    private fun <T : Any> parallel(names: List<String>, block: (String) -> T?): List<T> {
        val pool = Executors.newFixedThreadPool(PLAYLIST_THREADS)
        return try {
            names.map { name -> pool.submit(Callable { runCatching { block(name) }.getOrNull() }) }
                .mapNotNull { future -> runCatching { future.get(30, TimeUnit.SECONDS) }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    fun isBdavPacketPattern(bytes: ByteArray): Boolean {
        if (bytes.size < 2 * BDAV_PACKET_SIZE + 5) return false
        return (0..2).all { packet -> (bytes[packet * BDAV_PACKET_SIZE + 4].toInt() and 0xFF) == TS_SYNC_BYTE }
    }

    fun parseMpls(name: String, data: ByteArray): ParsedMpls? {
        if (data.size < 32 || readAscii(data, 0, 4) != "MPLS") return null

        val playlistStart = readUInt32(data, 8).toInt()
        if (playlistStart <= 0 || playlistStart + 10 > data.size) return null

        val sectionStart = playlistStart + 4
        val playItemCount = readUInt16(data, sectionStart + 2)
        var cursor = sectionStart + 6

        val clipIds = ArrayList<String>(playItemCount)
        val itemTicks = ArrayList<Long>(playItemCount)
        var duration90kHz = 0L
        for (item in 0 until playItemCount) {
            if (cursor + 2 > data.size) break
            val itemLength = readUInt16(data, cursor)
            val itemStart = cursor + 2
            val itemEnd = itemStart + itemLength
            if (itemLength <= 0 || itemEnd > data.size || itemStart + 20 > data.size) break

            val clipId = readAscii(data, itemStart, 5)
            val codecId = readAscii(data, itemStart + 5, 4)
            val inTime = readUInt32(data, itemStart + 12)
            val outTime = readUInt32(data, itemStart + 16)
            if (clipId.length == 5 && clipId.all { it.isDigit() } && codecId.equals("M2TS", ignoreCase = true)) {
                clipIds += clipId
                itemTicks += if (outTime > inTime) outTime - inTime else 0L
            }
            if (outTime > inTime) duration90kHz += outTime - inTime
            cursor = itemEnd
        }
        return if (clipIds.isEmpty()) null else ParsedMpls(name, clipIds, duration90kHz, itemTicks)
    }

    fun looksLikeBlurayDirectoryHtml(content: String): Boolean {
        val lower = content.lowercase(Locale.US)
        return (lower.contains("<html") || lower.contains("<a ")) &&
            lower.contains("bdmv") &&
            (lower.contains("certificate") || lower.contains("playlist"))
    }

    fun extractPlaylistNames(content: String): List<String> =
        Regex("""(?i)(\d{5}\.mpls)""").findAll(content).map { it.groupValues[1] }.distinct().toList()

    fun readBytes(factory: DataSource.Factory, uri: Uri, maxBytes: Int, quiet: Boolean = false): ByteArray? {
        val source = factory.createDataSource()
        return try {
            source.open(DataSpec.Builder().setUri(uri).setLength(maxBytes.toLong()).build())
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 shl 10)
            while (out.size() < maxBytes) {
                val read = source.read(buffer, 0, minOf(buffer.size, maxBytes - out.size()))
                if (read == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } catch (error: Exception) {
            if (!quiet) Log.w(TAG, "read failed for ${uri.lastPathSegment}: ${error.javaClass.simpleName}: ${error.message}")
            null
        } finally {
            runCatching { source.close() }
        }
    }

    private fun isHttp(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun toLocalFile(url: String): File? {
        if (url.isBlank()) return null
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        val candidate = when {
            parsed == null || parsed.scheme.isNullOrEmpty() -> File(url)
            parsed.scheme.equals("file", ignoreCase = true) -> File(parsed.path ?: return null)
            else -> return null
        }
        if (candidate.exists()) return candidate
        val decodedPath = runCatching { Uri.decode(candidate.path) }.getOrNull().orEmpty()
        if (decodedPath.isNotBlank()) {
            val decoded = File(decodedPath)
            if (decoded.exists()) return decoded
        }
        return null
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int {
        if (offset + 1 >= data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        if (offset + 3 >= data.size) return 0L
        return ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
    }

    private fun readAscii(data: ByteArray, offset: Int, length: Int): String {
        if (offset < 0 || length <= 0 || offset + length > data.size) return ""
        return String(data, offset, length, StandardCharsets.US_ASCII).trim()
    }

    private fun findChildDirectory(parent: File, expectedName: String): File? =
        parent.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(expectedName, ignoreCase = true) }

    private fun extractPath(url: String): String =
        url.substringBefore('#').substringBefore('?').substringAfter("://").let { rest ->
            if ('/' in rest) rest.substring(rest.indexOf('/')) else rest
        }

    private fun looksLikeBlurayPath(url: String): Boolean {
        val path = extractPath(url).lowercase(Locale.US)
        return path.contains("/bdmv") || path.endsWith(".mpls") || path.endsWith("index.bdmv")
    }
}
