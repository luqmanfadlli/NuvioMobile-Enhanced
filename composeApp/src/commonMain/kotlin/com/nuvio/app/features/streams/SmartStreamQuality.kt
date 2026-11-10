package com.nuvio.app.features.streams

// Reads what a stream is (resolution, HDR, codec, audio, source, size, instant) from everything
// the app knows about it: the user's badge matches, the stream name/description/filename and the
// structured fields some addons send. Used by "Sort", "Filters" and "Play best for this device".

enum class SmartResolution(val lines: Int, val label: String) {
    SD(480, "SD"),
    HD720(720, "720p"),
    FHD1080(1080, "1080p"),
    UHD4K(2160, "4K"),
}

enum class SmartHdr(val rank: Int, val label: String) {
    HDR10(1, "HDR10"),
    HDR10_PLUS(2, "HDR10+"),
    DOLBY_VISION(3, "Dolby Vision"),
}

enum class SmartCodec(val label: String) {
    HEVC("HEVC"),
    H264("H.264"),
    AV1("AV1"),
    OTHER("Other"),
}

enum class SmartAudio(val label: String) {
    ATMOS("Atmos"),
    DOLBY_DIGITAL("DD+ / DD 5.1"),
    DTS_TRUEHD("DTS / TrueHD"),
    STEREO("Stereo"),
}

enum class SmartSource(val rank: Int, val label: String) {
    HDTV(1, "HDTV"),
    WEBRIP(2, "WEBRip"),
    WEBDL(3, "WEB-DL"),
    BLURAY(4, "Blu-ray"),
    REMUX(5, "Remux"),
}

enum class SmartInstant { YES, NO, UNKNOWN }

data class SmartStreamInfo(
    val resolution: SmartResolution?,
    val hdr: SmartHdr?,
    val codec: SmartCodec?,
    val audio: Set<SmartAudio>,
    val source: SmartSource?,
    val sizeBytes: Long?,
    val instant: SmartInstant,
    val isLowQuality: Boolean,
) {
    /** Short line such as "1080p · HEVC · Dolby Vision · Instant · 4.2 GB". */
    fun summary(): String = listOfNotNull(
        resolution?.label,
        codec?.takeIf { it != SmartCodec.OTHER }?.label,
        hdr?.label,
        if (instant == SmartInstant.YES) "Instant" else null,
        sizeBytes?.let(::formatSmartSize),
    ).joinToString(" · ")
}

internal fun formatSmartSize(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) {
        val tenths = (gb * 10).toLong()
        "${tenths / 10}.${tenths % 10} GB"
    } else {
        "${(bytes / 1_000_000.0).toLong()} MB"
    }
}

object SmartStreamQuality {
    private const val B = "(?<![a-z0-9])"
    private const val E = "(?![a-z0-9])"

    private val res4k = Regex("${B}(2160p|4k|uhd|3840x2160)$E")
    private val res1080 = Regex("${B}(1080p|1080i|fhd|1920x1080)$E")
    private val res720 = Regex("${B}(720p|1280x720)$E")
    private val resSd = Regex("${B}(480p|576p|360p|sd|dvdrip|dvd)$E")

    private val dolbyVision = Regex("${B}(dv|dovi|dolby ?vision)$E")
    private val hdr10Plus = Regex("${B}(hdr10\\+|hdr10plus|hdr10p)")
    private val hdr10 = Regex("${B}(hdr10|hdr|hlg)$E")

    private val hevc = Regex("${B}(hevc|x265|h ?265)$E")
    private val h264 = Regex("${B}(avc|x264|h ?264)$E")
    private val av1 = Regex("${B}av1$E")
    private val otherCodec = Regex("${B}(vp9|xvid|divx|mpeg2)$E")

    private val atmos = Regex("${B}atmos$E")
    private val dolbyDigital = Regex("${B}(ddp|dd\\+|eac3|e ac 3|ac3|dd|dolby digital( plus)?)")
    private val dtsTrueHd = Regex("${B}(dts|truehd|true hd)")
    private val stereo = Regex("${B}(2 0|2ch|stereo)$E")
    private val surround = Regex("${B}(5 1|7 1|6ch|8ch)$E")
    private val aac = Regex("${B}aac")

    private val remux = Regex("${B}remux$E")
    private val bluray = Regex("${B}(bluray|blu ray|bdrip|brrip|bd)$E")
    private val webdl = Regex("${B}(web dl|webdl|web)$E")
    private val webrip = Regex("${B}(webrip|web rip)$E")
    private val hdtv = Regex("${B}(hdtv|pdtv|dsr)$E")
    // Bare "ts"/"tc" are left out on purpose: ".ts" is also a normal video file extension.
    private val lowQuality = Regex("${B}(cam|camrip|hdcam|hdts|telesync|hdtc|telecine|scr|screener|dvdscr|r5)$E")

    private val instantMarkers = Regex("(\\+]|⚡|${B}(instant|cached)$E)")
    private val notInstantMarkers = Regex("${B}(download|uncached|not cached)$E")
    private val sizePattern = Regex("(\\d+(?:[.,]\\d+)?)\\s?(tb|gb|gib|mb|mib)$E")

