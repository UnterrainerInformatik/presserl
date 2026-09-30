package info.unterrainer.presserl.admin.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.PopupPositionProvider

/**
 * A non-focusable popup for a field explanation. [onKeyEvent] receives key presses while it is open where the
 * platform routes them to popups (the web); elsewhere the focused help button handles them.
 */
@Composable
internal expect fun HelpPopup(
    positionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable () -> Unit,
)
