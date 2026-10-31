package com.nuvio.tv.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import java.util.concurrent.Executors

/**
 * Skips the parts of a YouTube video that SponsorBlock's community has marked (sponsors, intros and
 * so on). One per playback: it fetches the video's segments once, then [skipTarget] is asked where
 * playback is and answers where to jump to, if the position is inside a segment you've chosen to skip.
 */
class SponsorBlockSession(context: Context, private val videoId: String) {
    private companion object {
        const val TAG = "SponsorBlock"
        /** Don't jump for a sliver at the very end of a segment. */
        const val MIN_REMAINING_MS = 400L
        val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "yt-sponsorblock").apply { isDaemon = true } }
    }

    private val app = context.applicationContext
    private val enabled = YouTubeSettings.sponsorBlockEnabled(app)

    @Volatile private var segments: List<SponsorSegment> = emptyList()
    private val skipped = HashSet<Long>()

    init {
        if (enabled) {
            val categories = YouTubeSettings.sponsorCategoriesOn(app)
            if (categories.isNotEmpty()) {
                worker.execute {
                    runCatching {
                        GlobalPreferences.instance(app)
                        val found = YouTubeServiceManager.instance().mediaItemService
                            .getSponsorSegments(videoId, categories).orEmpty()
                            .filter { it.action == null || it.action == SponsorSegment.ACTION_SKIP }
                            .sortedBy { it.startMs }
                        segments = found
                        Log.i(TAG, "$videoId: ${found.size} segment(s)")
                    }.onFailure { Log.w(TAG, "couldn't load segments for $videoId", it) }
                }
            }
        }
    }

    /**
     * Where to jump to when playback is at [positionMs], or null to carry on. Each segment is skipped
     * once, so rewinding into one to watch it isn't fought.
     */
    fun skipTarget(positionMs: Long): Long? {
        val list = segments
        if (list.isEmpty()) return null
        val hit = list.firstOrNull {
            positionMs >= it.startMs && positionMs < it.endMs - MIN_REMAINING_MS && it.startMs !in skipped
        } ?: return null
        skipped += hit.startMs
        val label = YouTubeSettings.sponsorCategories.firstOrNull { it.first == hit.category }?.second ?: "segment"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, "Skipped: $label", Toast.LENGTH_SHORT).show()
        }
        return hit.endMs
    }
}
