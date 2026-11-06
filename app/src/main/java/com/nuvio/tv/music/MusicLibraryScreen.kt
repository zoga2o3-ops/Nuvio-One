package com.nuvio.tv.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.music.bitchord.data.model.Song

@Composable
internal fun MusicLibraryScreen(
    liked: List<Song>,
    recents: List<Song>,
    playlists: List<StoredPlaylist>,
    accountName: String?,
    accountLiked: List<Song>,
    accountPlaylists: List<MusicAccountPlaylist>,
    accountLoading: Boolean,
    onSignIn: () -> Unit,
    onManageAccount: () -> Unit,
    onPlayAccountLiked: (Int) -> Unit,
    onOpenAccountPlaylist: (MusicAccountPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlayLiked: (Int) -> Unit,
    onPlayRecents: (Int) -> Unit,
    onOpenPlaylist: (StoredPlaylist) -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playback by MusicPlayer.state.collectAsState()
    val downloads by MusicDownloads.state.collectAsState()
    val newPlaylistFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { newPlaylistFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "library-header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Library",
                        color = MusicPalette.text,
                        fontFamily = MusicDisplay,
                        fontSize = 40.sp,
                        lineHeight = 43.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "Your likes, playlists and listening history.",
                        color = MusicPalette.textSecondary,
                        fontSize = 14.sp
                    )
                }
                LibraryActionButton(
                    icon = Icons.Default.Add,
                    label = "New playlist",
                    modifier = Modifier.focusRequester(newPlaylistFocus),
                    onClick = onCreatePlaylist
                )
            }
        }

        item(key = "downloads-entry") {
            DownloadsLibraryRow(
                count = downloads.completed.size,
                bytes = downloads.totalBytes,
                active = downloads.active.size + downloads.starting.size,
                onClick = onOpenDownloads,
                modifier = Modifier.padding(horizontal = 26.dp)
            )
        }

        if (accountName == null) {
            item(key = "account-signin") {
                AccountSignInCard(onSignIn)
            }
        } else {
            item(key = "account-header") {
                AccountHeader(accountName, onManageAccount)
            }
            if (accountLoading && accountLiked.isEmpty() && accountPlaylists.isEmpty()) {
                item(key = "account-loading") {
                    LibraryHint(
                        icon = Icons.Default.AccountCircle,
                        text = "Loading your music library…"
                    )
                }
            } else {
                item(key = "account-liked-header") {
                    LibrarySectionHeader(
                        icon = Icons.Default.Favorite,
                        title = "Liked Songs",
                        count = accountLiked.size,
                        onPlayAll = if (accountLiked.isNotEmpty()) {
                            { onPlayAccountLiked(0) }
                        } else {
                            null
                        }
                    )
                }
                if (accountLiked.isEmpty()) {
                    item(key = "account-liked-empty") {
                        LibraryHint(
                            icon = Icons.Default.Favorite,
                            text = "Songs you like will appear here."
                        )
                    }
                } else {
                    itemsIndexed(
                        items = accountLiked,
                        key = { index, song -> "account-liked:${song.videoId}#$index" }
                    ) { index, song ->
                        MusicTrackRow(
                            song = song,
                            index = index,
                            isCurrent = playback.song?.videoId == song.videoId,
                            isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                            onClick = { onPlayAccountLiked(index) },
                            modifier = Modifier.padding(horizontal = 26.dp)
                        )
                    }
                }
                item(key = "account-playlists-header") {
                    LibrarySectionHeader(
                        icon = Icons.Default.PlaylistPlay,
                        title = "Playlists",
                        count = accountPlaylists.size
                    )
                }
                if (accountPlaylists.isEmpty()) {
                    item(key = "account-playlists-empty") {
                        LibraryHint(
                            icon = Icons.Default.PlaylistPlay,
                            text = "Playlists from your music account will appear here."
                        )
                    }
                } else {
                    item(key = "account-playlists-grid") {
                        AccountPlaylistGrid(
                            playlists = accountPlaylists,
                            onOpen = onOpenAccountPlaylist
                        )
                    }
                }
            }
        }

        item(key = "liked-header") {
            LibrarySectionHeader(
                icon = Icons.Default.Favorite,
                title = if (accountName != null) "Liked on this device" else "Liked Songs",
                count = liked.size,
                onPlayAll = if (liked.isNotEmpty()) {
                    { onPlayLiked(0) }
                } else {
                    null
                }
            )
        }
        if (liked.isEmpty()) {
            item(key = "liked-empty") {
                LibraryHint(
                    icon = Icons.Default.Favorite,
                    text = "Songs you like will appear here. Use the heart on Now Playing."
                )
            }
        } else {
            itemsIndexed(
                items = liked,
                key = { index, song -> "liked:${song.videoId}#$index" }
            ) { index, song ->
                MusicTrackRow(
                    song = song,
                    index = index,
                    isCurrent = playback.song?.videoId == song.videoId,
                    isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                    onClick = { onPlayLiked(index) },
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }

        item(key = "playlists-header") {
            LibrarySectionHeader(
                icon = Icons.Default.PlaylistPlay,
                title = if (accountName != null) "Playlists on this device" else "Playlists",
                count = playlists.size
            )
        }
        if (playlists.isEmpty()) {
            item(key = "playlists-empty") {
                LibraryHint(
                    icon = Icons.Default.PlaylistPlay,
                    text = "Create a playlist, then add songs from Now Playing."
                )
            }
        } else {
            item(key = "playlists-grid") {
                LocalPlaylistGrid(
                    playlists = playlists,
                    onOpen = onOpenPlaylist
                )
            }
        }

        if (recents.isNotEmpty()) {
            item(key = "recents-header") {
                LibrarySectionHeader(
                    icon = Icons.Default.Schedule,
                    title = "Recently played",
                    count = recents.size
                )
            }
            itemsIndexed(
                items = recents.take(20),
                key = { index, song -> "recent:${song.videoId}#$index" }
            ) { index, song ->
                MusicTrackRow(
                    song = song,
                    index = index,
                    isCurrent = playback.song?.videoId == song.videoId,
                    isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                    onClick = { onPlayRecents(index) },
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }
    }
}

@Composable
private fun LibrarySectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
    onPlayAll: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MusicPalette.accent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = MusicPalette.text,
            fontFamily = MusicDisplay,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            color = MusicPalette.textMuted,
            fontSize = 11.5.sp
        )
        Spacer(Modifier.weight(1f))
        if (onPlayAll != null) {
            LibraryActionButton(
                icon = Icons.Default.PlayArrow,
                label = "Play all",
                onClick = onPlayAll
            )
        }
    }
}

