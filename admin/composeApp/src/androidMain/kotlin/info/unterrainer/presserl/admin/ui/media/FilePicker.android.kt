package info.unterrainer.presserl.admin.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import info.unterrainer.presserl.admin.ui.logWarning
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
    // HEIC/HEIF and other types the server refuses become JPEG on the device (design D7)
    if (context.contentResolver.getType(uri) !in SERVER_IMAGE_MIME_TYPES) name = jpegName(name)
    return PickableFile(name, size) {
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (serverImageType(bytes)) bytes else toJpeg(context, uri, bytes)
        }
    }
}

/** The server's limits for an image (per side and in total); larger ones are not decoded, the server refuses them. */
private const val MAX_SIDE = 20_000
private const val MAX_PIXELS = 50_000_000L

/**
 * The image at [uri] decoded with its orientation applied and compressed as JPEG; [original] when the device cannot
 * decode it or it exceeds the server's limits, so the server answers with the usual "not a supported image type".
 */
private fun toJpeg(context: Context, uri: Uri, original: ByteArray): ByteArray {
    val bitmap = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                if (!withinLimits(info.size.width, info.size.height)) throw IOException("image too large: ${info.size}")
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
            if (withinLimits(bounds.outWidth, bounds.outHeight)) BitmapFactory.decodeByteArray(original, 0, original.size) else null
        }
    } catch (e: IOException) {
        logWarning("Cannot convert $uri to JPEG, uploading it unchanged", e)
        null
    } catch (e: OutOfMemoryError) {
        logWarning("Not enough memory to convert $uri to JPEG, uploading it unchanged", e)
        null
    } ?: return original
    return try {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}

private fun withinLimits(width: Int, height: Int): Boolean =
    width in 1..MAX_SIDE && height in 1..MAX_SIDE && width.toLong() * height <= MAX_PIXELS

private const val JPEG_QUALITY = 90

private fun photoFile(file: File): PickableFile =
    PickableFile(file.name, file.length()) { withContext(Dispatchers.IO) { file.readBytes() } }
