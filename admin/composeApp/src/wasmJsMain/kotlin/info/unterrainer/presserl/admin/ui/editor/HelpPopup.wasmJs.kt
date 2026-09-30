package info.unterrainer.presserl.admin.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
internal actual fun HelpPopup(
    positionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable () -> Unit,
) = Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = false),
    onKeyEvent = onKeyEvent,
    content = content,
)
