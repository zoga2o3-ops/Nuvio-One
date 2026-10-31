package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.youtube.YouTubeSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.youtube.YouTubeAccountViewModel

/** Settings → YouTube: sign in with a code from youtube.com/activate, or sign out. */
@Composable
internal fun YouTubeSettingsContent(
    viewModel: YouTubeAccountViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val context = LocalContext.current
    var sponsorOn by remember { mutableStateOf(YouTubeSettings.sponsorBlockEnabled(context)) }
    var categories by remember { mutableStateOf(YouTubeSettings.sponsorCategoriesOn(context)) }
    var quality by remember { mutableStateOf(YouTubeSettings.preferredHeight(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = "YouTube",
            subtitle = "Sign in to see your subscriptions and watch history in the YouTube section"
        )

        SettingsGroupCard(title = "Account") {
            val first = if (initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier
            when {
                state.code != null -> {
                    Text(
                        text = "On your phone or computer, go to ${state.url} and enter:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.colors.TextSecondary,
                        modifier = Modifier.padding(vertical = NuvioTheme.spacing.sm)
                    )
                    Text(
                        text = state.code.orEmpty(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTheme.spacing.sm)
                    )
                    SettingsActionRow(
                        title = "Cancel",
                        subtitle = "Waiting for you to approve on your other device",
                        onClick = viewModel::cancelSignIn,
                        modifier = first
                    )
                }
                state.signedIn -> {
                    SettingsActionRow(
                        title = "Signed in",
                        subtitle = state.accountName ?: "Your YouTube account is connected",
                        onClick = {},
                        enabled = false
                    )
                    SettingsActionRow(
                        title = "Sign out",
                        subtitle = "Removes the account from this device",
                        onClick = viewModel::signOut,
                        leadingIcon = Icons.Default.Logout,
                        modifier = first
                    )
                }
                else -> {
                    SettingsActionRow(
                        title = "Sign in",
                        subtitle = state.error ?: "Get a code to enter at youtube.com/activate",
                        onClick = viewModel::startSignIn,
                        leadingIcon = Icons.Default.Login,
                        modifier = first
                    )
                }
            }
        }

        SettingsGroupCard(title = "Playback") {
            SettingsActionRow(
                title = "Default quality",
                subtitle = "The video size to start at. You can still change it in the player.",
                value = YouTubeSettings.qualityLabel(quality),
                onClick = {
                    val choices = YouTubeSettings.qualityChoices
                    quality = choices[(choices.indexOf(quality).coerceAtLeast(0) + 1) % choices.size]
                    YouTubeSettings.setPreferredHeight(context, quality)
                }
            )
        }

        SettingsGroupCard(
            title = "SponsorBlock",
            subtitle = "Skips parts of videos that viewers have marked, such as sponsor reads"
        ) {
            SettingsToggleRow(
                title = "Skip marked segments",
                subtitle = "Jumps past them and shows what was skipped",
                checked = sponsorOn,
                onToggle = {
                    sponsorOn = !sponsorOn
                    YouTubeSettings.setSponsorBlockEnabled(context, sponsorOn)
                }
            )
            if (sponsorOn) {
                YouTubeSettings.sponsorCategories.forEach { (key, label) ->
                    SettingsToggleRow(
                        title = label,
                        subtitle = null,
                        checked = key in categories,
                        onToggle = {
                            val on = key !in categories
                            YouTubeSettings.setSponsorCategory(context, key, on)
                            categories = YouTubeSettings.sponsorCategoriesOn(context)
                        }
                    )
                }
            }
        }
    }
}
