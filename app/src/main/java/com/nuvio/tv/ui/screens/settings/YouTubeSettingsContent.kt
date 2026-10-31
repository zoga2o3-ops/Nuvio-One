package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

    Column(
        modifier = Modifier.fillMaxSize(),
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
    }
}