@Composable
private fun LibraryHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MusicPalette.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MusicPalette.textMuted,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            color = MusicPalette.textSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun AccountSignInCard(onSignIn: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MusicPalette.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MusicPalette.accent,
            modifier = Modifier.size(30.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "Music account",
                color = MusicPalette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Sign in to see your liked songs and playlists.",
                color = MusicPalette.textSecondary,
                fontSize = 13.sp
            )
        }
        LibraryActionButton(
            icon = Icons.Default.AccountCircle,
            label = "Sign in",
            onClick = onSignIn
        )
    }
}

@Composable
private fun AccountHeader(accountName: String, onManage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MusicPalette.accent,
            modifier = Modifier.size(26.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "Music account",
                color = MusicPalette.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = accountName,
                color = MusicPalette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LibraryActionButton(
            icon = Icons.Default.Person,
            label = "Manage",
            onClick = onManage
        )
    }
}

@Composable
private fun AccountPlaylistGrid(
    playlists: List<MusicAccountPlaylist>,
    onOpen: (MusicAccountPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        playlists.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { playlist ->
                    AccountPlaylistTile(
                        playlist = playlist,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(playlist) }
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AccountPlaylistTile(
    playlist: MusicAccountPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(140),
        label = "accountPlaylistScale"
    )
    val shape = RoundedCornerShape(12.dp)
    val subtitle = playlist.count?.let {
        "$it track" + if (it == 1) "" else "s"
    } ?: "Music playlist"
    Row(
        modifier
            .height(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MusicArtwork(
            url = playlist.thumbnailUrl,
            modifier = Modifier.size(48.dp),
            cornerRadius = 9.dp,
            iconSize = 22.dp
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = MusicPalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DownloadsLibraryRow(
    count: Int,
    bytes: Long,
    active: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) MusicPalette.surfaceFocused else MusicPalette.surface
            )
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MusicPalette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MusicPalette.accent,
                modifier = Modifier.size(23.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "Downloads",
                color = MusicPalette.text,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    active > 0 -> "$active downloading · $count on this device"
                    count > 0 -> "$count track" + (if (count == 1) "" else "s") +
                        " · ${formatDownloadBytes(bytes)} · plays offline"
                    else -> "Save albums and playlists for offline listening"
                },
                color = if (count > 0) MusicPalette.accentTeal else MusicPalette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open downloads",
            tint = if (focused) MusicPalette.text else MusicPalette.textMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun LibraryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else MusicPalette.surfaceRaised)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (focused) MusicPalette.background else MusicPalette.text,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (focused) MusicPalette.background else MusicPalette.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LocalPlaylistGrid(
    playlists: List<StoredPlaylist>,
    onOpen: (StoredPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        playlists.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { playlist ->
                    LocalPlaylistTile(
                        playlist = playlist,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(playlist) }
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LocalPlaylistTile(
    playlist: StoredPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(140),
        label = "localPlaylistScale"
    )
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MusicArtwork(
            url = playlist.songs.firstOrNull()?.thumbnailUrl,
            modifier = Modifier.size(48.dp),
            cornerRadius = 9.dp,
            iconSize = 22.dp
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = MusicPalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${playlist.songs.size} track" +
                    if (playlist.songs.size == 1) "" else "s",
                color = MusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun MusicLocalPlaylistScreen(
    playlist: StoredPlaylist,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playback by MusicPlayer.state.collectAsState()
    val songs = remember(playlist.songs) { playlist.songs.map { it.toSong() } }
    val firstFocus = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }

    LaunchedEffect(playlist.id) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "local-playlist-header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (backFocused) Color.White else MusicPalette.surfaceRaised)
                            .border(
                                2.dp,
                                if (backFocused) Color.White else MusicPalette.border,
                                RoundedCornerShape(50)
                            )
                            .onFocusChanged { backFocused = it.isFocused }
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (backFocused) MusicPalette.background else MusicPalette.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = "PLAYLIST",
                        color = MusicPalette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = playlist.name,
                    color = MusicPalette.text,
                    fontFamily = MusicDisplay,
                    fontSize = 40.sp,
                    lineHeight = 45.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${songs.size} track" + if (songs.size == 1) "" else "s",
                    color = MusicPalette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Play",
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = { if (songs.isNotEmpty()) onPlay(0) }
                    )
                    LibraryActionButton(
                        icon = Icons.Default.Delete,
                        label = "Delete playlist",
                        onClick = onDelete
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (songs.isEmpty()) {
            item(key = "local-playlist-empty") {
                MusicEmptyState(
                    title = "No songs yet",
                    message = "Add songs from Now Playing → Playlist.",
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                )
            }
        } else {
            itemsIndexed(
                items = songs,
                key = { index, song -> "${song.videoId}#$index" }
            ) { index, song ->
                MusicTrackRow(
                    song = song,
                    index = index,
                    isCurrent = playback.song?.videoId == song.videoId,
                    isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                    onClick = { onPlay(index) },
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }
    }
}
