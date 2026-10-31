package com.nuvio.tv.youtube

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class YtKind { VIDEO, CHANNEL, PLAYLIST }

/** One card. Kept separate from MediaServiceCore's types so the UI never touches them. */
data class YtItem(
    val kind: YtKind,
    val videoId: String?,
    val title: String,
    val author: String?,
    val thumbnail: String?,
    val badge: String?,
    val isLive: Boolean,
    /** How much YouTube says you've watched (0-100), from your account. */
    val percentWatched: Int,
    internal val source: MediaItem
)

data class YtRow(val title: String, val items: List<YtItem>)

/** The feeds along the top. Grid feeds are one long list; the others are rows of videos. */
enum class YtTab(val title: String, val needsSignIn: Boolean = false, val grid: Boolean = false) {
    HOME("Home"),
    TRENDING("Trending"),
    SUBSCRIPTIONS("Subscriptions", needsSignIn = true, grid = true),
    CHANNELS("Channels", needsSignIn = true, grid = true),
    HISTORY("History", needsSignIn = true, grid = true),
    PLAYLISTS("Playlists", needsSignIn = true, grid = true),
    LIVE("Live"),
    MUSIC("Music"),
    GAMING("Gaming"),
    NEWS("News"),
    SPORTS("Sports")
}

data class YtUiState(
    val tab: YtTab = YtTab.HOME,
    val signedIn: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val rows: List<YtRow> = emptyList(),
    val grid: List<YtItem> = emptyList(),
    val canLoadMore: Boolean = false,
    /** Set while looking at a channel, playlist or search opened from a feed; Back returns to the feed. */
    val subTitle: String? = null
) {
    val isEmpty get() = rows.isEmpty() && grid.isEmpty()
}

