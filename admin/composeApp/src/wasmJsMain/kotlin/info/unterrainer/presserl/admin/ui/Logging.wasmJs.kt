@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin.ui

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

private fun consoleWarn(message: String): Unit = js("console.warn(message)")

actual fun logWarning(message: String, cause: Throwable?) {
    consoleWarn(if (cause == null) message else "$message\n${cause.stackTraceToString()}")
}
