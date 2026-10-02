package info.unterrainer.presserl.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

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
