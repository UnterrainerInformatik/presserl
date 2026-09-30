package info.unterrainer.presserl.admin.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import info.unterrainer.presserl.admin.api.ApiErrorDto
import info.unterrainer.presserl.admin.api.MediaDetailsRequest
import info.unterrainer.presserl.admin.api.MediaDto
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

/** Why saving description and tags failed, as the media detail explains it; the unsaved input is kept. */
sealed interface MediaDetailsError {
    /** `400`: a value the server refuses; [field] is `description`, `tags` or `tags[i]` when known. */
    data class Invalid(val field: String?) : MediaDetailsError

    /** `403`. */
    data object Forbidden : MediaDetailsError

    /** `503`, other server errors and network failures. */
    data object Unreachable : MediaDetailsError

    /** Any other refusal, with the status. */
    data class Other(val message: String) : MediaDetailsError
}

/** Maps a failed `PUT /api/media/{id}/details`, reading the field of a `400`. */
suspend fun mediaDetailsErrorOf(e: Throwable): MediaDetailsError {
    if (e !is ResponseException) return MediaDetailsError.Unreachable
    return when (val status = e.response.status.value) {
        400 -> MediaDetailsError.Invalid(
            try {
                e.response.body<ApiErrorDto>().errors.firstOrNull()?.field
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                null
            },
        )
        403 -> MediaDetailsError.Forbidden
        in 500..599 -> MediaDetailsError.Unreachable
        else -> MediaDetailsError.Other("$status ${e.response.status.description}")
    }
}

/**
 * Description and tags of the media in the detail: shown from [media], changed after [edit] and stored with [save]
 * (`PUT /api/media/{id}/details`). [dirty] while the input differs from what is saved.
 */
class MediaDetailsModel(initial: MediaDto, private val store: suspend (MediaDetailsRequest) -> MediaDto) {
    var media by mutableStateOf(initial)
        private set
    var editing by mutableStateOf(false)
        private set
    var description by mutableStateOf("")
    var tags by mutableStateOf(TagChips())
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<MediaDetailsError?>(null)
        private set

    /** Whether the input differs from the saved values (typed but not added tag text included). */
    val dirty: Boolean
        get() = editing && (description.trim() != (media.description ?: "") || tags.tags != media.tags || tags.text.isNotBlank())

    /** Starts editing with the saved values. */
    fun edit() {
        description = media.description ?: ""
        tags = TagChips(media.tags)
        error = null
        editing = true
    }

    /** Drops the input. */
    fun cancel() {
        editing = false
        error = null
    }

    /**
     * Saves the input (text still typed in the tag input is added first); on success shows the stored values and
     * returns them, else keeps the input and sets [error].
     */
    suspend fun save(): MediaDto? {
        if (saving || !editing) return null
        if (tags.text.isNotBlank() && !tags.add()) return null
        saving = true
        error = null
        try {
            val saved = store(MediaDetailsRequest(description.trim().ifEmpty { null }, tags.tags))
            media = saved
            editing = false
            return saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = mediaDetailsErrorOf(e)
            return null
        } finally {
            saving = false
        }
    }
}

/** Longest description the server accepts. */
const val MAX_DESCRIPTION_LENGTH = 1000
