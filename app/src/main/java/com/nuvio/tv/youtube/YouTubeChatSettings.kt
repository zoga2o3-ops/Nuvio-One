package com.nuvio.tv.youtube

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.NuvioDialog

/** How the in-player live chat looks. Kept in [YouTubeSettings], applied as the viewer changes it. */
data class ChatAppearance(
    val position: String = "center_end",
    val size: String = "medium",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val nameColor: Int = 0xFF9BB8FF.toInt(),
    val backgroundOpacity: Int = 60
)

private val POSITIONS = listOf(
    "top_start", "top_center", "top_end",
    "center_start", "center", "center_end",
    "bottom_start", "bottom_center", "bottom_end"
)
private val POSITION_LABELS = mapOf(
    "top_start" to "Top left", "top_center" to "Top middle", "top_end" to "Top right",
    "center_start" to "Middle left", "center" to "Centre", "center_end" to "Middle right",
    "bottom_start" to "Bottom left", "bottom_center" to "Bottom middle", "bottom_end" to "Bottom right"
)
private val SIZES = listOf("small", "medium", "large")
private val SIZE_LABELS = mapOf("small" to "Small", "medium" to "Medium", "large" to "Large")
private val COLORS = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFFD0D0D0.toInt() to "Light grey",
    0xFFFFE066.toInt() to "Yellow",
    0xFF7FE3FF.toInt() to "Cyan",
    0xFF8CFF9E.toInt() to "Green",
    0xFFFF9B9B.toInt() to "Red"
)
private val OPACITIES = listOf(0, 25, 50, 75, 90)

fun loadChatAppearance(context: Context) = ChatAppearance(
    position = YouTubeSettings.chatPosition(context),
    size = YouTubeSettings.chatSize(context),
    textColor = YouTubeSettings.chatTextColor(context),
    nameColor = YouTubeSettings.chatNameColor(context),
    backgroundOpacity = YouTubeSettings.chatBackgroundOpacity(context)
)

fun saveChatAppearance(appearance: ChatAppearance, context: Context) {
    YouTubeSettings.setChatPosition(context, appearance.position)
    YouTubeSettings.setChatSize(context, appearance.size)
    YouTubeSettings.setChatTextColor(context, appearance.textColor)
    YouTubeSettings.setChatNameColor(context, appearance.nameColor)
    YouTubeSettings.setChatBackgroundOpacity(context, appearance.backgroundOpacity)
}

fun chatPositionLabel(value: String) = POSITION_LABELS[value] ?: "Middle right"
fun chatSizeLabel(value: String) = SIZE_LABELS[value] ?: "Medium"
fun chatColorLabel(value: Int) = COLORS.firstOrNull { it.first == value }?.second ?: "White"

private fun nextPosition(value: String) = POSITIONS[(POSITIONS.indexOf(value).coerceAtLeast(0) + 1) % POSITIONS.size]
private fun nextSize(value: String) = SIZES[(SIZES.indexOf(value).coerceAtLeast(0) + 1) % SIZES.size]
private fun nextColor(value: Int) = COLORS[(COLORS.indexOfFirst { it.first == value }.coerceAtLeast(0) + 1) % COLORS.size].first
private fun nextOpacity(value: Int) = OPACITIES[(OPACITIES.indexOf(value).coerceAtLeast(0) + 1) % OPACITIES.size]

/**
 * Chat appearance settings shown over the player, with the chat itself still visible so every
 * change is seen immediately. Each row cycles when pressed, which keeps it to one remote action.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun YouTubeChatSettingsPanel(
    appearance: ChatAppearance,
    onChange: (ChatAppearance) -> Unit,
    onClose: () -> Unit
) {
    NuvioDialog(
        onDismiss = onClose,
        title = "Live chat",
        subtitle = "Changes show up right away",
        containerBrush = androidx.compose.ui.graphics.SolidColor(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.70f)
        ),
        containerBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleRow("Position", chatPositionLabel(appearance.position)) {
                onChange(appearance.copy(position = nextPosition(appearance.position)))
            }
            CycleRow("Size", chatSizeLabel(appearance.size)) {
                onChange(appearance.copy(size = nextSize(appearance.size)))
            }
            CycleRow("Text colour", chatColorLabel(appearance.textColor)) {
                onChange(appearance.copy(textColor = nextColor(appearance.textColor)))
            }
            CycleRow("Name colour", chatColorLabel(appearance.nameColor)) {
                onChange(appearance.copy(nameColor = nextColor(appearance.nameColor)))
            }
            CycleRow("Background", "${appearance.backgroundOpacity}%") {
                onChange(appearance.copy(backgroundOpacity = nextOpacity(appearance.backgroundOpacity)))
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CycleRow(label: String, value: String, onCycle: () -> Unit) {
    Button(onClick = onCycle, modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Text("$label: $value", maxLines = 1)
    }
}
