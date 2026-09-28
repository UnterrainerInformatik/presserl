package info.unterrainer.presserl.admin.ui.media

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException

/**
 * The `thumbnail` renditions of media, loaded with [load] (it needs the token, so there is no plain image URL) and
 * decoded once per media id. Snapshot state, so previews update when a thumbnail arrives.
 */
class Thumbnails(
    private val decode: (ByteArray) -> ImageBitmap? = ::decodeImage,
    private val load: suspend (mediaId: Long) -> ByteArray,
) {
    private val images = mutableStateMapOf<Long, Result>()

    /** A loaded thumbnail, or [Result.Missing] when it could not be loaded or decoded. */
    sealed interface Result {
        data class Loaded(val image: ImageBitmap) : Result
        data object Missing : Result
    }

    /** The thumbnail if it has been fetched; `null` while it has not. */
    operator fun get(mediaId: Long): Result? = images[mediaId]

    /** Fetches the thumbnail unless it was fetched before. */
    suspend fun fetch(mediaId: Long) {
        if (images.containsKey(mediaId)) return
        images[mediaId] = try {
            decode(load(mediaId))?.let { Result.Loaded(it) } ?: Result.Missing
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.Missing
        }
    }
}

/** A deployment size such as `10M` for people: `10 MB`. */
fun formatMaxSize(value: String): String {
    val trimmed = value.trim()
    val unit = trimmed.lastOrNull()?.uppercaseChar()
    val number = trimmed.dropLast(1)
    return when {
        unit == 'K' && number.isNotEmpty() -> "$number KB"
        unit == 'M' && number.isNotEmpty() -> "$number MB"
        unit == 'G' && number.isNotEmpty() -> "$number GB"
        else -> trimmed
    }
}
