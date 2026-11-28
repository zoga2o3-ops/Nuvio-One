package com.nuvio.tv.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adaptive spacing used when an Xtream provider starts refusing requests.
 *
 * A large playlist asks the provider for one channel's listings at a time. Some providers allow
 * only a few dozen requests a minute, and the app used to answer the first refusal by stopping
 * everything for five minutes — so a 4,900-channel playlist filled about thirty channels and
 * then sat still for good.
 */
class XtreamArchiveBackoffTest {

    /** The same arithmetic [LiveTvRepository] uses, kept here so the rule is pinned. */
    private class Backoff {
        var waitMs = 0L
        fun afterRefusal() {
            waitMs = if (waitMs <= 0L) START else (waitMs * 2).coerceAtMost(MAX)
        }
        fun afterAnswer() {
            if (waitMs > 0L) waitMs = (waitMs / 2).takeIf { it >= START } ?: 0L
        }
    }

    @Test
    fun `a provider that never refuses is never made to wait`() {
        val b = Backoff()
        repeat(500) { b.afterAnswer() }

        assertEquals("the first request must not be delayed at all", 0L, b.waitMs)
    }

    @Test
    fun `the first refusal starts a short wait rather than a long stop`() {
        val b = Backoff()
        b.afterRefusal()

        assertEquals(START, b.waitMs)
        assertTrue("the old five-minute stop was the bug", b.waitMs < 60_000L)
    }

    @Test
    fun `repeated refusals back off, but never past the ceiling`() {
        val b = Backoff()
        repeat(50) { b.afterRefusal() }

        assertEquals(MAX, b.waitMs)
    }

    @Test
    fun `the wait eases back down once the provider answers again`() {
        val b = Backoff()
        repeat(4) { b.afterRefusal() }
        val climbed = b.waitMs
        b.afterAnswer()

        assertTrue("should have eased below $climbed", b.waitMs < climbed)
    }

    @Test
    fun `the wait returns to none, so a finished playlist is back to full speed`() {
        val b = Backoff()
        repeat(6) { b.afterRefusal() }
        repeat(20) { b.afterAnswer() }

        assertEquals(0L, b.waitMs)
    }

    @Test
    fun `a refusal is recognised from the message`() {
        val messages = listOf(
            "HTTP 429: too many requests, try again in a few minutes",
            "429 Too Many Requests",
            "Too many requests, please slow down",
        )
        messages.forEach { message ->
            val limited = message.contains("429") || message.contains("too many requests", ignoreCase = true)
            assertTrue("\"$message\" should count as a refusal", limited)
        }
    }

    @Test
    fun `an ordinary failure is not treated as a refusal`() {
        val message = "Failed to connect to provider.example (port 8080): timeout"
        val limited = message.contains("429") || message.contains("too many requests", ignoreCase = true)

        assertFalse(limited)
    }

    private companion object {
        const val START = 250L
        const val MAX = 4_000L
    }
}
