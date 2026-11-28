package com.nuvio.tv.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connect to an Emby or Jellyfin server's music.
 *
 * A plain on-screen form rather than the phone-setup page Navidrome uses: a server address, a name
 * and a password are short enough to type with a remote, and the credentials are used once and not
 * kept — only the access token the server hands back is stored.
 */
@Composable
internal fun EmbyMusicSetupDialog(
    initial: EmbyMusicConfig,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A previously saved address, so re-opening the form doesn't mean typing it all again.
    var server by remember { mutableStateOf(initial.server) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var jellyfin by remember { mutableStateOf(initial.isJellyfin) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MusicEmby.connect(context, server, username, password, jellyfin)
            }
            busy = false
            result.onSuccess { onDismiss() }
                .onFailure { error = it.message ?: "Couldn't connect." }
        }
    }

    MusicDialogSurface(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(620.dp)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Emby / Jellyfin music", color = MusicPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Signs in to your server and lists its music albums in the Library. " +
                    "Your password is used once and is not saved; only the access token is kept.",
                color = MusicPalette.textMuted,
                fontSize = 12.sp
            )

            MusicTextField(
                label = "Server",
                value = server,
                placeholder = "192.168.1.20:8096",
                onValueChange = { server = it },
                fontSize = 14.sp,
                onDone = { connect() },
            )

            // Two buttons rather than a dropdown: the remote can reach both, and the choice is
            // the only thing that changes in how the server is addressed.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MusicChoicePill("Emby", selected = !jellyfin, onClick = { jellyfin = false })
                MusicChoicePill("Jellyfin", selected = jellyfin, onClick = { jellyfin = true })
            }

            MusicTextField(
                label = "Username",
                value = username,
                placeholder = "",
                onValueChange = { username = it },
                fontSize = 14.sp,
                onDone = { connect() },
            )
            MusicTextField(
                label = "Password",
                value = password,
                placeholder = "",
                onValueChange = { password = it },
                fontSize = 14.sp,
                password = true,
                onDone = { connect() },
            )

            error?.let {
                Text(it, color = MusicPalette.accentCoral, fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MusicChoicePill(
                    label = if (busy) "Connecting…" else "Connect",
                    selected = true,
                    onClick = { connect() },
                )
                MusicChoicePill(label = "Cancel", selected = false, onClick = onDismiss)
                if (initial.configured) {
                    MusicChoicePill(
                        label = "Disconnect",
                        selected = false,
                        onClick = {
                            MusicEmby.disconnect(context)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** The dialog chrome the other Music dialogs use: a raised panel over a dimmed backdrop. */
@Composable
private fun MusicDialogSurface(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MusicPalette.surfaceRaised)
                .padding(2.dp)
        ) { content() }
    }
}

@Composable
private fun MusicChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.tv.material3.Button(
        onClick = onClick,
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = if (selected) MusicPalette.accentTeal else MusicPalette.surfaceRaised,
            contentColor = if (selected) MusicPalette.background else MusicPalette.text,
        ),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
