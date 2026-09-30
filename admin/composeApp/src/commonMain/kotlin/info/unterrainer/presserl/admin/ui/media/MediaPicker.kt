package info.unterrainer.presserl.admin.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.MediaListItemDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.media_picker_close
import info.unterrainer.presserl.admin.resources.media_picker_none
import info.unterrainer.presserl.admin.resources.media_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * The media picker of the editor: the newspaper's images with the search of the images view, starting without
 * filters every time it opens; no upload and no camera. Selecting a tile hands its media to [onPick]; the close button,
 * Escape and a click outside call [onDismiss].
 */
@Composable
fun MediaPickerDialog(api: ApiClient, thumbnails: Thumbnails, onPick: (MediaListItemDto) -> Unit, onDismiss: () -> Unit) {
    // Its own grid, dropped on close, so the images view's grid and filters stay untouched
    val grid = remember { mediaGridOf(api) }
    val search = rememberMediaSearch(grid)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 1100.dp).fillMaxHeight(0.9f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.media_picker_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.media_picker_close)) }
                }
                MediaSearchBar(search) { api.mediaTags(it) }
                Box(Modifier.weight(1f)) {
                    MediaGridList(search, thumbnails, stringResource(Res.string.media_picker_none), onClick = onPick)
                }
            }
        }
    }
}