    fun read(stream: StreamItem): SmartStreamInfo {
        val raw = buildList {
            addAll(StreamBadgeMatcher.badgeMatchCandidates(stream))
            stream.badges.forEach { add(it.name) }
        }.joinToString(" ").lowercase()
        // "WEB-DL" -> "web dl", "H.265" -> "h 265", "DD+5.1" stays readable as "dd+5 1".
        val text = raw.replace(Regex("[._\\-\\[\\]()|/,:]+"), " ")
        val parsed = stream.clientResolve?.stream?.raw?.parsed

        return SmartStreamInfo(
            resolution = readResolution(text, parsed?.resolution),
            hdr = readHdr(text, parsed?.hdr.orEmpty()),
            codec = readCodec(text, parsed?.codec),
            audio = readAudio(text),
            source = readSource(text, parsed?.quality),
            sizeBytes = stream.behaviorHints.videoSize?.takeIf { it > 0 } ?: readSize(raw),
            instant = readInstant(stream, raw),
            isLowQuality = lowQuality.containsMatchIn(text) && !webdl.containsMatchIn(text) && !bluray.containsMatchIn(text),
        )
    }

    private fun readResolution(text: String, parsed: String?): SmartResolution? {
        val p = parsed?.lowercase().orEmpty()
        return when {
            res4k.containsMatchIn(p) || res4k.containsMatchIn(text) -> SmartResolution.UHD4K
            res1080.containsMatchIn(p) || res1080.containsMatchIn(text) -> SmartResolution.FHD1080
            res720.containsMatchIn(p) || res720.containsMatchIn(text) -> SmartResolution.HD720
            resSd.containsMatchIn(p) || resSd.containsMatchIn(text) -> SmartResolution.SD
            else -> null
        }
    }

    private fun readHdr(text: String, parsed: List<String>): SmartHdr? {
        val all = (parsed.joinToString(" ").lowercase() + " " + text)
        return when {
            dolbyVision.containsMatchIn(all) -> SmartHdr.DOLBY_VISION
            hdr10Plus.containsMatchIn(all) -> SmartHdr.HDR10_PLUS
            hdr10.containsMatchIn(all) -> SmartHdr.HDR10
            else -> null
        }
    }

    private fun readCodec(text: String, parsed: String?): SmartCodec? {
        val all = parsed?.lowercase().orEmpty().replace('.', ' ') + " " + text
        return when {
            av1.containsMatchIn(all) -> SmartCodec.AV1
            hevc.containsMatchIn(all) -> SmartCodec.HEVC
            h264.containsMatchIn(all) -> SmartCodec.H264
            otherCodec.containsMatchIn(all) -> SmartCodec.OTHER
            else -> null
        }
    }

    private fun readAudio(text: String): Set<SmartAudio> = buildSet {
        if (atmos.containsMatchIn(text)) add(SmartAudio.ATMOS)
        if (dolbyDigital.containsMatchIn(text)) add(SmartAudio.DOLBY_DIGITAL)
        if (dtsTrueHd.containsMatchIn(text)) add(SmartAudio.DTS_TRUEHD)
        if (stereo.containsMatchIn(text) || (aac.containsMatchIn(text) && !surround.containsMatchIn(text))) {
            add(SmartAudio.STEREO)
        }
    }

    private fun readSource(text: String, parsed: String?): SmartSource? {
        val all = parsed?.lowercase().orEmpty().replace('-', ' ') + " " + text
        return when {
            remux.containsMatchIn(all) -> SmartSource.REMUX
            bluray.containsMatchIn(all) -> SmartSource.BLURAY
            webrip.containsMatchIn(all) -> SmartSource.WEBRIP
            webdl.containsMatchIn(all) -> SmartSource.WEBDL
            hdtv.containsMatchIn(all) -> SmartSource.HDTV
            else -> null
        }
    }

    private fun readSize(raw: String): Long? {
        val match = sizePattern.find(raw) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "tb" -> 1_000_000_000_000.0
            "gb", "gib" -> 1_000_000_000.0
            else -> 1_000_000.0
        }
        return (number * multiplier).toLong().takeIf { it > 0 }
    }

    private fun readInstant(stream: StreamItem, raw: String): SmartInstant {
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CACHED -> return SmartInstant.YES
            StreamDebridCacheState.NOT_CACHED -> return SmartInstant.NO
            else -> Unit
        }
        if (notInstantMarkers.containsMatchIn(raw)) return SmartInstant.NO
        if (instantMarkers.containsMatchIn(raw)) return SmartInstant.YES
        // A plain web link (not a torrent) plays straight away.
        if (!stream.isTorrentStream && stream.playableDirectUrl != null && !stream.shouldOpenExternally) {
            return SmartInstant.YES
        }
        // A torrent that has to be fetched peer to peer is the slowest to start.
        if (stream.needsLocalDebridResolve && !stream.isAddonDebridCandidate) return SmartInstant.NO
        return SmartInstant.UNKNOWN
    }
}
