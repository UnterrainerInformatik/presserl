package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.media.jpegName
import info.unterrainer.presserl.admin.ui.media.serverImageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageTypesTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun acceptsServerTypes() {
        assertTrue(serverImageType(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)))
        assertTrue(serverImageType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
        assertTrue(serverImageType("RIFF".encodeToByteArray() + bytes(0x24, 0, 0, 0) + "WEBPVP8 ".encodeToByteArray()))
    }

    @Test
    fun refusesOtherTypes() {
        // HEIC: ISO base media box "ftyp" with brand "heic"
        assertFalse(serverImageType(bytes(0, 0, 0, 0x18) + "ftypheic".encodeToByteArray() + bytes(0, 0, 0, 0)))
        assertFalse(serverImageType("GIF89a".encodeToByteArray() + bytes(1, 0, 1, 0)))
        assertFalse(serverImageType("RIFF".encodeToByteArray() + bytes(0x24, 0, 0, 0) + "WAVE".encodeToByteArray()))
        assertFalse(serverImageType(bytes(0xFF, 0xD8)))
        assertFalse(serverImageType("RIFF".encodeToByteArray()))
        assertFalse(serverImageType(ByteArray(0)))
    }

    @Test
    fun namesTheConvertedFile() {
        assertEquals("IMG_1.jpg", jpegName("IMG_1.heic"))
        assertEquals("photo.jpg", jpegName("photo"))
        assertEquals("a.b.jpg", jpegName("a.b.HEIF"))
    }
}
