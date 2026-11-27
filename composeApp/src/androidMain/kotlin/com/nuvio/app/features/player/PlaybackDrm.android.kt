@file:androidx.annotation.OptIn(UnstableApi::class)

package com.nuvio.app.features.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback

internal fun PlaybackDrm.exoDrmSessionManagerProvider(
    requestHeaders: Map<String, String>,
): DrmSessionManagerProvider? {
    val uuid = when (scheme) {
        PlaybackDrmScheme.ClearKey -> C.CLEARKEY_UUID
        PlaybackDrmScheme.Widevine -> C.WIDEVINE_UUID
    }
    val callback: MediaDrmCallback = if (hasLocalClearKeys) {
        LocalMediaDrmCallback(clearKeyLicenseJson().encodeToByteArray())
    } else {
        val url = licenseUrl ?: return null
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)
        HttpMediaDrmCallback(url, dataSourceFactory).apply {
            licenseHeaders.forEach { (name, value) -> setKeyRequestProperty(name, value) }
        }
    }
    val manager = DefaultDrmSessionManager.Builder()
        .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
        .build(callback)
    return DrmSessionManagerProvider { manager }
}
