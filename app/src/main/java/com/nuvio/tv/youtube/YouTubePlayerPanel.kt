package com.nuvio.tv.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class PanelMode { MAIN, PLAYLISTS, COMMENTS, REPLIES }

private val PanelBg = Color(0xF2111114)
private val RowBg = Color(0xFF1C1C1E)
private val Accent = Color(0xFFFF0033)

/** One row in the panel's lists. */
private class PanelEntry(
    val title: String,
    val detail: String?,
    val titleLines: Int = 2,
    val onClick: () -> Unit = {}
)

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
    // Where you were in the comments, so coming back from a comment's replies lands on it.
    var commentsIndex by remember { mutableIntStateOf(0) }
    var replyingTo by remember { mutableStateOf<YtComment?>(null) }

    LaunchedEffect(videoId) { viewModel.load(videoId) }
    BackHandler(enabled = mode != PanelMode.MAIN) {
        mode = when (mode) {
            PanelMode.REPLIES -> {
                viewModel.clearReplies()
                PanelMode.COMMENTS
            }
            else -> PanelMode.MAIN
        }
    }

    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(440.dp).background(PanelBg)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val info = state.info

            // The heading for this level. The list below it is always there, so it always owns the remote.
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
                }
                PanelMode.PLAYLISTS -> Text("Save to playlist", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                PanelMode.COMMENTS -> Text("Comments", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                PanelMode.REPLIES -> {
                    Text("Replies", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    replyingTo?.let {
                        Text(
                            "${it.author}: ${it.text}",
                            color = Color.Gray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val busy = when (mode) {
                PanelMode.MAIN -> state.loading && info == null
                PanelMode.PLAYLISTS -> state.playlists == null
                PanelMode.COMMENTS -> state.commentsLoading && state.comments.isEmpty()
                PanelMode.REPLIES -> state.repliesLoading && state.replies.isEmpty()
            }
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(color = Accent) }
            }

            val entries: List<PanelEntry> = when (mode) {
                PanelMode.MAIN -> if (info == null) emptyList() else buildList {
                    add(PanelEntry(if (info.liked) "Liked   ✓" else "Like", info.likeCount) { viewModel.toggleLike() })
                    add(PanelEntry(if (info.disliked) "Disliked   ✓" else "Dislike", null) { viewModel.toggleDislike() })
                    add(PanelEntry(if (info.subscribed) "Subscribed   ✓" else "Subscribe", info.author.takeIf(String::isNotBlank)) {
                        viewModel.toggleSubscribe()
                    })
                    add(PanelEntry("Save to playlist", null) {
                        mode = PanelMode.PLAYLISTS
                        viewModel.loadPlaylists()
                    })
                    add(PanelEntry("Comments", null) {
                        commentsIndex = 0
                        mode = PanelMode.COMMENTS
                        viewModel.loadComments()
                    })
                    info.channelId?.let { channel ->
                        add(PanelEntry("Open channel", info.author.takeIf(String::isNotBlank)) { onOpenChannel(channel, info.author) })
                        add(PanelEntry("Hide this channel", "Keeps it off the YouTube page") { viewModel.hideChannel() })
                    }
                }
                PanelMode.PLAYLISTS -> state.playlists.orEmpty().map { row ->
                    PanelEntry(if (row.contains) "${row.title}   ✓" else row.title, null) { viewModel.togglePlaylist(row) }
                }
                PanelMode.COMMENTS -> state.comments.map { c ->
                    PanelEntry(
                        title = c.text,
                        detail = listOfNotNull(
                            c.author, c.published, c.likes?.let { "$it likes" },
                            c.replyCount?.let { if (c.replyKey == null) null else if (it.any { ch -> ch.isLetter() }) it else "$it replies" }
                        ).joinToString("  ·  "),
                        titleLines = 6,
                        onClick = {
                            // A comment with replies opens them; the list keeps its place for the way back.
                            val key = c.replyKey
                            if (key != null) {
                                replyingTo = c
                                mode = PanelMode.REPLIES
                                viewModel.loadReplies(key)
                            }
                        }
                    )
                }
                PanelMode.REPLIES -> state.replies.map { c ->
                    PanelEntry(
                        title = c.text,
                        detail = listOfNotNull(c.author, c.published, c.likes?.let { "$it likes" }).joinToString("  ·  "),
                        titleLines = 6
                    )
                }
            }

            if (!busy && entries.isEmpty()) {
                Text(
                    when (mode) {
                        PanelMode.MAIN -> "Couldn't load this video's details."
                        PanelMode.PLAYLISTS -> "You have no playlists."
                        PanelMode.COMMENTS -> "No comments to show."
                        PanelMode.REPLIES -> "No replies to show."
                    },
                    color = Color.Gray, fontSize = 14.sp
                )
            }

            // A fresh list for each level; the comments list starts where you left it.
            key(mode) {
                NavigableList(
                    entries = entries,
                    modifier = Modifier.weight(1f),
                    startAt = if (mode == PanelMode.COMMENTS) commentsIndex else 0,
                    onSelectedChange = { if (mode == PanelMode.COMMENTS) commentsIndex = it },
                    header = if (mode == PanelMode.COMMENTS) {
                        { focused -> SortBar(sort = state.commentsSort, focused = focused) }
                    } else null,
                    onHeaderStep = { delta ->
                        val next = (state.commentsSort + delta).coerceIn(0, 1)
                        if (next != state.commentsSort) {
                            commentsIndex = 0
                            viewModel.loadComments(newest = next == 1)
                        }
                    },
                    onNearEnd = {
                        when (mode) {
                            PanelMode.COMMENTS -> if (state.canLoadMoreComments) viewModel.loadMoreComments()
                            PanelMode.REPLIES -> if (state.canLoadMoreReplies) viewModel.loadMoreReplies()
                            else -> Unit
                        }
                    }
                )
            }

            state.message?.let {
                Text(it, color = Color(0xFFFFC107), fontSize = 13.sp)
            }
            if (mode == PanelMode.MAIN && !state.signedIn && info != null) {
                Text("Sign in on the YouTube page to like, subscribe or save.", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

/** The "Top / Newest" choice above the comments, like the one on the YouTube page. */
@Composable
private fun SortBar(sort: Int, focused: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Sort by", color = Color.Gray, fontSize = 13.sp)
        listOf("Top", "Newest").forEachIndexed { index, label ->
            Text(
                label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (index == sort) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (index == sort) Accent else RowBg)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        if (focused) Text("◀ ▶ to switch", color = Color.Gray, fontSize = 11.sp)
    }
}

/**
 * A list that handles the remote itself. Up and Down move a highlight and scroll it into view, OK presses
 * the highlighted row, and Left and Right do nothing. Focus never leaves the list, so scrolling a long list
 * can't drop you back onto the player's controls; Back is how you leave.
 *
 * With a [header], Up from the first row moves onto it; there, Left and Right call [onHeaderStep] and Down
 * goes back to the list.
 */
@Composable
private fun NavigableList(
    entries: List<PanelEntry>,
    modifier: Modifier = Modifier,
    startAt: Int = 0,
    onSelectedChange: (Int) -> Unit = {},
    header: (@Composable (focused: Boolean) -> Unit)? = null,
    onHeaderStep: (Int) -> Unit = {},
    onNearEnd: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(startAt.coerceAtLeast(0)) }
    var onHeader by remember { mutableStateOf(false) }

    // This list keeps the remote for as long as it's showing.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // A list that gets shorter can't leave the highlight past its end.
    LaunchedEffect(entries.size) {
        if (selected > entries.lastIndex) selected = entries.lastIndex.coerceAtLeast(0)
    }
    LaunchedEffect(selected) { onSelectedChange(selected) }
    // Bring the highlighted row into view, only when it isn't already fully visible.
    LaunchedEffect(selected, entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == selected }
        val fullyVisible = row != null && row.offset >= 0 && row.offset + row.size <= info.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(selected)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val handled = when (e.key) {
                    Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
                    else -> false
                }
                if (handled && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionDown -> {
                            if (onHeader) {
                                onHeader = false
                            } else {
                                if (selected < entries.lastIndex) selected += 1
                                if (selected >= entries.lastIndex - 2) onNearEnd()
                            }
                        }
                        Key.DirectionUp -> {
                            if (!onHeader) {
                                if (selected > 0) selected -= 1 else if (header != null) onHeader = true
                            }
                        }
                        Key.DirectionLeft -> if (onHeader) onHeaderStep(-1)
                        Key.DirectionRight -> if (onHeader) onHeaderStep(1)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                            if (!onHeader) entries.getOrNull(selected)?.onClick?.invoke()
                        else -> Unit
                    }
                }
                handled
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        header?.invoke(onHeader)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(entries) { index, entry ->
                val isSelected = index == selected && !onHeader
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF2A2A2E) else RowBg)
                        .border(2.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(entry.title, color = Color.White, fontSize = 15.sp, maxLines = entry.titleLines, overflow = TextOverflow.Ellipsis)
                    if (!entry.detail.isNullOrBlank()) {
                        Text(entry.detail, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
