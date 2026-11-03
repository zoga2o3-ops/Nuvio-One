package com.nuvio.tv.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Works out which translated caption line is showing at a given moment in a YouTube video.
 *
 * The player accepts the translated caption track this app serves but never turns its segments into
 * cues, so nothing would be drawn. The lines are read from the same local server and handed to the
 * subtitle overlay by the player screen instead. Only YouTube uses this: Live TV, On Demand and
 * addon subtitles keep the normal path untouched.
 */
internal class YouTubeTranslatedCaptions(
    private val videoId: String,
    private val language: String
) {
    private var segmentNumber = -1L
    private var cues: List<Cue> = emptyList()

    private class Cue(val startMs: Long, val endMs: Long, val text: String)

    /** The line showing at [positionMs], or null when nothing is said then. */
    suspend fun lineAt(positionMs: Long): String? = withContext(Dispatchers.IO) {
        val number = positionMs / SEGMENT_MS
        if (number != segmentNumber) {
            segmentNumber = number
            cues = fetch(number)
        }
        val inside = positionMs - number * SEGMENT_MS
        cues.firstOrNull { inside >= it.startMs && inside < it.endMs }?.text
    }

    private fun fetch(number: Long): List<Cue> {
        val url = YouTubeMpdServer.segmentUrl(videoId, language, number) ?: return emptyList()
        val body = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        }.getOrNull() ?: return emptyList()
        return parse(body)
    }

    private fun parse(vtt: String): List<Cue> {
        val cues = ArrayList<Cue>()
        for (block in vtt.replace("\r\n", "\n").split(Regex("\n{2,}"))) {
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { "-->" in it }
            if (timingIndex < 0) continue
            val match = timing.find(lines[timingIndex]) ?: continue
            fun part(i: Int) = match.groupValues[i].toLongOrNull() ?: 0L
            val start = part(1) * 3_600_000 + part(2) * 60_000 + part(3) * 1_000 + part(4)
            val end = part(5) * 3_600_000 + part(6) * 60_000 + part(7) * 1_000 + part(8)
            val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
            if (text.isNotEmpty()) cues += Cue(start, end, text)
        }
        return cues
    }

    private companion object {
        const val SEGMENT_MS = YouTubeCaptionSegments.DURATION_SECONDS * 1_000
        /** Hours are optional in the timing line; minutes and seconds are always there. */
        val timing = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})[.,](\\d{3})\\s*-->\\s*(?:(\\d+):)?(\\d{1,2}):(\\d{2})[.,](\\d{3})")
        val http by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }
    }
}
