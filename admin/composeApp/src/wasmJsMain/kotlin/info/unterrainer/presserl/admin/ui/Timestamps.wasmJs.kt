@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin.ui

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

private fun toLocaleString(iso: String): String =
    js("new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })")

actual fun formatTimestamp(iso: String): String = toLocaleString(iso)
