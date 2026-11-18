@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

internal class BlurayIsoImage private constructor(
    val isoUri: Uri,
    val id: String,
    private val udf: UdfVolume,
    private val directories: Map<String, Map<String, UdfVolume.DirEntry>>,
) {
    class Extent(val fileOffset: Long, val isoOffset: Long, val length: Long)

    class IsoFile(val path: String, val size: Long, val extents: List<Extent>, val inline: ByteArray?)

    private val resolved = HashMap<String, IsoFile?>()

    fun list(directory: String): List<String> =
        directories[directory.uppercase(Locale.US)]?.values?.filterNot { it.isDirectory }?.map { it.name }.orEmpty()

    fun file(path: String): IsoFile? {
        val normalized = path.trim('/').uppercase(Locale.US)
        synchronized(resolved) {
            if (resolved.containsKey(normalized)) return resolved[normalized]
        }
        val directory = normalized.substringBeforeLast('/', "")
        val name = normalized.substringAfterLast('/')
        val entry = directories[directory]?.get(name)?.takeUnless { it.isDirectory }
        val file = entry?.let { runCatching { udf.resolveFile(normalized, it.icb) }.getOrNull() }
        synchronized(resolved) { resolved[normalized] = file }
        return file
    }

    fun readFile(path: String, maxBytes: Int): ByteArray? {
        val file = file(path) ?: return null
        return runCatching { udf.readFile(file, maxBytes) }.getOrNull()
    }

    fun clipUri(path: String): String = Uri.Builder()
        .scheme(SCHEME)
        .authority(id)
        .path("/" + path.trim('/'))
        .build()
        .toString()

    fun releaseReadCache() = udf.releaseCache()

    companion object {
        private const val TAG = "BlurayIsoImage"
        const val SCHEME = "nuvioiso"
        private const val MAX_IMAGES = 4

        private val images = object : LinkedHashMap<String, BlurayIsoImage>(MAX_IMAGES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BlurayIsoImage>?) = size > MAX_IMAGES
        }

        fun isIsoUri(uri: Uri): Boolean = uri.scheme.equals(SCHEME, ignoreCase = true)

        fun forUri(uri: Uri): BlurayIsoImage? = synchronized(images) { uri.authority?.let(images::get) }

        fun isLikelyIsoUrl(url: String): Boolean {
            val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.US)
            return path.endsWith(".iso")
        }

        fun looksLikeUdfImage(head: ByteArray): Boolean {
            var sector = 16
            while ((sector + 1) * UdfVolume.SECTOR_SIZE <= head.size && sector < 64) {
                val offset = sector * UdfVolume.SECTOR_SIZE + 1
                val identifier = String(head, offset, 5, Charsets.US_ASCII)
                if (identifier == "NSR02" || identifier == "NSR03") return true
                if (identifier != "BEA01" && identifier != "CD001" && identifier != "BOOT2" &&
                    identifier != "CDW02" && identifier != "TEA01"
                ) return false
                sector++
            }
            return false
        }

        fun open(isoUri: Uri, dataSourceFactory: DataSource.Factory): BlurayIsoImage? {
            val id = idFor(isoUri)
            synchronized(images) { images[id]?.let { return it } }
            val udf = try {
                UdfVolume.open(SectorReader(isoUri, dataSourceFactory))
            } catch (error: Exception) {
                Log.w(TAG, "not a readable UDF image: ${error.javaClass.simpleName}: ${error.message}")
                null
            } ?: return null
            val bdmv = udf.root.entries["BDMV"]?.takeIf { it.isDirectory } ?: run {
                Log.w(TAG, "image has no BDMV directory")
                return null
            }
            val bdmvEntries = udf.readDirectory(bdmv.icb)
            val directories = HashMap<String, Map<String, UdfVolume.DirEntry>>()
            directories["BDMV"] = bdmvEntries
            for (child in listOf("PLAYLIST", "STREAM", "CLIPINF")) {
                val entry = bdmvEntries[child]?.takeIf { it.isDirectory } ?: continue
                directories["BDMV/$child"] = runCatching { udf.readDirectory(entry.icb) }.getOrElse { emptyMap() }
            }
            if (directories["BDMV/PLAYLIST"].isNullOrEmpty() || directories["BDMV/STREAM"].isNullOrEmpty()) {
                Log.w(TAG, "image has no Blu-ray playlists or clips")
                return null
            }
            val image = BlurayIsoImage(isoUri, id, udf, directories)
            Log.i(
                TAG,
                "Blu-ray image: ${directories["BDMV/PLAYLIST"]?.size ?: 0} playlists, " +
                    "${directories["BDMV/STREAM"]?.size ?: 0} clips",
            )
            synchronized(images) { images[id] = image }
            return image
        }

        private fun idFor(uri: Uri): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray(Charsets.UTF_8))
            return digest.take(10).joinToString("") { "%02x".format(it) }
        }
    }

    internal class SectorReader(private val uri: Uri, private val dataSourceFactory: DataSource.Factory) {
        private val chunks = object : LinkedHashMap<Long, ByteArray>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) = size > MAX_CHUNKS
        }

        fun read(position: Long, length: Int): ByteArray {
            val out = ByteArray(length)
            var written = 0
            while (written < length) {
                val absolute = position + written
                val chunkIndex = absolute / CHUNK_SIZE
                val chunk = chunk(chunkIndex)
                val inChunk = (absolute - chunkIndex * CHUNK_SIZE).toInt()
                if (inChunk >= chunk.size) throw IOException("read past end of image at $absolute")
                val count = minOf(length - written, chunk.size - inChunk)
                System.arraycopy(chunk, inChunk, out, written, count)
                written += count
            }
            return out
        }

        fun releaseCache() = synchronized(chunks) { chunks.clear() }

        private fun chunk(index: Long): ByteArray {
            synchronized(chunks) { chunks[index]?.let { return it } }
            val bytes = fetch(index * CHUNK_SIZE, CHUNK_SIZE)
            synchronized(chunks) { chunks[index] = bytes }
            return bytes
        }

        private fun fetch(position: Long, length: Int): ByteArray {
            val source = dataSourceFactory.createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(uri).setPosition(position).setLength(length.toLong()).build())
                val buffer = ByteArray(length)
                var total = 0
                while (total < length) {
                    val read = source.read(buffer, total, length - total)
                    if (read == C.RESULT_END_OF_INPUT) break
                    total += read
                }
                return if (total == length) buffer else buffer.copyOf(total)
            } finally {
                runCatching { source.close() }
            }
        }

        private companion object {
            const val CHUNK_SIZE = 64 * 1024
            const val MAX_CHUNKS = 48
        }
    }
}

