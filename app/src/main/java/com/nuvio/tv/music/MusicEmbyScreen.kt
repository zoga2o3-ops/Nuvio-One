package com.nuvio.tv.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Browse an Emby or Jellyfin server's music: albums, then the tracks on one.
 *
 * Deliberately the same shape as the Navidrome screen — a grid of albums that opens into a track
 * list — because they do the same job. Playback goes through the ordinary Music queue, since each
 * track carries the server's stream URL the way a Navidrome track does.
 */
@Composable
internal fun MusicEmbyScreen(
    onClose: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    playbackSongId: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by MusicEmby.config.collectAsState()
    var albums by remember { mutableStateOf<List<EmbyAlbum>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var openAlbum by remember { mutableStateOf<EmbyAlbum?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var songsLoading by remember { mutableStateOf(false) }
    var songsError by remember { mutableStateOf<String?>(null) }
    var showSetup by remember { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        MusicEmby.init(context)
        if (!MusicEmby.config.value.configured) showSetup = true
        androidx.compose.runtime.withFrameNanos { }
        runCatching { closeFocus.requestFocus() }
    }

    LaunchedEffect(config.configured, reload) {
        if (!config.configured) return@LaunchedEffect
        loading = true
        error = null
        withContext(Dispatchers.IO) { MusicEmby.albums() }.fold(
            onSuccess = { albums = it },
            onFailure = { error = it.message ?: "Couldn't read the albums." },
        )
        loading = false
    }

    LaunchedEffect(openAlbum?.id) {
        val album = openAlbum ?: return@LaunchedEffect
        songsLoading = true
        songsError = null
        songs = emptyList()
        withContext(Dispatchers.IO) { MusicEmby.albumSongs(album.id) }.fold(
            onSuccess = { songs = it },
            onFailure = { songsError = it.message ?: "Couldn't read this album." },
        )
        songsLoading = false
    }

    androidx.activity.compose.BackHandler {
        when {
            openAlbum != null -> openAlbum = null
            showSetup && config.configured -> showSetup = false
            else -> onClose()
        }
    }

    Box(Modifier.fillMaxSize().background(MusicPalette.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MusicPalette.surfaceRaised)
                        .focusRequester(closeFocus)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = MusicPalette.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = openAlbum?.name ?: "Emby music",
                        color = MusicPalette.text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            openAlbum != null -> openAlbum?.artist.orEmpty()
                            config.configured ->
                                "Connected to ${config.server}" + if (config.isJellyfin) " (Jellyfin)" else " (Emby)"
                            else -> "Not connected"
                        },
                        color = MusicPalette.textMuted,
                        fontSize = 12.sp,
                    )
                }
                if (!config.configured) {
                    PillButton("Connect") { showSetup = true }
                } else if (openAlbum == null) {
                    PillButton("Disconnect") {
                        MusicEmby.disconnect(context)
                        albums = emptyList()
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                config.configured.not() && showSetup.not() -> Unit
                openAlbum != null -> AlbumTracks(
                    songs = songs,
                    loading = songsLoading,
                    error = songsError,
                    playbackSongId = playbackSongId,
                    onPlay = { index -> if (songs.isNotEmpty()) onPlay(songs, index) },
                )
                else -> AlbumGrid(
                    albums = albums,
                    loading = loading,
                    error = error,
                    onRetry = { reload++ },
                    onOpen = { openAlbum = it },
                )
            }
        }

        if (showSetup) {
            EmbyMusicSetupDialog(initial = config, onDismiss = { showSetup = false })
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<EmbyAlbum>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (EmbyAlbum) -> Unit,
) {
    when {
        loading && albums.isEmpty() -> CenteredMessage("Reading your albums…")
        error != null && albums.isEmpty() -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error, color = MusicPalette.error, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            PillButton("Try again", onClick = onRetry)
        }
        albums.isEmpty() -> CenteredMessage(
            "No music albums on this server. Check that the library is shared with your user."
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.id }) { album -> AlbumCard(album) { onOpen(album) } }
        }
    }
}

@Composable
private fun AlbumCard(album: EmbyAlbum, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(if (focused) MusicPalette.surfaceRaised else Color.Transparent)
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .padding(6.dp),
    ) {
        AsyncImage(
            model = MusicEmby.imageUrl(album),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MusicPalette.surfaceRaised),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            album.name,
            color = MusicPalette.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(album.artist, color = MusicPalette.textMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun AlbumTracks(
    songs: List<Song>,
    loading: Boolean,
    error: String?,
    playbackSongId: String?,
    onPlay: (Int) -> Unit,
) {
    when {
        loading -> CenteredMessage("Reading the tracks…")
        error != null -> CenteredMessage(error, isError = true)
        songs.isEmpty() -> CenteredMessage("This album has no tracks.")
        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(songs, key = { _, song -> song.videoId }) { index, song ->
                val playing = song.videoId == playbackSongId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(if (playing) MusicPalette.surfaceRaised else Color.Transparent)
                        .clickable { onPlay(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = if (playing) MusicPalette.accentTeal else MusicPalette.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        song.title,
                        color = if (playing) MusicPalette.accentTeal else MusicPalette.text,
                        fontSize = 13.sp,
                        fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(song.durationText.orEmpty(), color = MusicPalette.textMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (isError) MusicPalette.error else MusicPalette.textMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    androidx.tv.material3.Button(
        onClick = onClick,
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = MusicPalette.surfaceRaised,
            contentColor = MusicPalette.text,
        ),
    ) {
        Text(label, fontSize = 12.sp)
    }
}
