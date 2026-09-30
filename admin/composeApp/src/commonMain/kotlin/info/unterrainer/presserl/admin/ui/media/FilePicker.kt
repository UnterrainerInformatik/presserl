package info.unterrainer.presserl.admin.ui.media

/** A file the user chose on the device. */
class PickedFile(val name: String, val bytes: ByteArray)

/**
 * Lets the user choose an image file (JPEG, PNG or WebP offered first; the server decides what it accepts);
 * `null` when the user cancels.
 */
expect suspend fun pickImageFile(): PickedFile?

/** A file the user chose on the device; its bytes are read only by [read], right before they are needed. */
class PickableFile(val name: String, val size: Long, private val reader: suspend () -> ByteArray) {
    suspend fun read(): ByteArray = reader()
}

/**
 * Lets the user choose several image files (JPEG, PNG and WebP offered), or with [camera] take a new photo with the
 * device's rear camera where the browser supports it (the file picker otherwise); `null` when the user cancels or
 * chooses nothing.
 */
expect suspend fun pickImageFiles(camera: Boolean): List<PickableFile>?
