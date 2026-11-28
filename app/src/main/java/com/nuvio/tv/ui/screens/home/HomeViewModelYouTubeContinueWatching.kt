package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.youtube.YouTubeProgress
import com.nuvio.tv.youtube.YouTubeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * YouTube Fork: videos started on the YouTube page, for a separate Continue Watching row.
 *
 * These never enter Nuvio's own WatchProgress store: they stay out of history, Trakt and Next Up.
 * The row only exists while the switch is on, and Nuvio's master Continue Watching switch still
 * applies.
 */
internal fun HomeViewModel.observeYouTubeContinueWatching() {
    viewModelScope.launch {
        // Why the counters are part of the emitted value: combine{} of two StateFlows produces a
        // new value on every bump of either one, so anything derived from the counters alone
        // ("is the row on") repeats itself and distinctUntilChanged() drops it. Home then kept the
        // row it built at startup until the app was restarted, however much was watched after.
        // Carrying the counters in the value makes each bump distinct, so the row is rebuilt.
        combine(
            YouTubeSettings.homeRowChanges,
            YouTubeProgress.changes,
            layoutPreferenceDataStore.continueWatchingEnabled
        ) { settingsChanges, progressChanges, continueWatchingEnabled ->
            val playing = (settingsChanges to progressChanges)
            playing to (continueWatchingEnabled && YouTubeSettings.continueWatchingOnHome(appContext))
        }
            .distinctUntilChanged { old, new -> old == new }
            .map { (_, enabled) ->
                if (!enabled) return@map emptyList()
                YouTubeProgress.backfillMissingDetails(appContext)
                // fillFromHistory: finished videos are kept as filler, so the row has a useful
                // number of cards instead of only the one or two still part-way through.
                // withAccountHistory: videos watched before this device kept its own list come
                // from the account, in the same order OneTube's History tab shows them.
                YouTubeProgress.continueWatching(
                    appContext,
                    fillFromHistory = true,
                    withAccountHistory = true,
                ).map { entry ->
                    // A finished video has already been watched to the end: resume from the start
                    // instead of dropping the player at the last second, and stop the card claiming
                    // a resume point it can't honour.
                    val resumeAt = if (entry.isFinished) 0L else entry.positionMs
                    ContinueWatchingItem.InProgress(
                        progress = WatchProgress(
                            contentId = entry.contentId,
                            contentType = "youtube",
                            name = entry.title.ifBlank { appContext.getString(com.nuvio.tv.R.string.youtube_continue_watching_video) },
                            poster = entry.posterOrStill,
                            backdrop = null,
                            logo = null,
                            videoId = entry.contentId,
                            season = null,
                            episode = null,
                            episodeTitle = entry.channel,
                            position = resumeAt,
                            duration = entry.durationMs,
                            lastWatched = entry.lastWatched,
                            progressPercent = if (entry.isFinished) 0f else entry.percent.toFloat(),
                        )
                    )
                }
            }
            .flowOn(Dispatchers.IO)
            .collect { items ->
                _uiState.update { state ->
                    if (state.youtubeContinueWatchingItems == items) state
                    else state.copy(youtubeContinueWatchingItems = items)
                }
            }
    }
}
