package info.unterrainer.presserl.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Below this width the header shrinks to a menu and the editor lets its status row scroll (design D1). */
val COMPACT_WIDTH = 720.dp

/** Whether the logged-in screens are narrower than [COMPACT_WIDTH]; provided by `LoggedIn`. */
val LocalCompactLayout = compositionLocalOf { false }

/**
 * Whether the on-screen keyboard is shown. Only Android reports it; the web always answers `false`, because mobile
 * browsers resize the viewport instead (design D3).
 */
@Composable
expect fun keyboardVisible(): Boolean

/** Header and full editor bar give way to the text while typing on a narrow screen. */
@Composable
fun hideChrome(): Boolean = LocalCompactLayout.current && keyboardVisible()

/** The uniform scale that makes [contentWidth] fit into [maxWidth]: 1 when it fits or a width is not positive. */
fun fitScale(contentWidth: Int, maxWidth: Int): Float =
    if (contentWidth <= 0 || maxWidth <= 0 || contentWidth <= maxWidth) 1f else maxWidth.toFloat() / contentWidth

/**
 * Measures its single child at its natural width and scales it down uniformly until it fits the available width,
 * instead of letting it wrap or clip. The scale sits in the graphics layer, so taps land where the child is drawn
 * (editor-bar-single-line design D2).
 */
@Composable
fun FitWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints(maxHeight = constraints.maxHeight))
        val scale = fitScale(placeable.width, constraints.maxWidth)
        val width = constraints.constrainWidth((placeable.width * scale).roundToInt())
        val height = constraints.constrainHeight((placeable.height * scale).roundToInt())
        layout(width, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
