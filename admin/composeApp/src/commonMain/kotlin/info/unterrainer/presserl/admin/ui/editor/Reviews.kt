package info.unterrainer.presserl.admin.ui.editor

import info.unterrainer.presserl.admin.api.ApiErrorDto
import info.unterrainer.presserl.admin.api.ReviewDto
import info.unterrainer.presserl.admin.api.json
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText

/** Longest rejection note the server accepts, in code points. */
const val REJECT_NOTE_MAX = 1000

private const val NOTE_FIELD = "note"

/** The newest review if it is a rejection and no submission is pending: why the article came back. */
fun rejectionToShow(reviews: List<ReviewDto>, pendingLevel: String?): ReviewDto? =
    reviews.firstOrNull()?.takeIf { it.decision == "REJECTED" && pendingLevel == null }

/** A reject dialog may only be confirmed with a note that is not blank. */
fun canConfirmReject(note: String): Boolean = note.isNotBlank()

/** Input for the note field, cut at [REJECT_NOTE_MAX] code points (line feeds allowed). */
fun limitNote(note: String): String {
    var count = 0
    var end = 0
    while (end < note.length && count < REJECT_NOTE_MAX) {
        end += if (note[end].isHighSurrogate() && end + 1 < note.length) 2 else 1
        count++
    }
    return note.substring(0, end)
}

/** The server's message about `note` in a refused reject, or `null` if [e] is no such answer. */
suspend fun noteErrorOf(e: Throwable): String? {
    if (e !is ResponseException || e.response.status.value != 400) return null
    return try {
        json.decodeFromString<ApiErrorDto>(e.response.bodyAsText()).errors.firstOrNull { it.field == NOTE_FIELD }?.message
    } catch (_: Exception) {
        null
    }
}
