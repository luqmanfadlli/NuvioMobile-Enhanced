package com.nuvio.app.features.livetv

import com.nuvio.app.features.player.PlaybackDrmScheme
import com.nuvio.app.features.player.clearKeyLicenseJson
import com.nuvio.app.features.player.mpvDemuxerLavfOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTvM3uDrmTest {
    private val kid = "0123456789abcdef0123456789abcdef"
    private val key = "fedcba9876543210fedcba9876543210"

    @Test
    fun clearKeyFromKodiPropIsAttachedToTheChannel() {
        val channels = parseM3uPlaylist(
            """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=${kid.uppercase()}:$key
            #EXTINF:-1,Channel One
            https://example.com/one/index.mpd
            #EXTINF:-1,Channel Two
            https://example.com/two/index.mpd
            """.trimIndent(),
        )
        val drm = channels[0].drm
        assertEquals(PlaybackDrmScheme.ClearKey, drm?.scheme)
        assertEquals(mapOf(kid to key), drm?.clearKeys)
        assertNull(channels[1].drm)
    }

    @Test
    fun widevineLicenseUrlKeepsItsHeaders() {
        val channels = parseM3uPlaylist(
            """
            #EXTINF:-1,Channel
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://license.example.com/wv|User-Agent=Test
            https://example.com/index.mpd
            """.trimIndent(),
        )
        val drm = channels.single().drm
        assertEquals(PlaybackDrmScheme.Widevine, drm?.scheme)
        assertEquals("https://license.example.com/wv", drm?.licenseUrl)
        assertEquals("Test", drm?.licenseHeaders?.get("User-Agent"))
    }

    @Test
    fun legacyDrmPropertyAndJsonKeysAreRead() {
        val legacy = parseM3uPlaylist(
            """
            #KODIPROP:inputstream.adaptive.drm_legacy=org.w3.clearkey|$kid:$key
            #EXTINF:-1,Channel
            https://example.com/index.mpd
            """.trimIndent(),
        ).single().drm
        assertEquals(mapOf(kid to key), legacy?.clearKeys)

        val json = parseM3uPlaylist(
            """
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","kid":"ASNFZ4mrze8BI0VniavN7w","k":"_ty6mHZUMhD-3LqYdlQyEA"}]}
            #EXTINF:-1,Channel
            https://example.com/index.mpd
            """.trimIndent(),
        ).single().drm
        assertEquals(mapOf(kid to key), json?.clearKeys)
    }

    @Test
    fun mpvOptionsAndClearKeyResponseAreBuiltFromTheKeys() {
        val single = parseM3uPlaylist(
            "#KODIPROP:inputstream.adaptive.license_key=$kid:$key\n#EXTINF:-1,A\nhttps://example.com/a.mpd",
        ).single().drm
        assertTrue(single.mpvDemuxerLavfOptions().endsWith(",cenc_decryption_key=$key"))
        assertEquals(
            "{\"keys\":[{\"kty\":\"oct\",\"kid\":\"ASNFZ4mrze8BI0VniavN7w\",\"k\":\"_ty6mHZUMhD-3LqYdlQyEA\"}],\"type\":\"temporary\"}",
            single!!.clearKeyLicenseJson(),
        )

        val otherKid = "11111111111111111111111111111111"
        val double = parseM3uPlaylist(
            "#KODIPROP:inputstream.adaptive.license_key=$kid:$key,$otherKid:$key\n#EXTINF:-1,A\nhttps://example.com/a.mpd",
        ).single().drm
        assertTrue(double.mpvDemuxerLavfOptions().endsWith(",cenc_decryption_keys=[$kid=$key:$otherKid=$key]"))
    }
}
