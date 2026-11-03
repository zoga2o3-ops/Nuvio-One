package com.nuvio.tv.youtube

import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nuvio.tv.R
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun YouTubeThumbnailPreview(item: YtItem, viewModel: YouTubeViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    var playable by remember(item.videoId) { mutableStateOf<YtPlayable?>(null) }
    LaunchedEffect(item.videoId, resumed) {
        playable = null
        if (resumed) {
            delay(1500)
            playable = viewModel.previewVideo(item)
        }
    }
    val video = playable ?: return
    if (!resumed) return
    var ready by remember(video.url) { mutableStateOf(false) }
    val player = remember(video.url) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(video.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(video.url))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f),
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.trailer_player_view, null) as PlayerView).apply {
                useController = false
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null }
    )
}
