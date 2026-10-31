package com.nuvio.tv.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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
import com.nuvio.tv.core.qr.QrCodeGenerator

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

    // Signing in happens on this page: the code shows here and the tab reloads when it's approved.
    val accountViewModel: YouTubeAccountViewModel = hiltViewModel()
    val account by accountViewModel.state.collectAsState()
    var signInStarted by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { accountViewModel.refresh() }
    LaunchedEffect(account.signedIn) {
        if (signInStarted && account.signedIn) {
            signInStarted = false
            viewModel.selectTab(state.tab)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Back goes up from a channel, playlist or search before it leaves the page.
    BackHandler(enabled = state.subTitle != null) { viewModel.back() }
    BackHandler(enabled = showAccounts) { showAccounts = false }

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
                keyboardActions = KeyboardActions(onSearch = {
                    // Close the keyboard and move on to the results.
                    keyboard?.hide()
                    focusManager.moveFocus(FocusDirection.Down)
                    viewModel.search(query)
                }),
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
            Spacer(Modifier.weight(1f))
            // Who's watching: the account or channel profile in use. Sign in when there's none.
            TabChip(
                label = if (account.signedIn) "\u25CF  ${account.accountName ?: "Account"}" else "Sign in",
                selected = false
            ) {
                if (account.signedIn) showAccounts = !showAccounts
                else {
                    signInStarted = true
                    accountViewModel.startSignIn()
                }
            }
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

        Content(
            state = state,
            account = account,
            viewModel = viewModel,
            onPlay = onPlay,
            onSignIn = {
                signInStarted = true
                accountViewModel.startSignIn()
            },
            onCancelSignIn = {
                signInStarted = false
                accountViewModel.cancelSignIn()
            },
            showAccounts = showAccounts,
            onSelectAccount = { id ->
                showAccounts = false
                accountViewModel.selectAccount(id) { viewModel.selectTab(state.tab) }
            },
            onAddAccount = {
                showAccounts = false
                signInStarted = true
                accountViewModel.startSignIn()
            },
            onSignOut = {
                showAccounts = false
                accountViewModel.signOut { viewModel.selectTab(state.tab) }
            }
        )
    }
}

@Composable
private fun Content(
    state: YtUiState,
    account: YouTubeAccountState,
    viewModel: YouTubeViewModel,
    onPlay: (YtPlayable) -> Unit,
    onSignIn: () -> Unit,
    onCancelSignIn: () -> Unit,
    showAccounts: Boolean,
    onSelectAccount: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    when {
        account.code != null -> SignInPanel(account, onCancelSignIn)

        showAccounts -> AccountsPanel(account, onSelectAccount, onAddAccount, onSignOut)

        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = YtRed) }

        state.tab.needsSignIn && !state.signedIn && state.subTitle == null -> Message(
            "Sign in to see your ${state.tab.title.lowercase()}",
            account.error ?: "You'll get a code to enter at ${account.url}.",
            "Sign in",
            onSignIn
        )

        state.error != null && state.isEmpty -> Message(
            state.error,
            "Check your connection and try again.",
            "Try again"
        ) { viewModel.selectTab(state.tab) }

        state.isEmpty && !state.signedIn && state.tab == YtTab.HOME && state.subTitle == null -> Message(
            "Home is picked for you",
            "YouTube only fills it once you sign in. Try Trending, Music or Gaming above, or sign in.",
            "Sign in",
            onSignIn
        )

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
private fun SignInPanel(account: YouTubeAccountState, onCancel: () -> Unit) {
    val qr = remember(account.activateLink) {
        account.activateLink?.let { link ->
            runCatching { QrCodeGenerator.generate(link, 480, margin = 1).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
            qr?.let {
                Image(it, contentDescription = "QR code to sign in", modifier = Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Sign in to YouTube", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (qr != null) "Scan the code with your phone, or go to ${account.url} and enter:"
                    else "On your phone or computer, go to ${account.url} and enter:",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
                Text(account.code.orEmpty(), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("Waiting for you to approve it\u2026", color = Color.Gray, fontSize = 14.sp)
                TabChip("Cancel", selected = false, onClick = onCancel)
            }
        }
    }
}

@Composable
private fun AccountsPanel(
    account: YouTubeAccountState,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onSignOut: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(Modifier.width(520.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Who's watching?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            account.accounts.forEachIndexed { i, a ->
                AccountRow(
                    title = if (a.selected) "${a.name}   \u2713" else a.name,
                    detail = a.detail,
                    avatar = a.avatar,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier
                ) { onSelect(a.id) }
            }
            AccountRow("Add another account", null, null, Modifier) { onAdd() }
            AccountRow("Sign out of ${account.accountName ?: "this account"}", null, null, Modifier) { onSignOut() }
        }
    }
}

@Composable
private fun AccountRow(title: String, detail: String?, avatar: String?, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
            )
        }
        Column {
            Text(title, color = Color.White, fontSize = 17.sp)
            detail?.let { Text(it, color = Color.Gray, fontSize = 13.sp) }
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
