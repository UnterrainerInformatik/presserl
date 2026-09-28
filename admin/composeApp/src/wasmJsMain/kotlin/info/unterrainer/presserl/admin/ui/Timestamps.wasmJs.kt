@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin.ui

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

private fun toLocaleString(iso: String): String =
    js("new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })")

actual fun formatTimestamp(iso: String): String = toLocaleString(iso)

/** Local midnight of the date, so no time zone shifts it to the neighbouring day. */
private fun toLocaleDate(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(undefined, { dateStyle: 'long' })")

actual fun formatDate(iso: String): String {
    val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
    return if (parts.size == 3) toLocaleDate(parts[0], parts[1], parts[2]) else iso
}
