package com.nuvio.tv.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

private val YtRed = Color(0xFFFF0033)
private val CardBg = Color(0xFF1C1C1E)

/**
 * The YouTube page: its own section in the side menu, laid out like the YouTube app: feeds along
 * the top, then rows or a grid. Videos play in Nuvio's own player via [onPlay]; nothing here feeds
 * Home or Continue Watching.
 */
@Composable
fun YouTubeScreen(
    onPlay: (YtPlayable) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: YouTubeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    var query by remember { mutableStateOf("") }

    // Back goes up from a channel, playlist or search before it leaves the page.
    BackHandler(enabled = state.subTitle != null) { viewModel.back() }

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                state.subTitle ?: "YouTube",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(360.dp)
            )
            var focused by remember { mutableStateOf(false) }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search YouTube", color = Color.Gray, fontSize = 18.sp)
                    inner()
                }
            )
        }

        LazyRow(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(YtTab.entries) { tab ->
                TabChip(tab.title, selected = state.tab == tab && state.subTitle == null) {
                    query = ""
                    viewModel.selectTab(tab)
                }
            }
        }

        error?.let {
            Text(it, color = YtRed, modifier = Modifier.padding(bottom = 8.dp).clickable { viewModel.clearError() })
        }

        Content(state, viewModel, onPlay, onOpenSettings)
    }
}

@Composable
private fun Content(
    state: YtUiState,
    viewModel: YouTubeViewModel,
    onPlay: (YtPlayable) -> Unit,
    onOpenSettings: () -> Unit
) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = YtRed) }

        state.tab.needsSignIn && !state.signedIn && state.subTitle == null -> Message(
            "Sign in to see your ${state.tab.title.lowercase()}",
            "Open Settings, then YouTube, and sign in with the code.",
            "Open YouTube settings",
            onOpenSettings
        )

        state.error != null && state.isEmpty -> Message(
            state.error,
            "Check your connection and try again.",
            "Try again"
        ) { viewModel.selectTab(state.tab) }

        state.isEmpty -> Message("Nothing here yet", null, null) {}

        state.rows.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(state.rows) { row ->
                Column {
                    if (row.title.isNotBlank()) {
                        Text(row.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(row.items) { v ->
                            ItemCard(v, Modifier.width(260.dp), onFocused = { viewModel.prefetch(v) }) {
                                viewModel.activate(v, onPlay)
                            }
                        }
                    }
                }
            }
        }

        else -> {
            val gridState = rememberLazyGridState()
            // Keep loading as you near the end, like an endless feed.
            LaunchedEffect(gridState, state.canLoadMore) {
                snapshotFlow {
                    val info = gridState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
                }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(240.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(state.grid) { v ->
                    ItemCard(v, Modifier, onFocused = { viewModel.prefetch(v) }) { viewModel.activate(v, onPlay) }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, detail: String?, action: String?, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            detail?.let { Text(it, color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center) }
            if (action != null) TabChip(action, selected = true, onClick = onAction)
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) YtRed else CardBg)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

@Composable
private fun ItemCard(item: YtItem, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // What this device has played, or what your account says, whichever is further along.
    val watched = remember(item.videoId) {
        maxOf(item.percentWatched, YouTubeProgress.localPercent(context, item.videoId))
    }
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .background(CardBg)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (watched > 0 && item.kind == YtKind.VIDEO && !item.isLive) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x99000000))
                ) {
                    Box(Modifier.fillMaxWidth(if (watched >= 95) 1f else watched / 100f).height(4.dp).background(YtRed))
                }
            }
            val badge = when {
                item.isLive -> "LIVE"
                item.kind == YtKind.CHANNEL -> "CHANNEL"
                item.kind == YtKind.PLAYLIST -> "PLAYLIST"
                else -> item.badge
            }
            if (!badge.isNullOrBlank()) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(if (item.isLive) YtRed else Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(item.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.author?.let { Text(it, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
