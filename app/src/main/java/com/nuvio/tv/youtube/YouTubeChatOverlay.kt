package com.nuvio.tv.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val VISIBLE_LINES = 12

/**
 * The live chat of a live stream, drawn over the right side of the picture as messages arrive, newest at the
 * bottom. Read-only: it has no focus, so the remote keeps working on the player.
 */
@Composable
fun BoxScope.YouTubeChatOverlay(viewModel: YouTubePlayerViewModel) {
    val lines by viewModel.chat.collectAsState()
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 24.dp)
            .width(320.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99000000))
            .padding(10.dp)
    ) {
        if (lines.isEmpty()) {
            Text("Waiting for chat…", color = Color(0xFFBBBBBB), fontSize = 12.sp)
        }
        lines.takeLast(VISIBLE_LINES).forEach { line ->
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF9BB8FF), fontWeight = FontWeight.SemiBold)) { append(line.author) }
                    append("  ")
                    withStyle(SpanStyle(color = Color.White)) { append(line.message) }
                },
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}