internal class UdfVolume private constructor(
    private val reader: BlurayIsoImage.SectorReader,
    private val blockSize: Int,
    private val partitions: List<Partition>,
) {
    class LongAd(val length: Long, val block: Long, val partition: Int)

    class DirEntry(val name: String, val isDirectory: Boolean, val icb: LongAd)

    class Root(val entries: Map<String, DirEntry>)

    private class Run(val sector: Long, val blocks: Long)

    private sealed interface Partition {
        class Physical(val startSector: Long) : Partition
        class Metadata(val physical: Physical, val runs: List<Run>) : Partition
    }

    private class FileEntry(
        val isDirectory: Boolean,
        val size: Long,
        val ads: List<RawAd>,
        val inline: ByteArray?,
    )

    private class RawAd(val type: Int, val length: Long, val block: Long, val partition: Int)

    private class PartitionMap(val number: Int, val metadataLocations: List<Long>?)

    lateinit var root: Root
        private set

    fun releaseCache() = reader.releaseCache()

    fun readDirectory(icb: LongAd): Map<String, DirEntry> {
        val entry = readFileEntry(icb)
        if (!entry.isDirectory) throw IOException("not a directory")
        val data = readEntryData(entry, icb.partition, MAX_DIRECTORY_BYTES)
        return parseDirectory(data)
    }

    fun resolveFile(path: String, icb: LongAd): BlurayIsoImage.IsoFile {
        val entry = readFileEntry(icb)
        if (entry.inline != null) {
            return BlurayIsoImage.IsoFile(path, entry.inline.size.toLong(), emptyList(), entry.inline)
        }
        val extents = ArrayList<BlurayIsoImage.Extent>()
        var fileOffset = 0L
        for (ad in entry.ads) {
            if (fileOffset >= entry.size) break
            val length = minOf(ad.length, entry.size - fileOffset)
            if (ad.type == AD_RECORDED) {
                for ((isoOffset, runLength) in physicalRuns(ad.partition, ad.block, length)) {
                    val last = extents.lastOrNull()
                    if (last != null && last.isoOffset >= 0 && last.isoOffset + last.length == isoOffset &&
                        last.fileOffset + last.length == fileOffset
                    ) {
                        extents[extents.size - 1] = BlurayIsoImage.Extent(last.fileOffset, last.isoOffset, last.length + runLength)
                    } else {
                        extents += BlurayIsoImage.Extent(fileOffset, isoOffset, runLength)
                    }
                    fileOffset += runLength
                }
            } else {
                extents += BlurayIsoImage.Extent(fileOffset, -1L, length)
                fileOffset += length
            }
        }
        return BlurayIsoImage.IsoFile(path, minOf(entry.size, fileOffset), extents, null)
    }

    fun readFile(file: BlurayIsoImage.IsoFile, maxBytes: Int): ByteArray {
        file.inline?.let { return if (it.size > maxBytes) it.copyOf(maxBytes) else it }
        val total = minOf(file.size, maxBytes.toLong()).toInt()
        val out = ByteArray(total)
        for (extent in file.extents) {
            if (extent.fileOffset >= total) break
            val count = minOf(extent.length, (total - extent.fileOffset)).toInt()
            if (extent.isoOffset >= 0) {
                reader.read(extent.isoOffset, count).copyInto(out, extent.fileOffset.toInt())
            }
        }
        return out
    }

    private fun readFileEntry(icb: LongAd): FileEntry {
        val sector = sectorOffset(icb.partition, icb.block)
        val b = reader.read(sector, blockSize)
        val tag = u16(b, 0)
        if (tag == TAG_INDIRECT_ENTRY) {
            return readFileEntry(longAd(b, 36))
        }
        val (eaLengthAt, adLengthAt, headerLength) = when (tag) {
            TAG_FILE_ENTRY -> Triple(168, 172, 176)
            TAG_EXTENDED_FILE_ENTRY -> Triple(208, 212, 216)
            else -> throw IOException("unexpected tag $tag for a file entry")
        }
        val fileType = b[27].u8()
        val flags = u16(b, 34)
        val size = u64(b, 56)
        val eaLength = u32(b, eaLengthAt)
        val adLength = u32(b, adLengthAt)
        val adStart = headerLength + eaLength.toInt()
        if (adStart < 0 || adStart + adLength > b.size) throw IOException("file entry descriptors out of range")
        val isDirectory = fileType == FILE_TYPE_DIRECTORY
        return when (val adType = flags and 0x7) {
            AD_TYPE_EMBEDDED -> FileEntry(
                isDirectory = isDirectory,
                size = size,
                ads = emptyList(),
                inline = b.copyOfRange(adStart, adStart + adLength.toInt()),
            )
            AD_TYPE_SHORT, AD_TYPE_LONG -> FileEntry(
                isDirectory = isDirectory,
                size = size,
                ads = parseAds(b, adStart, adLength.toInt(), adType, icb.partition, depth = 0),
                inline = null,
            )
            else -> throw IOException("unsupported allocation descriptor type $adType")
        }
    }

    private fun parseAds(b: ByteArray, start: Int, length: Int, adType: Int, partition: Int, depth: Int): List<RawAd> {
        val out = ArrayList<RawAd>()
        val size = if (adType == AD_TYPE_SHORT) 8 else 16
        var p = start
        val end = start + length
        while (p + size <= end) {
            val raw = u32(b, p)
            val type = ((raw ushr 30) and 0x3).toInt()
            val extentLength = raw and 0x3FFFFFFF
            if (extentLength == 0L) break
            val block = u32(b, p + 4)
            val adPartition = if (adType == AD_TYPE_SHORT) partition else u16(b, p + 8)
            if (type == AD_NEXT_EXTENT) {
                if (depth > MAX_AD_DEPTH) throw IOException("allocation extent chain too long")
                val next = reader.read(sectorOffset(adPartition, block), blockSize)
                if (u16(next, 0) != TAG_ALLOCATION_EXTENT) throw IOException("bad allocation extent")
                val nextLength = u32(next, 20).toInt().coerceAtMost(blockSize - 24)
                out += parseAds(next, 24, nextLength, adType, partition, depth + 1)
                break
            }
            out += RawAd(type, extentLength, block, adPartition)
            p += size
        }
        return out
    }

    private fun readEntryData(entry: FileEntry, partition: Int, maxBytes: Int): ByteArray {
        entry.inline?.let { return it }
        val total = minOf(entry.size, maxBytes.toLong()).toInt()
        val out = ByteArray(total)
        var offset = 0L
        for (ad in entry.ads) {
            if (offset >= total) break
            val length = minOf(ad.length, total - offset)
            if (ad.type == AD_RECORDED) {
                var written = offset
                for ((isoOffset, runLength) in physicalRuns(ad.partition, ad.block, length)) {
                    reader.read(isoOffset, runLength.toInt()).copyInto(out, written.toInt())
                    written += runLength
                }
            }
            offset += length
        }
        return out
    }

    private fun parseDirectory(data: ByteArray): Map<String, DirEntry> {
        val out = LinkedHashMap<String, DirEntry>()
        var p = 0
        while (p + 38 <= data.size) {
            if (u16(data, p) != TAG_FILE_IDENTIFIER) break
            val characteristics = data[p + 18].u8()
            val nameLength = data[p + 19].u8()
            val icb = longAd(data, p + 20)
            val implementationLength = u16(data, p + 36)
            val nameStart = p + 38 + implementationLength
            if (nameStart + nameLength > data.size) break
            val deleted = characteristics and 0x04 != 0
            val parent = characteristics and 0x08 != 0
            if (!deleted && !parent && nameLength > 0) {
                val name = decodeDString(data, nameStart, nameLength)
                if (name.isNotEmpty()) {
                    out[name.uppercase(Locale.US)] = DirEntry(name, characteristics and 0x02 != 0, icb)
                }
            }
            p += (38 + implementationLength + nameLength + 3) and 3.inv()
        }
        return out
    }

    private fun physicalRuns(partition: Int, block: Long, length: Long): List<Pair<Long, Long>> {
        val part = partitions.getOrNull(partition) ?: throw IOException("unknown partition $partition")
        return when (part) {
            is Partition.Physical -> listOf((part.startSector + block) * blockSize to length)
            is Partition.Metadata -> metadataRuns(part, block, length)
                ?: listOf((part.physical.startSector + block) * blockSize to length)
        }
    }

    private fun metadataRuns(part: Partition.Metadata, block: Long, length: Long): List<Pair<Long, Long>>? {
        val out = ArrayList<Pair<Long, Long>>()
        var remaining = length
        var current = block
        var runStart = 0L
        for (run in part.runs) {
            if (remaining <= 0) break
            val runEnd = runStart + run.blocks
            if (current in runStart until runEnd) {
                val blocksHere = runEnd - current
                val bytesHere = minOf(remaining, blocksHere * blockSize)
                out += (run.sector + (current - runStart)) * blockSize to bytesHere
                remaining -= bytesHere
                current += (bytesHere + blockSize - 1) / blockSize
            }
            runStart = runEnd
        }
        return if (remaining > 0) null else out
    }

    private fun sectorOffset(partition: Int, block: Long): Long =
        physicalRuns(partition, block, blockSize.toLong()).first().first

    companion object {
        const val SECTOR_SIZE = 2048
        private const val AVDP_SECTOR = 256L
        private const val MAX_DIRECTORY_BYTES = 4 shl 20
        private const val MAX_AD_DEPTH = 64

        private const val TAG_PRIMARY_VOLUME = 1
        private const val TAG_ANCHOR = 2
        private const val TAG_VOLUME_POINTER = 3
        private const val TAG_PARTITION = 5
        private const val TAG_LOGICAL_VOLUME = 6
        private const val TAG_TERMINATING = 8
        private const val TAG_FILE_SET = 256
        private const val TAG_FILE_IDENTIFIER = 257
        private const val TAG_ALLOCATION_EXTENT = 258
        private const val TAG_INDIRECT_ENTRY = 259
        private const val TAG_FILE_ENTRY = 261
        private const val TAG_EXTENDED_FILE_ENTRY = 266

        private const val FILE_TYPE_DIRECTORY = 4
        private const val AD_TYPE_SHORT = 0
        private const val AD_TYPE_LONG = 1
        private const val AD_TYPE_EMBEDDED = 3
        private const val AD_RECORDED = 0
        private const val AD_NEXT_EXTENT = 3

        fun open(reader: BlurayIsoImage.SectorReader): UdfVolume {
            val anchor = reader.read(AVDP_SECTOR * SECTOR_SIZE, SECTOR_SIZE)
            if (u16(anchor, 0) != TAG_ANCHOR) throw IOException("no anchor volume descriptor")
            val sequences = listOf(u32(anchor, 20) to u32(anchor, 16), u32(anchor, 28) to u32(anchor, 24))
            var lastError: Exception? = null
            for ((location, length) in sequences) {
                if (location <= 0L || length <= 0L) continue
                try {
                    return fromSequence(reader, location, length)
                } catch (error: Exception) {
                    lastError = error
                }
            }
            throw lastError ?: IOException("no volume descriptor sequence")
        }

        private fun fromSequence(reader: BlurayIsoImage.SectorReader, location: Long, length: Long): UdfVolume {
            val partitionStarts = HashMap<Int, Long>()
            var logicalVolume: ByteArray? = null
            var sector = location
            var end = location + (minOf(length, 64L * SECTOR_SIZE) + SECTOR_SIZE - 1) / SECTOR_SIZE
            var hops = 0
            while (sector < end) {
                val d = reader.read(sector * SECTOR_SIZE, SECTOR_SIZE)
                when (u16(d, 0)) {
                    TAG_PARTITION -> partitionStarts.putIfAbsent(u16(d, 22), u32(d, 188))
                    TAG_LOGICAL_VOLUME -> if (logicalVolume == null) logicalVolume = d
                    TAG_VOLUME_POINTER -> {
                        if (++hops > 8) break
                        sector = u32(d, 24)
                        end = sector + (minOf(u32(d, 20), 64L * SECTOR_SIZE) + SECTOR_SIZE - 1) / SECTOR_SIZE
                        continue
                    }
                    TAG_TERMINATING -> break
                    TAG_PRIMARY_VOLUME -> Unit
                }
                sector++
            }
            val lvd = logicalVolume ?: throw IOException("no logical volume descriptor")
            val blockSize = u32(lvd, 212).toInt().takeIf { it == SECTOR_SIZE } ?: throw IOException("unsupported block size")
            val fileSet = longAd(lvd, 248)
            val mapCount = u32(lvd, 268).toInt()
            val mapTableLength = u32(lvd, 264).toInt()
            val maps = ArrayList<PartitionMap>()
            var p = 440
            val mapsEnd = minOf(lvd.size, 440 + mapTableLength)
            repeat(mapCount) {
                if (p + 2 > mapsEnd) return@repeat
                val type = lvd[p].u8()
                val mapLength = lvd[p + 1].u8()
                if (mapLength < 2 || p + mapLength > mapsEnd) return@repeat
                when (type) {
                    1 -> maps += PartitionMap(u16(lvd, p + 4), metadataLocations = null)
                    2 -> {
                        val identifier = String(lvd, p + 5, 23, Charsets.US_ASCII).trimEnd('\u0000', ' ')
                        maps += when {
                            identifier.startsWith("*UDF Metadata Partition") ->
                                PartitionMap(u16(lvd, p + 38), listOf(u32(lvd, p + 40), u32(lvd, p + 44)))
                            identifier.startsWith("*UDF Sparable Partition") ->
                                PartitionMap(u16(lvd, p + 38), metadataLocations = null)
                            else -> throw IOException("unsupported partition map $identifier")
                        }
                    }
                }
                p += mapLength
            }
            if (maps.isEmpty()) throw IOException("no partition maps")
            val physicalByNumber = HashMap<Int, Partition.Physical>()
            val partitions = maps.map { map ->
                physicalByNumber.getOrPut(map.number) {
                    Partition.Physical(partitionStarts[map.number] ?: throw IOException("missing partition ${map.number}"))
                }
            }.toMutableList<Partition>()
            val bootstrap = UdfVolume(reader, blockSize, partitions.toList())
            maps.forEachIndexed { index, map ->
                val locations = map.metadataLocations ?: return@forEachIndexed
                val physical = partitions[index] as Partition.Physical
                val runs = locations.firstNotNullOfOrNull { location ->
                    runCatching { bootstrap.metadataFileRuns(physical, location) }.getOrNull()?.takeIf { it.isNotEmpty() }
                } ?: throw IOException("unreadable metadata partition")
                partitions[index] = Partition.Metadata(physical, runs)
            }
            val volume = UdfVolume(reader, blockSize, partitions)
            val fsd = reader.read(volume.sectorOffset(fileSet.partition, fileSet.block), blockSize)
            if (u16(fsd, 0) != TAG_FILE_SET) throw IOException("no file set descriptor")
            val rootIcb = longAd(fsd, 400)
            volume.root = Root(volume.readDirectory(rootIcb))
            return volume
        }

        private fun longAd(b: ByteArray, at: Int) = LongAd(u32(b, at) and 0x3FFFFFFF, u32(b, at + 4), u16(b, at + 8))

        private fun decodeDString(b: ByteArray, at: Int, length: Int): String {
            if (length < 1) return ""
            return when (b[at].u8()) {
                8 -> String(b, at + 1, length - 1, Charsets.ISO_8859_1)
                16 -> String(b, at + 1, (length - 1) and 1.inv(), Charsets.UTF_16BE)
                else -> ""
            }.trimEnd('\u0000')
        }

        private fun Byte.u8() = toInt() and 0xFF

        private fun u16(b: ByteArray, at: Int): Int = (b[at].u8()) or (b[at + 1].u8() shl 8)

        private fun u32(b: ByteArray, at: Int): Long =
            (b[at].u8().toLong()) or (b[at + 1].u8().toLong() shl 8) or
                (b[at + 2].u8().toLong() shl 16) or (b[at + 3].u8().toLong() shl 24)

        private fun u64(b: ByteArray, at: Int): Long = u32(b, at) or (u32(b, at + 4) shl 32)
    }

    private fun metadataFileRuns(physical: Partition.Physical, location: Long): List<Run> {
        val b = reader.read((physical.startSector + location) * blockSize, blockSize)
        val tag = u16(b, 0)
        val (eaLengthAt, adLengthAt, headerLength) = when (tag) {
            TAG_FILE_ENTRY -> Triple(168, 172, 176)
            TAG_EXTENDED_FILE_ENTRY -> Triple(208, 212, 216)
            else -> throw IOException("unexpected metadata file tag $tag")
        }
        val adType = u16(b, 34) and 0x7
        if (adType != AD_TYPE_SHORT && adType != AD_TYPE_LONG) throw IOException("unsupported metadata descriptors")
        val adStart = headerLength + u32(b, eaLengthAt).toInt()
        val adLength = u32(b, adLengthAt).toInt()
        if (adStart + adLength > b.size) throw IOException("metadata descriptors out of range")
        val runs = ArrayList<Run>()
        val size = if (adType == AD_TYPE_SHORT) 8 else 16
        var p = adStart
        while (p + size <= adStart + adLength) {
            val raw = u32(b, p)
            val length = raw and 0x3FFFFFFF
            if (length == 0L) break
            runs += Run(physical.startSector + u32(b, p + 4), (length + blockSize - 1) / blockSize)
            p += size
        }
        return runs
    }
}
