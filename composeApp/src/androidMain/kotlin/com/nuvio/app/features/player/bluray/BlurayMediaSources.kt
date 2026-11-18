@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor

internal object BlurayMediaSources {
    private const val BDAV_TIMESTAMP_SEARCH_BYTES = 10_000 * TsExtractor.TS_PACKET_SIZE
    private const val PLAYLIST_CLIP_PLACEHOLDER_MS = 60_000L

    fun create(
        disc: BlurayDisc,
        title: BlurayTitle,
        playbackFactory: DataSource.Factory,
        metadataFactory: DataSource.Factory,
        mediaItem: MediaItem,
    ): MediaSource {
        val clipInfoFactory = BlurayIsoDataSourceFactory(metadataFactory)
        val mediaFactory = BdavM2tsDataSourceFactory(BlurayIsoDataSourceFactory(playbackFactory), disc::isBdavUri)
        val clipFactory = DefaultMediaSourceFactory(mediaFactory, extractorsFactory(clipInfoFactory))
        val subtitleConfigurations = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()

        fun clipItem(uri: String, withSubtitles: Boolean): MediaItem =
            MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.VIDEO_MP2T)
                .setMediaId(mediaItem.mediaId)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .apply { if (withSubtitles) setSubtitleConfigurations(subtitleConfigurations) }
                .build()

        if (title.clipUris.size == 1) {
            return clipFactory.createMediaSource(clipItem(title.clipUris.first(), withSubtitles = true))
        }
        val builder = ConcatenatingMediaSource2.Builder().setMediaItem(mediaItem)
        title.clipUris.forEachIndexed { index, clipUri ->
            val placeholderMs = title.durationsMs.getOrNull(index)?.takeIf { it > 0 } ?: PLAYLIST_CLIP_PLACEHOLDER_MS
            builder.add(clipFactory.createMediaSource(clipItem(clipUri, withSubtitles = false)), placeholderMs)
        }
        return builder.build()
    }

    private fun extractorsFactory(clipInfoFactory: DataSource.Factory): ExtractorsFactory = object : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> = arrayOf(createTsExtractor())

        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
            val info = BlurayClipInfo.info(uri, clipInfoFactory)
            val extractor = createTsExtractor()
            return arrayOf(if (info.isEmpty) extractor else BlurayClipInfo.ClipInfoExtractor(extractor, info))
        }
    }

    private fun createTsExtractor(): Extractor {
        val payloadReaders = BdavTsPayloadReaderFactory.withDefaults(
            DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
        )
        return TsExtractor(
            TsExtractor.MODE_SINGLE_PMT,
            0,
            DefaultSubtitleParserFactory(),
            TimestampAdjuster(0),
            payloadReaders,
            BDAV_TIMESTAMP_SEARCH_BYTES,
        )
    }
}
