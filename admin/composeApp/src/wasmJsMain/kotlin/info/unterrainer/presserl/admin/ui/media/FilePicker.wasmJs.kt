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
 * For [camera] any image type with `capture="environment"`: mobile browsers open the rear camera, desktop browsers
 * ignore `capture` and show the picker. Otherwise several files may be chosen. The browser `File`s stay handles until
 * [PickableFile.read].
 */
actual suspend fun pickImageFiles(camera: Boolean): List<PickableFile>? {
    val files = pick { input ->
        if (camera) {
            input.accept = "image/*"
            input.setAttribute("capture", "environment")
        } else {
            input.accept = ACCEPT
            input.multiple = true
        }
    } ?: return null
    return files.map { file -> PickableFile(file.name, file.size.toDouble().toLong()) { read(file) } }.ifEmpty { null }
}

private suspend fun read(file: File): ByteArray = Int8Array(arrayBuffer(file).await()).toByteArray()

/**
 * A hidden `<input type="file">` set up by [configure] and opened programmatically; `change` delivers the files,
 * `cancel` (closing the dialog without a choice) delivers `null`. The input is removed afterwards.
 */
private suspend fun pick(configure: (HTMLInputElement) -> Unit): List<File>? {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.hidden = true
    configure(input)
    document.body!!.appendChild(input)
    try {
        return suspendCancellableCoroutine { continuation ->
            input.addEventListener("change", {
                val files = input.files
                val chosen = if (files == null) emptyList() else (0 until files.length).mapNotNull { files.item(it) }
                if (continuation.isActive) continuation.resume(chosen)
            })
            input.addEventListener("cancel", { if (continuation.isActive) continuation.resume(null) })
            input.click()
        }
    } finally {
        input.remove()
    }
}
