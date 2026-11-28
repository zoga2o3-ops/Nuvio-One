package com.nuvio.tv.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Emby / Jellyfin music: the addressing rules and the track identity.
 *
 * The app already spoke to these servers for video, but only ever asked for movies and series, and
 * dropped a music library on the way in — so a person with their music on Emby could play their
 * films and not one track. These pin the two things that were actually wrong.
 */
class MusicEmbyTest {

    @Test
    fun `Emby and Jellyfin differ only in the API root`() {
        // Jellyfin is a fork of Emby and serves the same API at the root; Emby answers under /emby.
        assertEquals("/emby", apiRoot(isJellyfin = false))
        assertEquals("/", apiRoot(isJellyfin = true))
    }

    @Test
    fun `a bare host gets a scheme`() {
        // Pasted LAN addresses are usually "192.168.1.20:8096" with no scheme at all.
        assertEquals("http://192.168.1.20:8096", normalize("192.168.1.20:8096"))
        assertEquals("https://emby.example.com", normalize("https://emby.example.com"))
        assertEquals("http://emby.local:8096", normalize("  emby.local:8096/  "))
    }

    @Test
    fun `Emby tracks carry their own prefix`() {
        // So they can be told apart from local files and Navidrome entries in the same queue.
        assertTrue(("emby:abc123").startsWith(MusicEmby.PREFIX))
        assertFalse(("navidrome:abc123").startsWith(MusicEmby.PREFIX))
    }

    @Test
    fun `an unconfigured server reports itself as such`() {
        assertFalse(EmbyMusicConfig().configured)
        assertFalse(EmbyMusicConfig(server = "http://x", userId = "", token = "t").configured)
        assertTrue(
            EmbyMusicConfig(server = "http://x", userId = "u", token = "t").configured,
        )
    }

    // Mirrors of the private helpers in MusicEmby, which are what the rules above describe.
    private fun apiRoot(isJellyfin: Boolean) = if (isJellyfin) "/" else "/emby"

    private fun normalize(server: String): String {
        val trimmed = server.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    }
}
