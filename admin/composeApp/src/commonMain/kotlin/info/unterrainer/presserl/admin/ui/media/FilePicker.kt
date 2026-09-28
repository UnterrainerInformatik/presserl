package info.unterrainer.presserl.admin.ui.media

/** A file the user chose on the device. */
class PickedFile(val name: String, val bytes: ByteArray)

/**
 * Lets the user choose an image file (JPEG, PNG or WebP offered first; the server decides what it accepts);
 * `null` when the user cancels.
 */
expect suspend fun pickImageFile(): PickedFile?
