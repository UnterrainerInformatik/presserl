package info.unterrainer.presserl.admin.ui

import info.unterrainer.presserl.admin.ui.editor.fieldErrorsOf
import kotlinx.coroutines.CancellationException

/** A failure as text for the user: the server's messages if it sent any, otherwise the error's message. */
suspend fun describe(e: Throwable): String =
    fieldErrorsOf(e)?.let { (it.header.values + it.blocks.values + it.general).joinToString(" ") }
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
