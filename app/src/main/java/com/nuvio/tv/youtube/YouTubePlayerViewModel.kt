package com.nuvio.tv.youtube

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class YtVideoInfo(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String?,
    val subscribers: String?,
    val likeCount: String?,
    val liked: Boolean,
    val disliked: Boolean,
    val subscribed: Boolean,
    val commentsKey: String?
)

data class YtPlaylistRow(val id: String, val title: String, val contains: Boolean)

data class YtComment(val author: String, val text: String, val likes: String?, val published: String?)

data class YtPlayerState(
    val info: YtVideoInfo? = null,
    val loading: Boolean = false,
    val signedIn: Boolean = false,
    /** A one-line result or problem to show in the panel. */
    val message: String? = null,
    val playlists: List<YtPlaylistRow>? = null,
    val comments: List<YtComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val canLoadMoreComments: Boolean = false
)

/**
 * What you can do about the YouTube video that's playing: like it, subscribe to its channel, save it to a
 * playlist, read its comments. One per player screen; [load] picks up the video it's about.
 */
@HiltViewModel
class YouTubePlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private companion object { const val TAG = "YouTubePlayerVM" }

    private val _state = MutableStateFlow(YtPlayerState())
    val state: StateFlow<YtPlayerState> = _state.asStateFlow()

    private val service by lazy {
        GlobalPreferences.instance(context)
        YouTubeServiceManager.instance()
    }
    private val media get() = service.mediaItemService

    private var videoId: String? = null
    private var nextCommentsKey: String? = null

    fun load(id: String) {
        if (videoId == id) return
        videoId = id
        nextCommentsKey = null
        _state.value = YtPlayerState(loading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val signed = service.signInService.isSigned
                    media.getMetadataObserve(id).blockingFirst() to signed
                }
            }
            result.fold(
                onSuccess = { (meta, signed) -> _state.update { it.copy(info = toInfo(id, meta), loading = false, signedIn = signed) } },
                onFailure = {
                    Log.e(TAG, "couldn't load details for $id", it)
                    _state.update { s -> s.copy(loading = false, message = "Couldn't load this video's details.") }
                }
            )
        }
    }

    private fun toInfo(id: String, m: MediaItemMetadata) = YtVideoInfo(
        videoId = id,
        title = m.title.orEmpty(),
        author = m.author.orEmpty(),
        channelId = m.channelId,
        subscribers = m.subscriberCount,
        likeCount = m.likeCount,
        liked = m.likeStatus == MediaItemMetadata.LIKE_STATUS_LIKE,
        disliked = m.likeStatus == MediaItemMetadata.LIKE_STATUS_DISLIKE,
        subscribed = m.isSubscribed,
        commentsKey = m.commentsKey
    )

    private fun item(info: YtVideoInfo) = YouTubeMediaItem.fromIds(info.videoId, info.channelId)

    /** Runs a change on YouTube, then [apply]s its effect to what's shown. Signed-out users are told why not. */
    private fun act(okMessage: String, call: (YtVideoInfo) -> Observable<Void>, apply: (YtVideoInfo) -> YtVideoInfo) {
        val info = _state.value.info ?: return
        if (!_state.value.signedIn) {
            _state.update { it.copy(message = "Sign in to YouTube first, with the account button on the YouTube page.") }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { call(info).ignoreElements().blockingAwait() } }
            result.fold(
                onSuccess = { _state.update { s -> s.copy(info = s.info?.let(apply), message = okMessage) } },
                onFailure = {
                    Log.e(TAG, "action failed", it)
                    _state.update { s -> s.copy(message = "That didn't go through. Try again.") }
                }
            )
        }
    }

    fun toggleLike() {
        val liked = _state.value.info?.liked == true
        act(if (liked) "Like removed" else "Liked",
            { if (liked) media.removeLikeObserve(item(it)) else media.setLikeObserve(item(it)) },
            { it.copy(liked = !liked, disliked = false) })
    }

    fun toggleDislike() {
        val disliked = _state.value.info?.disliked == true
        act(if (disliked) "Dislike removed" else "Disliked",
            { if (disliked) media.removeDislikeObserve(item(it)) else media.setDislikeObserve(item(it)) },
            { it.copy(disliked = !disliked, liked = false) })
    }

    fun toggleSubscribe() {
        val subscribed = _state.value.info?.subscribed == true
        val channel = _state.value.info?.channelId
        if (channel == null) {
            _state.update { it.copy(message = "This video has no channel to subscribe to.") }
            return
        }
        act(if (subscribed) "Unsubscribed" else "Subscribed",
            { if (subscribed) media.unsubscribeObserve(channel) else media.subscribeObserve(channel) },
            { it.copy(subscribed = !subscribed) })
    }

    // ---- playlists ----

    fun loadPlaylists() {
        val info = _state.value.info ?: return
        if (!_state.value.signedIn) {
            _state.update { it.copy(message = "Sign in to YouTube first, with the account button on the YouTube page.") }
            return
        }
        _state.update { it.copy(playlists = null, loading = true, message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { media.getPlaylistsInfoObserve(info.videoId).blockingFirst() } }
            _state.update { s ->
                s.copy(
                    loading = false,
                    playlists = result.getOrNull().orEmpty().map { YtPlaylistRow(it.playlistId.orEmpty(), it.title.orEmpty(), it.isSelected) },
                    message = if (result.isFailure) "Couldn't load your playlists." else null
                )
            }
        }
    }

    fun togglePlaylist(row: YtPlaylistRow) {
        val info = _state.value.info ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val call = if (row.contains) media.removeFromPlaylistObserve(row.id, info.videoId)
                    else media.addToPlaylistObserve(row.id, info.videoId)
                    call.ignoreElements().blockingAwait()
                }
            }
            _state.update { s ->
                if (result.isSuccess) {
                    s.copy(
                        playlists = s.playlists?.map { if (it.id == row.id) it.copy(contains = !row.contains) else it },
                        message = if (row.contains) "Removed from ${row.title}" else "Saved to ${row.title}"
                    )
                } else s.copy(message = "That didn't go through. Try again.")
            }
        }
    }

    // ---- comments ----

    fun loadComments() {
        val key = _state.value.info?.commentsKey
        if (key.isNullOrBlank()) {
            _state.update { it.copy(message = "Comments are off for this video.") }
            return
        }
        if (_state.value.comments.isNotEmpty()) return
        fetchComments(key)
    }

    fun loadMoreComments() {
        val key = nextCommentsKey ?: return
        fetchComments(key)
    }

    private fun fetchComments(key: String) {
        if (_state.value.commentsLoading) return
        _state.update { it.copy(commentsLoading = true, message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { service.commentsService.getCommentsObserve(key).blockingFirst() } }
            val group = result.getOrNull()
            nextCommentsKey = group?.nextCommentsKey
            _state.update { s ->
                s.copy(
                    commentsLoading = false,
                    comments = s.comments + group?.comments.orEmpty().filter { !it.isEmpty }.map {
                        YtComment(it.authorName.orEmpty(), it.message.orEmpty(), it.likeCount, it.publishedDate)
                    },
                    canLoadMoreComments = group?.nextCommentsKey != null,
                    message = if (result.isFailure) "Couldn't load comments." else null
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
