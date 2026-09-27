package info.unterrainer.presserl.admin.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The section palette keys in the server's palette order. */
val SECTION_COLORS = listOf("red", "orange", "yellow", "green", "teal", "blue", "purple", "pink")

/** The colour the server picks for a new section when none is sent. */
fun defaultSectionColor(existingSections: Int): String = SECTION_COLORS[existingSections % SECTION_COLORS.size]

/** Display colour of a palette key; the reader theme defines its own. */
fun sectionColor(key: String): Color = when (key) {
    "red" -> Color(0xFFD32F2F)
    "orange" -> Color(0xFFF57C00)
    "yellow" -> Color(0xFFFBC02D)
    "green" -> Color(0xFF388E3C)
    "teal" -> Color(0xFF00897B)
    "blue" -> Color(0xFF1976D2)
    "purple" -> Color(0xFF7B1FA2)
    "pink" -> Color(0xFFC2185B)
    else -> Color.Gray
}

@Composable
fun ColorMarker(key: String, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(sectionColor(key), CircleShape))
}
