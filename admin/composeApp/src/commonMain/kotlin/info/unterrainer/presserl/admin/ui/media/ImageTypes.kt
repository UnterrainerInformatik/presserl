package info.unterrainer.presserl.admin.ui.media

/** The image types the server accepts. */
val SERVER_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/** Whether [bytes] start like a JPEG, PNG or WebP file, the types the server accepts. */
fun serverImageType(bytes: ByteArray): Boolean {
    fun startsWith(offset: Int, prefix: ByteArray) =
        bytes.size >= offset + prefix.size && prefix.indices.all { bytes[offset + it] == prefix[it] }
    val jpeg = startsWith(0, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
    val webp = startsWith(0, "RIFF".encodeToByteArray()) && startsWith(8, "WEBP".encodeToByteArray())
    return jpeg || webp || startsWith(0, PNG_SIGNATURE)
}

/** [name] with its extension replaced by `.jpg`, or `.jpg` appended when it has none. */
fun jpegName(name: String): String {
    val dot = name.lastIndexOf('.')
    return (if (dot > 0) name.substring(0, dot) else name) + ".jpg"
}
