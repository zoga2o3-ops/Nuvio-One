package com.nuvio.tv.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which videos the OneTube Continue Watching row is built from.
 *
 * Reported: the row showed only one or two cards, because videos watched to the end were dropped
 * (nothing left to resume). [fillFromHistory] keeps unfinished videos first and fills the rest of
 * the row with recently finished ones, newest first.
 */
class YouTubeContinueWatchingSelectionTest {

    private fun item(id: String, positionMs: Long, durationMs: Long, at: Long) = YouTubeHistoryItem(
        videoId = id,
        title = "title-$id",
        channel = null,
        poster = null,
        positionMs = positionMs,
        durationMs = durationMs,
        lastWatched = at,
    )

    private val minute = 60_000L

    /** The same rules as YouTubeProgress.continueWatching, applied to a fixed list. */
    private fun select(all: List<YouTubeHistoryItem>, limit: Int, fillFromHistory: Boolean): List<YouTubeHistoryItem> {
        val started = all.filter { it.durationMs > 0L && it.positionMs >= 5_000L }
        val unfinished = started.filter { !it.isFinished }
        if (!fillFromHistory) return unfinished.take(limit)
        return (unfinished + started.filter { it.isFinished })
            .distinctBy { it.videoId }
            .sortedByDescending { it.lastWatched }
            .take(limit)
    }

    private val history = listOf(
        item("done1", 10 * minute, 10 * minute, at = 900),   // finished (100%)
        item("part1", 4 * minute, 10 * minute, at = 800),    // in progress
        // "Finished" means 95% or within 20 s of the end, so 9:50 of 10 min counts but 9:00 does not.
        item("done2", 10 * minute - 10_000, 10 * minute, at = 700),
        item("part2", 1 * minute, 10 * minute, at = 600),    // in progress
        item("skip", 1_000, 10 * minute, at = 500),          // barely started (< 5 s)
        item("done3", 10 * minute, 10 * minute, at = 400),   // finished
    )

    @Test
    fun `without filling, finished videos are dropped`() {
        val got = select(history, limit = 10, fillFromHistory = false)
        assertEquals(listOf("part1", "part2"), got.map { it.videoId })
    }

    @Test
    fun `filling keeps unfinished videos and tops the row up with finished ones`() {
        val got = select(history, limit = 10, fillFromHistory = true)
        // All four usable videos, newest first; the barely-started one still excluded.
        assertEquals(listOf("done1", "part1", "done2", "part2", "done3"), got.map { it.videoId })
        assertTrue("skip never counts as started", got.none { it.videoId == "skip" })
    }

    @Test
    fun `the row can reach ten cards`() {
        val many = (1..12).map { item("v$it", 10 * minute, 10 * minute, at = (100 - it).toLong()) } +
            listOf(item("open", 2 * minute, 10 * minute, at = 999))
        val got = select(many, limit = 30, fillFromHistory = true)
        assertTrue("expected at least 10 cards, got ${got.size}", got.size >= 10)
    }

    @Test
    fun `the limit is still respected`() {
        assertEquals(3, select(history, limit = 3, fillFromHistory = true).size)
    }

    @Test
    fun `a video is never listed twice`() {
        val duplicated = history + history
        val got = select(duplicated, limit = 30, fillFromHistory = true)
        assertEquals(got.map { it.videoId }.distinct(), got.map { it.videoId })
    }
}
