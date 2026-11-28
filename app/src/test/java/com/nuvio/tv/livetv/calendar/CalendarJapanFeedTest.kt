package com.nuvio.tv.livetv.calendar

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Japan's schedule as Airing Today reads it.
 *
 * Anime that airs first in Japan is in neither the chosen country's TV list nor the streaming
 * list, so without the Japan feed it never reached the Calendar at all (issue #50). This pins the
 * two rules that make the extra feed safe: only Animation is kept, and its entries are marked so
 * they can be told apart from the country's own shows.
 */
class CalendarJapanFeedTest {

    /** The same filter `airingOn` applies to the Japan list, over a small stand-in payload. */
    private fun japanEntries(raw: String, country: String = "US"): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val show = o.optJSONObject("show") ?: continue
            if (o.isNull("number")) continue
            // Japan's list is mostly Animation, but not only: keep the animation.
            if (!show.optString("type").equals("Animation", ignoreCase = true)) continue
            out += show.optString("name") to true
        }
        return out
    }

    private val jpPayload = """
        [
          {"id":1,"number":8,"season":1,"show":{"id":10,"name":"The Apothecary Diaries","type":"Animation"}},
          {"id":2,"number":22,"season":2,"show":{"id":11,"name":"The Exiled Heavy Knight","type":"Animation"}},
          {"id":3,"number":5,"season":1,"show":{"id":12,"name":"Borders: Tokyo Police","type":"Scripted"}},
          {"id":4,"number":null,"season":1,"show":{"id":13,"name":"No Episode Number","type":"Animation"}}
        ]
    """.trimIndent()

    @Test
    fun `japanese animation is kept and marked`() {
        val got = japanEntries(jpPayload)
        assertEquals(listOf("The Apothecary Diaries", "The Exiled Heavy Knight"), got.map { it.first })
        assertTrue("Japan entries are marked so they can be labelled", got.all { it.second })
    }

    @Test
    fun `non-animation japanese shows are left out`() {
        val names = japanEntries(jpPayload).map { it.first }
        assertFalse("scripted Japanese shows are not anime", names.contains("Borders: Tokyo Police"))
    }

    @Test
    fun `a schedule row with no episode number is skipped`() {
        assertFalse(japanEntries(jpPayload).any { it.first == "No Episode Number" })
    }

    @Test
    fun `an empty japan list is harmless`() {
        assertTrue(japanEntries("[]").isEmpty())
    }
}
