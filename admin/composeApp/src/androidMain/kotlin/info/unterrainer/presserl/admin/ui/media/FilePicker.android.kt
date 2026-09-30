package info.unterrainer.presserl.admin.ui.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * The activity's result launchers for the photo picker and the camera, registered by [installImagePicker] before the
 * activity starts; [pickImageFiles] suspends until the chosen or taken images come back.
 */
private object ImagePicker {
    lateinit var context: Context
    lateinit var gallery: ActivityResultLauncher<PickVisualMediaRequest>
    lateinit var camera: ActivityResultLauncher<Uri>
    var pending: CancellableContinuation<List<PickableFile>?>? = null
    var photo: File? = null

    fun deliver(files: List<PickableFile>?) {
        pending?.takeIf { it.isActive }?.resume(files?.ifEmpty { null })
        pending = null
    }
}

/** Registers the launchers on [activity]; call in `onCreate`. */
fun installImagePicker(activity: ComponentActivity) {
    ImagePicker.context = activity.applicationContext
    ImagePicker.gallery = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        ImagePicker.deliver(uris.map { contentFile(activity.applicationContext, it) })
    }
    ImagePicker.camera = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val photo = ImagePicker.photo
        ImagePicker.deliver(if (taken && photo != null && photo.length() > 0) listOf(photoFile(photo)) else null)
    }
}

/**
 * The system photo picker (no permission to read all photos), several images at once; with [camera] the camera app
 * takes one photo into the cache.
 */
actual suspend fun pickImageFiles(camera: Boolean): List<PickableFile>? = suspendCancellableCoroutine { continuation ->
    ImagePicker.deliver(null)
    ImagePicker.pending = continuation
    if (camera) {
        val directory = File(ImagePicker.context.cacheDir, "photos").apply { mkdirs() }
        // earlier photos are uploaded or dropped by now
        directory.listFiles()?.forEach(File::delete)
        val photo = File(directory, "photo-${System.currentTimeMillis()}.jpg").also { ImagePicker.photo = it }
        val uri = FileProvider.getUriForFile(ImagePicker.context, ImagePicker.context.packageName + ".photos", photo)
        ImagePicker.camera.launch(uri)
    } else {
        ImagePicker.gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    continuation.invokeOnCancellation { ImagePicker.pending = null }
}

private fun contentFile(context: Context, uri: Uri): PickableFile {
    var name = uri.lastPathSegment ?: "image"
    var size = 0L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(0)?.let { name = it }
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    return PickableFile(name, size) {
        withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
    }
}

private fun photoFile(file: File): PickableFile =
    PickableFile(file.name, file.length()) { withContext(Dispatchers.IO) { file.readBytes() } }
