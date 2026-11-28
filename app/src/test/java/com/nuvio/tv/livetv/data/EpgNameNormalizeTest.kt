package com.nuvio.tv.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The name cleanup guide matching compares after. A playlist channel only matches a guide
 * channel when the two come out equal here, so anything left behind by the cleanup keeps them
 * apart no matter how the guide spells the channel.
 */
class EpgNameNormalizeTest {
    private fun norm(name: String) = LiveTvRepository.normalize(name)

    @Test
    fun `quality words are dropped`() {
        assertEquals("bbcone", norm("UK: BBC One FHD"))
        assertEquals("bbcone", norm("UK: BBC One HD"))
        assertEquals("tntsports1", norm("UK: TNT Sports 1 HD"))
    }

    @Test
    fun `a frame rate in the name is dropped`() {
        // Reported by a user whose playlist carries "50FPS": matching left it as "bbcone50fps",
        // which never equalled the guide's "bbcone", so the channel had to be assigned by hand.
        assertEquals("bbcone", norm("UK: BBC One FHD 50FPS"))
        assertEquals("bbcone", norm("UK: BBC One HD50FPS"))
        assertEquals("bbcone", norm("UK: BBC One 25 FPS"))
        assertEquals("skywitness", norm("UK: Sky Witness FHD 50FPS"))
    }

    @Test
    fun `a playlist name with a frame rate matches the plain guide name`() {
        assertEquals(norm("BBC One"), norm("UK: BBC One FHD 50FPS"))
        assertEquals(norm("Sky Witness"), norm("UK: Sky Witness FHD 50FPS"))
        assertEquals(norm("Sky Sports Ultra 1"), norm("UK: Sky Sports Ultra HD 1"))
    }

    @Test
    fun `different channels are still kept apart`() {
        assertNotEquals(norm("BBC One"), norm("BBC Two"))
        assertNotEquals(norm("UK: BBC One FHD"), norm("UK: BBC Two FHD"))
        assertNotEquals(norm("Sky Sports Premier League"), norm("Sky Sports Football"))
    }

    @Test
    fun `a word that merely starts like a frame rate is kept`() {
        // "fps" only counts as a frame rate when it follows a number.
        assertEquals("fpsone", norm("FPS One"))
    }
}
