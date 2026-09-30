package info.unterrainer.presserl.admin.ui

import androidx.compose.runtime.Composable

/**
 * Handles the system back action (Android) with [onBack] while [enabled]; the innermost enabled handler wins, and
 * without one the app is left. Does nothing on the web, where the browser's back button leaves the app as before.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** The routes after the system back action on [stack], or `null` to leave the app (on the [start] view). */
fun stackAfterBack(stack: List<Route>, start: NavEntry?): List<Route>? {
    val top = stack.lastOrNull() ?: return null
    if (stack.size > 1) return stack.dropLast(1)
    val home = startStack(start)
    val onStartView = home.firstOrNull()?.let { it == top || (it is Route.ArticleList && top is Route.ArticleList) } ?: false
    return if (onStartView || home.isEmpty()) null else home
}

/** The routes of the view opened after login; empty for the "no writing role" notice. */
fun startStack(start: NavEntry?): List<Route> = when (start) {
    NavEntry.ARTICLES -> listOf(Route.ArticleList(ListTab.MINE))
    NavEntry.IMAGES -> listOf(Route.Media)
    else -> emptyList()
}
