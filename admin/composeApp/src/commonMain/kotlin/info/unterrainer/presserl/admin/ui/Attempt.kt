package info.unterrainer.presserl.admin.ui

import info.unterrainer.presserl.admin.api.ApiErrorDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.editor.fieldErrorsOf
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/** A failure as text for the user: the server's messages if it sent any, otherwise the error's message. */
suspend fun describe(e: Throwable): String =
    fieldErrorsOf(e)?.let { (listOfNotNull(it.section) + it.header.values + it.blocks.values + it.general).joinToString(" ") }
        ?: e.message ?: e.toString()

/** Runs [block]; a failure goes to [onError] as text, cancellation passes through. */
suspend fun <T> attempt(onError: (String) -> Unit, block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    // Browser fetch failures surface as kotlin.Error, not Exception
    onError(describe(e))
    null
}

/** The errors of a refused request (`4xx` with an error body); `null` for any other failure. */
suspend fun apiErrorsOf(e: Throwable): List<FieldErrorDto>? =
    if (e is ResponseException && e.response.status.value in 400..499) {
        try {
            json.decodeFromString<ApiErrorDto>(e.response.bodyAsText()).errors
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