/** What the player needs to start a video. */
data class YtPlayable(
    val url: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * The YouTube section's data. Everything here is shown only inside the YouTube page; nothing is
 * handed to Home, Continue Watching or the library.
 */
@HiltViewModel
class YouTubeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private companion object {
        const val TAG = "YouTubeVM"
        const val MAX_CLIENT_SWITCHES = 8
        const val PREFETCH_DELAY_MS = 250L
        const val MAX_CACHED = 24
    }

    private val _state = MutableStateFlow(YtUiState())
    val state: StateFlow<YtUiState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val service by lazy {
        // MediaServiceCore reads its prefs through this singleton, so it must exist first.
        GlobalPreferences.instance(context)
        YouTubeServiceManager.instance()
    }
    private val content: ContentService get() = service.contentService

    /** The page being shown: pages opened from it go on top, and Back takes them off. */
    private class Page(val state: YtUiState, val group: MediaGroup?)
    private val backStack = ArrayDeque<Page>()
    private var currentGroup: MediaGroup? = null
    private var loadJob: Job? = null

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    init { selectTab(YtTab.HOME) }

    // ---- feeds ----

    fun selectTab(tab: YtTab) {
        backStack.clear()
        _state.update { YtUiState(tab = tab, signedIn = it.signedIn, loading = true) }
        load(tab.title) {
            val signed = runCatching { service.signInService.isSigned }.getOrDefault(false)
            _state.update { it.copy(signedIn = signed) }
            if (tab.needsSignIn && !signed) return@load Feed.Nothing
            when (tab) {
                YtTab.HOME -> rows(content.homeObserve.blockingFirst())
                YtTab.TRENDING -> rows(content.trendingObserve.blockingFirst())
                YtTab.LIVE -> rows(content.liveObserve.blockingFirst())
                YtTab.MUSIC -> rows(content.musicObserve.blockingFirst())
                YtTab.GAMING -> rows(content.gamingObserve.blockingFirst())
                YtTab.NEWS -> rows(content.newsObserve.blockingFirst())
                YtTab.SPORTS -> rows(content.sportsObserve.blockingFirst())
                YtTab.SUBSCRIPTIONS -> grid(content.subscriptionsObserve.blockingFirst())
                YtTab.CHANNELS -> grid(content.subscribedChannelsObserve.blockingFirst())
                YtTab.HISTORY -> grid(content.historyObserve.blockingFirst())
                YtTab.PLAYLISTS -> grid(content.playlistsObserve.blockingFirst())
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        pushPage()
        _state.update {
            it.copy(loading = true, error = null, rows = emptyList(), grid = emptyList(), canLoadMore = false,
                subTitle = "Results for \"${query.trim()}\"")
        }
        load(query) { rows(content.getSearchObserve(query).blockingFirst()) }
    }

    /** Opens a channel or playlist on top of the current page. */
    fun open(item: YtItem) {
        pushPage()
        _state.update {
            it.copy(loading = true, error = null, rows = emptyList(), grid = emptyList(), canLoadMore = false,
                subTitle = item.title)
        }
        load(item.title) {
            when (item.kind) {
                YtKind.CHANNEL -> rows(content.getChannelObserve(item.source).blockingFirst())
                else -> grid(content.getGroupObserve(item.source).blockingFirst())
            }
        }
    }

    private fun pushPage() {
        // A page that's still loading has nothing to come back to.
        if (!_state.value.loading) backStack.addLast(Page(_state.value, currentGroup))
    }

    /** Takes the top page off. Returns false when there's nothing to go back to. */
    fun back(): Boolean {
        val page = backStack.removeLastOrNull() ?: return false
        loadJob?.cancel()
        currentGroup = page.group
        _state.value = page.state.copy(loading = false)
        return true
    }

    fun loadMore() {
        val group = currentGroup ?: return
        val s = _state.value
        if (s.loading || s.loadingMore || !s.canLoadMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { content.continueGroupObserve(group).blockingFirst() }.getOrNull()
            }
            val more = next?.let(::toItems).orEmpty()
            currentGroup = next ?: group
            _state.update {
                it.copy(
                    grid = it.grid + more,
                    loadingMore = false,
                    canLoadMore = next?.nextPageKey != null && more.isNotEmpty()
                )
            }
        }
    }

    private sealed interface Feed {
        data object Nothing : Feed
        class Rows(val rows: List<YtRow>) : Feed
        class Grid(val group: MediaGroup) : Feed
    }

    private fun rows(groups: List<MediaGroup>?): Feed =
        Feed.Rows(groups.orEmpty().map { YtRow(it.title.orEmpty(), toItems(it)) }.filter { it.items.isNotEmpty() })

    private fun grid(group: MediaGroup?): Feed = if (group == null) Feed.Nothing else Feed.Grid(group)

    private fun load(label: String, fetch: () -> Feed) {
        loadJob?.cancel()
        currentGroup = null
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetch() } }
            result.fold(
                onSuccess = { feed ->
                    when (feed) {
                        Feed.Nothing -> _state.update { it.copy(loading = false) }
                        is Feed.Rows -> {
                            _state.update { it.copy(loading = false, rows = feed.rows, grid = emptyList(), canLoadMore = false) }
                            // The first lookup after the app starts is slow (YouTube's player scripts
                            // load then), so do it now, while you're still choosing.
                            feed.rows.firstOrNull()?.items?.firstOrNull { it.kind == YtKind.VIDEO }?.let { lookup(it) }
                        }
                        is Feed.Grid -> {
                            currentGroup = feed.group
                            _state.update {
                                it.copy(
                                    loading = false,
                                    rows = emptyList(),
                                    grid = toItems(feed.group),
                                    canLoadMore = feed.group.nextPageKey != null
                                )
                            }
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "couldn't load $label", e)
                    _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load $label") }
                }
            )
        }
    }

    private fun toItems(g: MediaGroup): List<YtItem> = g.mediaItems.orEmpty().mapNotNull(::toItem)

    private fun toItem(i: MediaItem): YtItem? {
        val kind = when (i.type) {
            MediaItem.TYPE_CHANNEL -> YtKind.CHANNEL
            MediaItem.TYPE_PLAYLIST -> YtKind.PLAYLIST
            else -> YtKind.VIDEO
        }
        if (kind == YtKind.VIDEO && i.videoId == null) return null
        return YtItem(
            kind = kind,
            videoId = i.videoId,
            title = i.title.orEmpty(),
            author = i.author ?: i.secondTitle?.toString(),
            thumbnail = i.cardImageUrl,
            badge = i.badgeText,
            isLive = i.isLive,
            percentWatched = i.percentWatched.coerceIn(0, 100),
            source = i
        )
    }

    // ---- playback ----

    private val resolved = java.util.concurrent.ConcurrentHashMap<String, YtPlayable>()
    private var prefetchJob: Job? = null
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, Deferred<Result<YtPlayable>>>()

    /** Looks up the stream details for [video]; slow (several requests), so call off the main thread. */
    private fun resolveBlocking(video: YtItem): YtPlayable {
        val videoId = video.videoId ?: error("Not a video")
        val startedAt = android.os.SystemClock.elapsedRealtime()
        fun since() = android.os.SystemClock.elapsedRealtime() - startedAt
        var info = service.mediaItemService.getFormatInfoObserve(videoId).blockingFirst()
        Log.i(TAG, "first lookup $videoId client=${info.clientInfo?.clientName} at ${since()}ms")
        // YouTube answers some client types with SABR only (no plain stream URLs), which the stock
        // player can't read. Step through the engine's other clients until one returns streams we can play.
        var attempts = 0
        while (!playable(info) && attempts++ < MAX_CLIENT_SWITCHES) {
            Log.i(TAG, "client ${info.clientInfo?.clientName} gave no plain streams " +
                "(sabr=${info.containsSabrFormats()} unplayable=${info.isUnplayable}); switching")
            service.switchNextClientNow()
            info = service.mediaItemService.getFormatInfoObserve(videoId).blockingFirst()
            Log.i(TAG, "lookup with client=${info.clientInfo?.clientName} at ${since()}ms")
        }
        if (info.isUnplayable) error(info.playabilityReason ?: "This video can't be played")
        Log.i(TAG, "formats video=$videoId dash=${info.containsDashFormats()} " +
            "sabr=${info.containsSabrFormats()} hls=${info.containsHlsUrl()} " +
            "adaptive=${info.adaptiveFormats?.size} live=${info.isLive} client=${info.clientInfo?.clientName}")
        // Live streams come as HLS. Everything else is separate video and audio streams, which we
        // describe in a DASH manifest so the player can offer every quality. A single combined
        // stream (one low quality) is the last resort.
        val url = info.hlsManifestUrl?.takeIf { info.isLive }
            ?: dashUrl(videoId, info)
            ?: info.hlsManifestUrl
            ?: info.dashManifestUrl
            ?: info.urlFormats?.firstOrNull()?.url
            ?: error("No playable stream found")
        Log.i(TAG, "ready $videoId after ${since()}ms")
        // Stream URLs are tied to the client that asked for them, so the player has to present
        // the same user-agent.
        val headers = info.clientInfo?.userAgent?.takeIf { it.isNotBlank() }
            ?.let { mapOf("User-Agent" to it) }.orEmpty()
        return YtPlayable(url, video.title, video.thumbnail, videoId, headers)
    }

    /** One lookup per video at a time: hovering a card and then pressing OK share the same request. */
    private fun lookup(video: YtItem): Deferred<Result<YtPlayable>> {
        val id = video.videoId ?: return CompletableDeferred(Result.failure(IllegalStateException("Not a video")))
        resolved[id]?.let { return CompletableDeferred(Result.success(it)) }
        return inflight.getOrPut(id) {
            viewModelScope.async(Dispatchers.IO) {
                runCatching { resolveBlocking(video) }.also { r ->
                    r.getOrNull()?.let {
                        if (resolved.size >= MAX_CACHED) resolved.clear()
                        resolved[id] = it
                    }
                    inflight.remove(id)
                }
            }
        }
    }

    /**
     * Starts looking up [item]'s stream while its card is highlighted, so pressing OK is instant.
     * Waits a moment first so scrolling past cards doesn't fire a request for each one.
     */
    fun prefetch(item: YtItem) {
        val id = item.videoId ?: return
        if (item.kind != YtKind.VIDEO || resolved.containsKey(id)) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(PREFETCH_DELAY_MS)
            lookup(item)
        }
    }

    /** Plays a video, or opens a channel or playlist. [onReady] runs on the main thread. */
    fun activate(item: YtItem, onReady: (YtPlayable) -> Unit) {
        if (item.kind != YtKind.VIDEO) {
            open(item)
            return
        }
        prefetchJob?.cancel()
        viewModelScope.launch {
            lookup(item).await().fold(
                onSuccess = onReady,
                onFailure = { _error.value = it.message ?: "Couldn't play this video" }
            )
        }
    }

    private fun playable(info: MediaItemFormatInfo): Boolean =
        !info.isUnplayable && (info.containsDashFormats() || (info.isLive && info.containsHlsUrl()))

    private fun dashUrl(videoId: String, info: MediaItemFormatInfo): String? {
        if (!info.containsDashFormats()) return null
        val mpd = runCatching { info.createMpdStream()?.use { it.readBytes() } }.getOrNull()
        Log.i(TAG, "mpd bytes=${mpd?.size}")
        return mpd?.takeIf { it.isNotEmpty() }?.let { YouTubeMpdServer.publish(videoId, it) }
    }

    fun clearError() { _error.value = null }
}
