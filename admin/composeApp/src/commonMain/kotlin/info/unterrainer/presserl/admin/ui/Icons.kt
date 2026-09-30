package info.unterrainer.presserl.admin.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Symbols drawn as vectors instead of text characters: the Wasm runtime has no font for arrows and
 * crosses and would download one from fonts.gstatic.com, which the admin CSP blocks. Path data from
 * Google's Material Icons (Apache 2.0), 24 × 24 viewport.
 */
object Icons {
    val Back = icon("Back", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
    val Up = icon("Up", "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z")
    val Down = icon("Down", "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z")
    val Close = icon(
        "Close",
        "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
    )
    val Undo = icon(
        "Undo",
        "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5" +
            "l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
    )
    val Redo = icon(
        "Redo",
        "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 " +
            "5.12 1.88L13 16h9V7l-3.6 3.6z",
    )
    val OpenInNew = icon(
        "OpenInNew",
        "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41" +
            "L19 6.41V10h2V3h-7z",
    )
    val PlayArrow = icon("PlayArrow", "M8 5v14l11-7z")

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}

/** An [icon] in the content colour; decorative unless it stands without a label, then give [contentDescription]. */
@Composable
fun SymbolIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(icon, contentDescription, modifier.size(size), tint)
}

/** Button content: [label] with [icon] in front of it, or behind it when [iconAfter]. */
@Composable
fun IconLabel(icon: ImageVector, label: String, iconAfter: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!iconAfter) {
            SymbolIcon(icon)
            Spacer(Modifier.width(6.dp))
        }
        Text(label)
        if (iconAfter) {
            Spacer(Modifier.width(6.dp))
            SymbolIcon(icon)
        }
    }
}
