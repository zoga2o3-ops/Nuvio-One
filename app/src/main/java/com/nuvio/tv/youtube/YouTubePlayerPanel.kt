package com.nuvio.tv.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class PanelMode { MAIN, PLAYLISTS, COMMENTS }

private val PanelBg = Color(0xF2111114)
private val RowBg = Color(0xFF1C1C1E)

/**
 * The YouTube panel over the player: what you can do about the video that's playing. Opened from the
 * YouTube button in the controls; Back goes up a level, then closes it.
 */
@Composable
fun BoxScope.YouTubePlayerPanel(
    videoId: String,
    viewModel: YouTubePlayerViewModel,
    onClose: () -> Unit,
    onOpenChannel: (channelId: String, name: String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var mode by remember { mutableStateOf(PanelMode.MAIN) }
    val first = remember { FocusRequester() }

    LaunchedEffect(videoId) { viewModel.load(videoId) }
    // The first row takes focus whenever the panel changes level.
    LaunchedEffect(mode, state.info != null, state.playlists != null, state.comments.size == 0) {
        runCatching { first.requestFocus() }
    }
    BackHandler(enabled = mode != PanelMode.MAIN) { mode = PanelMode.MAIN }

    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(440.dp).background(PanelBg)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val info = state.info
            when (mode) {
                PanelMode.MAIN -> {
                    Text(
                        info?.title ?: "YouTube",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    info?.let {
                        Text(
                            listOfNotNull(it.author.takeIf(String::isNotBlank), it.subscribers).joinToString("  ·  "),
                            color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.loading && info == null) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFFF0033)) }
                    }
                    if (info != null) {
                        PanelRow(
                            if (info.liked) "Liked   ✓" else "Like",
                            info.likeCount, Modifier.focusRequester(first)
                        ) { viewModel.toggleLike() }
                        PanelRow(if (info.disliked) "Disliked   ✓" else "Dislike", null, Modifier) { viewModel.toggleDislike() }
                        PanelRow(
                            if (info.subscribed) "Subscribed   ✓" else "Subscribe",
                            info.author.takeIf(String::isNotBlank), Modifier
                        ) { viewModel.toggleSubscribe() }
                        PanelRow("Save to playlist", null, Modifier) {
                            mode = PanelMode.PLAYLISTS
                            viewModel.loadPlaylists()
                        }
                        PanelRow("Comments", null, Modifier) {
                            mode = PanelMode.COMMENTS
                            viewModel.loadComments()
                        }
                        info.channelId?.let { channel ->
                            PanelRow("Open channel", info.author.takeIf(String::isNotBlank), Modifier) {
                                onOpenChannel(channel, info.author)
                            }
                        }
                    }
                }

                PanelMode.PLAYLISTS -> {
                    Text("Save to playlist", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val lists = state.playlists
                    when {
                        lists == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFFF0033))
                        }
                        lists.isEmpty() -> Text("You have no playlists.", color = Color.Gray, fontSize = 14.sp)
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lists.size) { i ->
                                val row = lists[i]
                                PanelRow(
                                    if (row.contains) "${row.title}   ✓" else row.title, null,
                                    if (i == 0) Modifier.focusRequester(first) else Modifier
                                ) { viewModel.togglePlaylist(row) }
                            }
                        }
                    }
                }

                PanelMode.COMMENTS -> {
                    Text("Comments", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (state.comments.isEmpty()) {
                        if (state.commentsLoading) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFFF0033)) }
                        } else {
                            // Keeps a row to land on, so Back and the remote still work on an empty list.
                            PanelRow("No comments to show", null, Modifier.focusRequester(first)) { mode = PanelMode.MAIN }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.comments.size) { i ->
                                val c = state.comments[i]
                                PanelRow(
                                    title = c.text,
                                    detail = listOfNotNull(c.author, c.published, c.likes?.let { "$it likes" }).joinToString("  ·  "),
                                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                                    titleLines = 5
                                ) {}
                            }
                            if (state.canLoadMoreComments) {
                                item {
                                    PanelRow(if (state.commentsLoading) "Loading…" else "Load more comments", null, Modifier) {
                                        viewModel.loadMoreComments()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.message?.let {
                Text(it, color = Color(0xFFFFC107), fontSize = 13.sp, modifier = Modifier.clickable { viewModel.clearMessage() })
            }
            if (mode == PanelMode.MAIN && !state.signedIn && info != null) {
                Text(
                    "Sign in on the YouTube page to like, subscribe or save.",
                    color = Color.Gray, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PanelRow(
    title: String,
    detail: String?,
    modifier: Modifier,
    titleLines: Int = 2,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RowBg)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
        if (!detail.isNullOrBlank()) Text(detail, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
