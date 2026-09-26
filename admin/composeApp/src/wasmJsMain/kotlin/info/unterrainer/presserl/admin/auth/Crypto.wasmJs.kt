@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin.auth

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js

private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun uint8ArrayOf(buffer: JsAny): JsAny = js("new Uint8Array(buffer)")

private fun length(array: JsAny): Int = js("array.length")

private fun get(array: JsAny, index: Int): Int = js("array[index]")

private fun set(array: JsAny, index: Int, value: Int): Unit = js("array[index] = value")

private fun subtleSha256(data: JsAny): Promise<JsAny?> = js("globalThis.crypto.subtle.digest('SHA-256', data)")

private fun fillRandom(array: JsAny): Unit = js("globalThis.crypto.getRandomValues(array)")

private fun ByteArray.toUint8Array(): JsAny {
    val array = newUint8Array(size)
    forEachIndexed { index, byte -> set(array, index, byte.toInt() and 0xff) }
    return array
}

private fun JsAny.toByteArray(): ByteArray = ByteArray(length(this)) { get(this, it).toByte() }

actual suspend fun sha256(data: ByteArray): ByteArray =
    uint8ArrayOf(subtleSha256(data.toUint8Array()).await<JsAny?>()!!).toByteArray()

actual fun secureRandomBytes(size: Int): ByteArray {
    val array = newUint8Array(size)
    fillRandom(array)
    return array.toByteArray()
}
