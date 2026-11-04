package com.nuvio.tv.youtube

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.ui.components.NuvioDialog
import java.util.Date
import kotlinx.coroutines.delay
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
    val cardMenu by viewModel.menu.collectAsState()
    var query by remember { mutableStateOf("") }

    // Signing in happens on this page: the code shows here and the tab reloads when it's approved.
    val accountViewModel: YouTubeAccountViewModel = hiltViewModel()
    val account by accountViewModel.state.collectAsState()
    var signInStarted by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var suggestionsOpen by remember { mutableStateOf(false) }
    val suggestions by viewModel.suggestions.collectAsState()
    val firstSuggestion = remember { FocusRequester() }
    // Search stays a magnifier until it's opened, so the header isn't dominated by a text box.
    var searchExpanded by remember { mutableStateOf(false) }
    var collapseToSearchIcon by remember { mutableStateOf(false) }
    val searchIconFocus = remember { FocusRequester() }

    // The tabs you've left on in Settings. Read each time you come to the page.
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val tabs = remember { YouTubeSettings.visibleTabs(appContext) }
    var pinnedPlaylists by remember { mutableStateOf(YouTubeSettings.pinnedPlaylists(appContext)) }
    // Pinning or unpinning happens in the card menu; refresh the row when that closes.
    LaunchedEffect(cardMenu) {
        if (cardMenu == null) pinnedPlaylists = YouTubeSettings.pinnedPlaylists(appContext)
    }
    LaunchedEffect(tabs) {
        // A tab you've since turned off can't stay open.
        if (state.tab !in tabs) viewModel.selectTab(tabs.first())
    }
    LaunchedEffect(Unit) { accountViewModel.refresh() }
    LaunchedEffect(account.signedIn) {
        if (signInStarted && account.signedIn) {
            signInStarted = false
            viewModel.selectTab(state.tab)
        }
    }
    // The page can open before the account is known; once it turns out you're signed in, load your feed.
    LaunchedEffect(account.checked, account.signedIn) {
        if (account.checked && account.signedIn && !state.signedIn) viewModel.selectTab(state.tab)
    }
    // A sign-in that YouTube's account list didn't answer keeps the token but not the name yet.
    var nameRefreshTried by remember { mutableStateOf(false) }
    LaunchedEffect(account.checked, account.signedIn, account.accountName) {
        if (account.checked && account.signedIn && account.accountName == null && !nameRefreshTried) {
            nameRefreshTried = true
            accountViewModel.refreshAccountInfo()
        }
    }
    // An error from a card action is useful for a moment, then it should get out of the way.
    LaunchedEffect(error) {
        if (error != null) {
            delay(6000)
            viewModel.clearError()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val sidebarExpanded = com.nuvio.tv.LocalSidebarExpanded.current
    val openSidebar = com.nuvio.tv.LocalOpenSidebar.current

    // After the search box closes, the remote's highlight goes back to the magnifier.
    LaunchedEffect(collapseToSearchIcon) {
        if (collapseToSearchIcon) {
            runCatching { searchIconFocus.requestFocus() }
            collapseToSearchIcon = false
        }
    }

    // Back goes up from a channel, playlist or search before it leaves the page.
    BackHandler(enabled = state.subTitle == null && !showAccounts && !suggestionsOpen && !sidebarExpanded) { openSidebar() }
    BackHandler(enabled = state.subTitle != null) { viewModel.back() }
    BackHandler(enabled = showAccounts) { showAccounts = false }
    BackHandler(enabled = suggestionsOpen) { suggestionsOpen = false }

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The left half: who's watching, then the page name.
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Who's watching: the account or channel profile in use. Sign in when there's none.
                // First in the row, so pressing up from the first tab lands straight on it.
                TabChip(
                    label = when {
                        account.signedIn -> "●  ${account.accountName ?: "Account"}"
                        !account.checked -> "Account"
                        else -> "Sign in"
                    },
                    selected = false
                ) {
                    if (account.signedIn) showAccounts = !showAccounts
                    else {
                        signInStarted = true
                        accountViewModel.startSignIn()
                    }
                }
                Text(
                    state.subTitle ?: "YouTube",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            // The time, dead centre, when it's on in Settings.
            if (YouTubeSettings.showClock(appContext)) HeaderClock()
            var editing by remember { mutableStateOf(false) }
            // The right half: the search and the settings gear, together at the end.
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
            ) {
                if (!searchExpanded) {
                    IconChip(Icons.Default.Search, "Search", searchIconFocus) {
                        searchExpanded = true
                        editing = true
                    }
                } else {
                    // The box is selected like any other item on the page (it's just something the arrows land on),
                    // and only starts taking text, with the keyboard, once OK is pressed on it.
                    var boxFocused by remember { mutableStateOf(false) }
                    val textFocus = remember { FocusRequester() }
                    LaunchedEffect(editing) {
                        if (editing) {
                            runCatching { textFocus.requestFocus() }
                            keyboard?.show()
                        }
                    }
                    // Back out of the search: the keyboard goes, the suggestions close, and the
                    // magnifier takes its place again. The query is kept for the next time it opens.
                    BackHandler {
                        keyboard?.hide()
                        suggestionsOpen = false
                        editing = false
                        searchExpanded = false
                        collapseToSearchIcon = true
                    }
                    Box(
                        Modifier
                            .width(320.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(CardBg)
                            .border(2.dp, if (boxFocused || editing) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
                            // Down from the box goes into the suggestions when they're showing.
                            .focusProperties { if (suggestionsOpen && suggestions.isNotEmpty()) down = firstSuggestion }
                            .onFocusChanged { boxFocused = it.isFocused }
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { editing = true }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                viewModel.suggest(it)
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                // Close the keyboard and move on to the results.
                                keyboard?.hide()
                                suggestionsOpen = false
                                editing = false
                                focusManager.moveFocus(FocusDirection.Down)
                                viewModel.search(query)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(textFocus)
                                // Only while typing: the arrows never land inside the text field by themselves.
                                .focusProperties {
                                    canFocus = editing
                                    if (suggestionsOpen && suggestions.isNotEmpty()) down = firstSuggestion
                                }
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        suggestionsOpen = true
                                        viewModel.suggest(query)
                                    } else if (editing) {
                                        // Focus left the box (down into the suggestions, say): done typing.
                                        editing = false
                                    }
                                },
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Search YouTube", color = Color.Gray, fontSize = 18.sp)
                                inner()
                            }
                        )
                    }
                }
                IconChip(Icons.Default.Settings, "YouTube settings") { onOpenSettings() }
            }
        }

        LazyRow(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tabs) { tab ->
                TabChip(tab.title, selected = state.tab == tab && state.subTitle == null) {
                    query = ""
                    suggestionsOpen = false
                    viewModel.selectTab(tab)
                }
            }
        }

        // Playlists pinned from their card menu, so they're one press away from the top of the page.
        if (pinnedPlaylists.isNotEmpty() && state.subTitle == null) {
            LazyRow(
                Modifier.padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text("Pinned", color = Color.Gray, fontSize = 14.sp)
                }
                items(pinnedPlaylists) { (id, title) ->
                    TabChip(title, selected = false) {
                        query = ""
                        suggestionsOpen = false
                        viewModel.openPlaylistById(id, title)
                    }
                }
            }
        }

        error?.let {
            Text(it, color = YtRed, modifier = Modifier.padding(bottom = 8.dp).clickable { viewModel.clearError() })
        }

        // A failed sign-in used to be silent: the code screen closed and nothing said why.
        account.error?.let {
            Text(it, color = YtRed, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (suggestionsOpen && suggestions.isNotEmpty()) {
            SuggestionList(
                modifier = Modifier.weight(1f, fill = false),
                items = suggestions,
                recent = query.isBlank(),
                first = firstSuggestion,
                onPick = { text ->
                    query = text
                    suggestionsOpen = false
                    keyboard?.hide()
                    viewModel.search(text)
                },
                onClearHistory = {
                    viewModel.clearSearchHistory()
                    suggestionsOpen = false
                }
            )
        } else Content(
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

    cardMenu?.let { open ->
        YouTubeItemMenuDialog(
            menu = open,
            onClose = { viewModel.closeMenu() },
            onOpenChannel = {
                open.item.channelId?.let { id ->
                    viewModel.openChannel(id, open.item.author?.takeIf { it.isNotBlank() } ?: open.item.title)
                }
                viewModel.closeMenu()
            },
            onOpenItem = {
                viewModel.open(open.item)
                viewModel.closeMenu()
            },
            onToggleSubscribe = { viewModel.menuToggleSubscribe() },
            onTogglePlaylist = { viewModel.menuTogglePlaylist(it) },
            onNewPlaylist = { viewModel.menuCreatePlaylist(it) },
            onTogglePinned = { viewModel.menuTogglePinned() },
            onBlockChannel = { viewModel.menuBlockChannel() }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    // Rows size their cards from the "Posters per row" setting too, so every tab honours it.
    val columnCount = YouTubeSettings.posterColumns(context)
    val screenWidthDp = context.resources.configuration.screenWidthDp
    val rowCardWidth = ((screenWidthDp - 48 - (columnCount - 1) * 14) / columnCount)
        .coerceAtLeast(140).dp
    var needsInitialFocus by remember { mutableStateOf(true) }
    val canRequestInitialFocus = needsInitialFocus && !com.nuvio.tv.LocalSidebarExpanded.current
    val restoreId = viewModel.lastPlayedVideoId
    val firstRowWithVideo = state.rows.indexOfFirst { row -> row.items.any { it.kind == YtKind.VIDEO } }
    val restoredRow = state.rows.indexOfFirst { row -> row.items.any { it.kind == YtKind.VIDEO && it.videoId == restoreId } }
    val targetRow = if (restoredRow >= 0) restoredRow else firstRowWithVideo
    val targetRowVideo = state.rows.getOrNull(targetRow)?.items?.let { items ->
        items.indexOfFirst { it.kind == YtKind.VIDEO && it.videoId == restoreId }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.kind == YtKind.VIDEO }
    } ?: -1
    val restoredGridIndex = state.grid.indexOfFirst { it.kind == YtKind.VIDEO && it.videoId == restoreId }
    val targetGridIndex = if (restoredGridIndex >= 0) restoredGridIndex else state.grid.indexOfFirst { it.kind == YtKind.VIDEO }
    when {
        account.code != null -> SignInPanel(account, onCancelSignIn)

        showAccounts -> AccountsPanel(account, onSelectAccount, onAddAccount, onSignOut)

        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = YtRed) }

        !account.checked && !state.signedIn && state.subTitle == null && (state.tab.needsSignIn || state.tab == YtTab.HOME) ->
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = YtRed) }

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
        ) {
            // Take the remote back to the content once it loads, rather than leaving it on the
            // header after the button disappears.
            needsInitialFocus = true
            viewModel.selectTab(state.tab)
        }

        state.isEmpty && !state.signedIn && state.tab == YtTab.HOME && state.subTitle == null -> Message(
            "Home is picked for you",
            "YouTube only fills it once you sign in. Try Live, Music or Gaming above, or sign in.",
            "Sign in",
            onSignIn
        )

        state.isEmpty -> Message(
            "Nothing here yet",
            "YouTube didn't send this page. It can be busy for a moment.",
            "Try again"
        ) {
            needsInitialFocus = true
            viewModel.selectTab(state.tab)
        }

        state.rows.isNotEmpty() -> {
          val rowsState = rememberLazyListState()
          LaunchedEffect(needsInitialFocus, targetRow) {
              if (needsInitialFocus && targetRow >= 0) rowsState.scrollToItem(targetRow)
          }
          LazyColumn(Modifier.fillMaxSize(), state = rowsState, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(state.rows) { rowIndex, row ->
                val rowState = rememberLazyListState()
                LaunchedEffect(needsInitialFocus, targetRow, targetRowVideo) {
                    if (needsInitialFocus && rowIndex == targetRow && targetRowVideo >= 0) rowState.scrollToItem(targetRowVideo)
                }
                Column {
                    if (row.title.isNotBlank()) {
                        Text(row.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    LazyRow(Modifier.padding(top = 8.dp), state = rowState, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(row.items) { i, v ->
                            ItemCard(v, viewModel, Modifier.width(rowCardWidth), requestInitialFocus = canRequestInitialFocus && rowIndex == targetRow && i == targetRowVideo, onInitialFocus = { needsInitialFocus = false }, onFocused = {
                                viewModel.prefetch(v)
                                if (row.canLoadMore && i >= row.items.size - 4) viewModel.loadMoreRow(rowIndex)
                            }, onLongClick = { viewModel.openMenu(v) }) {
                                viewModel.activate(v, row.items, onPlay)
                            }
                        }
                    }
                }
            }
          }
        }

        state.tab == YtTab.CHANNELS && state.subTitle == null -> ChannelsSplit(state, viewModel, onPlay, canRequestInitialFocus) { needsInitialFocus = false }

        else -> {
            val gridState = rememberLazyGridState()
            LaunchedEffect(needsInitialFocus, targetGridIndex) {
                if (needsInitialFocus && targetGridIndex >= 0) gridState.scrollToItem(targetGridIndex)
            }
            // Keep loading as you near the end, like an endless feed.
            LaunchedEffect(gridState, state.canLoadMore) {
                snapshotFlow {
                    val info = gridState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
                }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(
                    YouTubeSettings.posterColumns(androidx.compose.ui.platform.LocalContext.current)
                ),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItemsIndexed(state.grid) { index, v ->
                    ItemCard(v, viewModel, Modifier, requestInitialFocus = canRequestInitialFocus && index == targetGridIndex, onInitialFocus = { needsInitialFocus = false }, onFocused = { viewModel.prefetch(v) }, onLongClick = { viewModel.openMenu(v) }) { viewModel.activate(v, state.grid, onPlay) }
                }
            }
        }
    }
}

