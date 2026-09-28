package info.unterrainer.presserl.admin.ui.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

/**
 * Decodes JPEG or PNG bytes (a rendition from the server) for display on the canvas; `null` when they
 * cannot be decoded. The canvas UI cannot show an `<img>`, so images are always decoded here.
 */
fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    bytes.decodeToImageBitmap()
} catch (e: Throwable) {
    null
}
