@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin.ui.media

import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.js.js

private const val ACCEPT = "image/jpeg,image/png,image/webp"

private fun arrayBuffer(file: File): Promise<ArrayBuffer> = js("file.arrayBuffer()")

/**
 * A hidden `<input type="file">`, opened programmatically; `change` delivers the file, `cancel` (closing the
 * dialog without a choice) delivers `null`. The input is removed afterwards.
 */
actual suspend fun pickImageFile(): PickedFile? {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ACCEPT
    input.hidden = true
    document.body!!.appendChild(input)
    try {
        val file = suspendCancellableCoroutine<File?> { continuation ->
            input.addEventListener("change", { if (continuation.isActive) continuation.resume(input.files?.item(0)) })
            input.addEventListener("cancel", { if (continuation.isActive) continuation.resume(null) })
            input.click()
        } ?: return null
        val bytes = Int8Array(arrayBuffer(file).await()).toByteArray()
        return PickedFile(file.name, bytes)
    } finally {
        input.remove()
    }
}