/** Your subscribed channels as a list; the highlighted one's videos show beside it, like SmartTube. */
@Composable
private fun ChannelsSplit(state: YtUiState, viewModel: YouTubeViewModel, onPlay: (YtPlayable) -> Unit, needsInitialFocus: Boolean, onInitialFocus: () -> Unit) {
    val preview by viewModel.preview.collectAsState()
    // Only OK loads a channel's videos; moving the focus just highlights the name.
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyColumn(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(state.grid) { i, channel ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(3.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused && i >= state.grid.size - 5) viewModel.loadMore()
                        }
                        .clickable {
                            selectedChannel = channel.title
                            viewModel.previewChannel(channel)
                        }
                        .background(if (selectedChannel == channel.title) Color(0xFF2A2A2E) else CardBg)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = channel.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    )
                    Text(
                        channel.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val gridState = rememberLazyGridState()
            val restoredIndex = preview.items.indexOfFirst { it.kind == YtKind.VIDEO && it.videoId == viewModel.lastPlayedVideoId }
            val initialIndex = if (restoredIndex >= 0) restoredIndex else preview.items.indexOfFirst { it.kind == YtKind.VIDEO }
            LaunchedEffect(needsInitialFocus, initialIndex) {
                if (needsInitialFocus && initialIndex >= 0) gridState.scrollToItem(initialIndex)
            }
            LaunchedEffect(gridState, preview.canLoadMore, preview.title) {
                snapshotFlow {
                    val info = gridState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
                }.collect { nearEnd -> if (nearEnd) viewModel.previewMore() }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(
                    YouTubeSettings.posterColumns(androidx.compose.ui.platform.LocalContext.current)
                ),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItemsIndexed(preview.items) { index, v ->
                    ItemCard(v, viewModel, Modifier, requestInitialFocus = needsInitialFocus && index == initialIndex, onInitialFocus = onInitialFocus, onFocused = { viewModel.prefetch(v) }, onLongClick = { viewModel.openMenu(v) }) { viewModel.activate(v, preview.items, onPlay) }
                }
            }
            if (preview.loading && preview.items.isEmpty()) {
                CircularProgressIndicator(color = YtRed, modifier = Modifier.align(Alignment.Center))
            }
            if (!preview.loading && preview.items.isEmpty()) {
                Text(
                    "Choose a channel and press OK to see its videos",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
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
private fun SuggestionList(
    items: List<String>,
    recent: Boolean,
    first: FocusRequester,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A long history (or a long list of suggestions) scrolls instead of running off the bottom of the screen.
    Column(modifier.width(520.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (recent) "Recent searches" else "Suggestions", color = Color.Gray, fontSize = 14.sp)
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items) { i, text ->
                AccountRow(text, null, null, if (i == 0) Modifier.focusRequester(first) else Modifier) { onPick(text) }
            }
            if (recent) item {
                AccountRow("Clear search history", null, null, Modifier) { onClearHistory() }
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

/** A round, focusable icon button, for the remote's header actions. */
@Composable
private fun IconChip(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(CardBg)
            .border(2.dp, if (focused) Color.White else Color.Transparent, CircleShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** The time in the middle of the header, in this device's 12/24-hour form. */
@Composable
private fun HeaderClock() {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    var time by remember(format) { mutableStateOf(format.format(Date())) }
    // Check once a second but only recompose when the shown minute actually changes.
    LaunchedEffect(format) {
        while (true) {
            val now = format.format(Date())
            if (now != time) time = now
            delay(1000)
        }
    }
    Text(
        time,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun YouTubeItemMenuDialog(
    menu: YtItemMenu,
    onClose: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenItem: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onTogglePlaylist: (YtPlaylistRow) -> Unit,
    onNewPlaylist: (String) -> Unit,
    onTogglePinned: () -> Unit,
    onBlockChannel: () -> Unit
) {
    var showPlaylists by remember(menu.item.videoId) { mutableStateOf(false) }
    var creatingPlaylist by remember(menu.item.videoId) { mutableStateOf(false) }
    var newPlaylistName by remember(menu.item.videoId) { mutableStateOf("") }
    val firstRow = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(menu.item.videoId, creatingPlaylist) {
        kotlinx.coroutines.yield()
        if (creatingPlaylist) {
            runCatching { nameFocus.requestFocus() }
            keyboard?.show()
        } else {
            runCatching { firstRow.requestFocus() }
        }
    }
    val isVideo = menu.item.kind == YtKind.VIDEO
    val hasChannel = menu.item.channelId != null

    if (creatingPlaylist) {
        BackHandler {
            creatingPlaylist = false
            newPlaylistName = ""
            keyboard?.hide()
        }
    }

    fun create() {
        if (newPlaylistName.isBlank()) return
        onNewPlaylist(newPlaylistName)
        creatingPlaylist = false
        newPlaylistName = ""
        keyboard?.hide()
    }

    NuvioDialog(
        onDismiss = onClose,
        title = if (creatingPlaylist) "New playlist" else menu.item.title,
        subtitle = if (creatingPlaylist) "The video is added to it" else menu.item.author,
        width = 560.dp
    ) {
        if (creatingPlaylist) {
            OutlinedTextField(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                singleLine = true,
                label = { Text("Playlist name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
            )
            MenuRow("Save to new playlist", enabled = newPlaylistName.isNotBlank() && !menu.busy) { create() }
            MenuRow("Cancel") {
                creatingPlaylist = false
                newPlaylistName = ""
                keyboard?.hide()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isVideo && hasChannel) {
                    MenuRow("Open channel", firstRow, onClick = onOpenChannel)
                }
                if (menu.item.kind == YtKind.CHANNEL) {
                    MenuRow("Open channel", firstRow, onClick = onOpenItem)
                }
                if (menu.item.kind == YtKind.PLAYLIST) {
                    MenuRow("Open playlist", firstRow, onClick = onOpenItem)
                    MenuRow(
                        if (menu.pinned) "Unpin from the YouTube page" else "Pin to the YouTube page",
                        enabled = !menu.busy,
                        onClick = onTogglePinned
                    )
                }
                if (isVideo && hasChannel && menu.subscribed != null) {
                    MenuRow(
                        if (menu.subscribed == true) "Unsubscribe" else "Subscribe",
                        enabled = !menu.busy,
                        onClick = onToggleSubscribe
                    )
                }
                if (isVideo) {
                    when {
                        menu.loadingPlaylists -> Text("Loading playlists…", color = Color.Gray, fontSize = 14.sp)
                        menu.playlists != null -> {
                            MenuRow(
                                if (showPlaylists) "Hide playlists" else "Add to playlist",
                                enabled = !menu.busy
                            ) { showPlaylists = !showPlaylists }
                            if (showPlaylists) {
                                menu.playlists.forEach { row ->
                                    MenuRow(
                                        (if (row.contains) "✓  " else "   ") + row.title,
                                        enabled = !menu.busy
                                    ) { onTogglePlaylist(row) }
                                }
                            }
                            MenuRow("New playlist", enabled = !menu.busy) {
                                creatingPlaylist = true
                            }
                        }
                    }
                }
                if (isVideo && hasChannel) {
                    MenuRow("Block this channel", enabled = !menu.busy, onClick = onBlockChannel)
                }
            }
        }
        menu.message?.let {
            Text(it, color = Color(0xFFFF9A9A), fontSize = 13.sp)
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (enabled) Color.White else Color.Gray,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF34343A) else Color.Transparent)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/** Separators YouTube puts between the parts of a card's second line. */
private val YOUTUBE_INFO_SEPARATOR = Regex("\\s+[.·•|]\\s+|\\s*[•·|]\\s*|\\n")

/** Bidi and zero-width marks the engine wraps around these parts; they stop "@" checks matching. */
private val YOUTUBE_INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\u061C\\uFEFF]")

private fun String.cleanYouTubeText(): String = replace(YOUTUBE_INVISIBLE, "").trim()

private fun youtubeInfoSegments(meta: String): List<String> =
    meta.split(YOUTUBE_INFO_SEPARATOR).map { it.cleanYouTubeText() }.filter { it.isNotBlank() }

/** Case and spacing don't matter when deciding whether two info parts are the same channel. */
private fun youtubeInfoKey(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

/** A views line rather than a channel name. */
private fun looksLikeYouTubeCount(value: String): Boolean =
    value.any { it.isDigit() } && (
        value.contains("view", ignoreCase = true) ||
            value.contains("watch", ignoreCase = true) ||
            Regex("\\b\\d+(\\.\\d+)?\\s*[KMB]\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)
        )

/** Whether a segment is a posted-time phrase rather than a count or channel. */
private fun looksLikeYouTubeTime(value: String): Boolean =
    value.contains("ago", ignoreCase = true) ||
        value.contains("yesterday", ignoreCase = true) ||
        value.contains("today", ignoreCase = true) ||
        Regex("\\b(19|20)\\d{2}\\b").containsMatchIn(value)

@Composable
private fun ItemCard(item: YtItem, viewModel: YouTubeViewModel, modifier: Modifier, requestInitialFocus: Boolean = false, onInitialFocus: () -> Unit = {}, onFocused: () -> Unit, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember(item.videoId) { mutableStateOf(false) }
    var opened by remember(item.videoId) { mutableStateOf(false) }
    // OK is held down on this poster (and whether that already counted as a long press).
    var held by remember(item.videoId) { mutableStateOf(false) }
    var longFired by remember(item.videoId) { mutableStateOf(false) }
    // The side menu covers the page, so a preview should not keep playing behind it.
    val sidebarExpanded = com.nuvio.tv.LocalSidebarExpanded.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            kotlinx.coroutines.yield()
            runCatching { focusRequester.requestFocus() }
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    // What this device has played, or what your account says, whichever is further along.
    val watched = remember(item.videoId) {
        maxOf(item.percentWatched, YouTubeProgress.localPercent(context, item.videoId))
    }
    // A video that is being looked up shows a spinner, so a slow start is visible and repeated presses
    // are not needed.
    val resolving by viewModel.resolving.collectAsState()
    Column(
        modifier
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) opened = false
                if (it.isFocused && requestInitialFocus) onInitialFocus()
                if (it.isFocused) onFocused()
            }
            // Holding OK opens the poster's channel; a plain press still plays it.
            .onPreviewKeyEvent { e ->
                val longAction = onLongClick ?: return@onPreviewKeyEvent false
                val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!isOk) return@onPreviewKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            held = true
                            longFired = false
                        } else if (held && !longFired) {
                            longFired = true
                            longAction()
                        }
                    }
                    KeyEventType.KeyUp -> {
                        // A plain press plays the video; a long press already did its own action.
                        if (held && !longFired) {
                            opened = true
                            onClick()
                        }
                        held = false
                        longFired = false
                    }
                }
                true
            }
            .clickable { opened = true; onClick() }
            .background(if (focused) Color(0xFF34343A) else CardBg)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (item.isShorts) 9f / 16f else 16f / 9f)) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (focused && !opened && !sidebarExpanded && item.kind == YtKind.VIDEO && YouTubeSettings.videoPreviews(context)) {
                YouTubeThumbnailPreview(item, viewModel)
            }
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
            if (item.videoId != null && resolving == item.videoId) {
                YouTubeLoadingGlow(Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(10.dp)) {
            // Two lines reserved, so the info below has room and sits at the same height on every card.
            Text(
                item.title,
                color = Color.White,
                fontSize = 14.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Two info lines: the channel, then views and post time. Some feeds put the whole
            // "channel . @handle . channel . views" string in one field and add invisible bidi
            // marks around the parts, so clean and split both fields together.
            val infoSegments = buildList {
                item.author?.let { addAll(youtubeInfoSegments(it)) }
                item.meta?.let { addAll(youtubeInfoSegments(it)) }
            }
            val channel = infoSegments.firstOrNull { !it.startsWith("@") && !looksLikeYouTubeCount(it) }
            val channelKey = channel?.let(::youtubeInfoKey)
            val details = infoSegments
                .filter { !it.startsWith("@") }
                .filter { channelKey == null || youtubeInfoKey(it) != channelKey }
                .distinctBy { youtubeInfoKey(it) }
            val channelText = channel
            // History/Subscriptions and Music sometimes leave the post time or count out of the
            // second line; the engine's production date text still has them.
            val fallback = item.postTime
                ?.let(::youtubeInfoSegments)
                .orEmpty()
                .filter { !it.startsWith("@") && (looksLikeYouTubeTime(it) || looksLikeYouTubeCount(it)) }
                .filter { channelKey == null || youtubeInfoKey(it) != channelKey }
            val detailText = (details + fallback)
                .distinctBy { youtubeInfoKey(it) }
                .joinToString("  ·  ")
            if (!channelText.isNullOrBlank()) {
                Text(channelText, color = Color(0xFF8A8A92), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // Keep the two-line info block even when the feed has no channel line.
                Spacer(Modifier.height(16.dp))
            }
            if (detailText.isNotBlank()) {
                Text(detailText, color = Color(0xFF8A8A92), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
