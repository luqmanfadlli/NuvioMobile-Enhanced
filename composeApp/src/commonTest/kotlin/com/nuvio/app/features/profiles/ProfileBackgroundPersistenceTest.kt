package com.nuvio.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileBackgroundPersistenceTest {
    @Test
    fun `remote profile refresh preserves device local background selection`() {
        val local = NuvioProfile(
            profileIndex = 2,
            profileBackgroundId = "catalog-background",
            profileBackgroundUrl = "https://images.example/background.gif",
        )
        val remote = NuvioProfile(profileIndex = 2, name = "Remote name")

        val merged = mergeProfileBackgrounds(
            remoteProfiles = listOf(remote),
            localProfiles = listOf(local),
        ).single()

        assertEquals("Remote name", merged.name)
        assertEquals("catalog-background", merged.profileBackgroundId)
        assertEquals("https://images.example/background.gif", merged.profileBackgroundUrl)
    }

    @Test
    fun `explicit background clear wins over stale cached and remote values`() {
        val local = NuvioProfile(
            profileIndex = 1,
            profileBackgroundId = "old-id",
            profileBackgroundUrl = "https://images.example/old.gif",
        )
        val remote = local.copy(profileBackgroundId = "old-id", profileBackgroundUrl = "https://images.example/old.gif")

        val merged = mergeProfileBackgrounds(
            remoteProfiles = listOf(remote),
            localProfiles = listOf(local),
            backgroundOverrides = mapOf(1 to (null to null)),
        ).single()

        assertNull(merged.profileBackgroundId)
        assertNull(merged.profileBackgroundUrl)
    }

    @Test
    fun `remote background is retained when no local profile exists`() {
        val remote = NuvioProfile(
            profileIndex = 3,
            profileBackgroundId = "remote-id",
            profileBackgroundUrl = "https://images.example/remote.jpg",
        )

        val merged = mergeProfileBackgrounds(
            remoteProfiles = listOf(remote),
            localProfiles = emptyList(),
        ).single()

        assertEquals("remote-id", merged.profileBackgroundId)
        assertEquals("https://images.example/remote.jpg", merged.profileBackgroundUrl)
    }
}
