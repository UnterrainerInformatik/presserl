package info.unterrainer.presserl.admin.ui.media

import androidx.compose.runtime.Composable
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.upload_damaged
import info.unterrainer.presserl.admin.resources.upload_failed
import info.unterrainer.presserl.admin.resources.upload_too_large
import info.unterrainer.presserl.admin.resources.upload_too_large_unknown
import info.unterrainer.presserl.admin.resources.upload_unreachable
import info.unterrainer.presserl.admin.resources.upload_unsupported
import io.ktor.client.plugins.ResponseException
import org.jetbrains.compose.resources.stringResource

/** Why an upload was refused, as the images view explains it. */
sealed interface UploadError {
    /** `413`: above the newspaper's `media.max-size`. */
    data object TooLarge : UploadError

    /** `415`: not a JPEG, PNG or WebP image (HEIC, GIF, other files). */
    data object Unsupported : UploadError

    /** `400`: damaged, or too many pixels. */
    data object Damaged : UploadError

    /** `503`, other server errors and network failures. */
    data object Unreachable : UploadError

    /** Any other refusal, with the server's text. */
    data class Other(val message: String) : UploadError
}

/** Maps a failed upload to what the images view shows. */
fun uploadErrorOf(e: Throwable): UploadError {
    if (e !is ResponseException) return UploadError.Unreachable
    return when (val status = e.response.status.value) {
        413 -> UploadError.TooLarge
        415 -> UploadError.Unsupported
        400 -> UploadError.Damaged
        in 500..599 -> UploadError.Unreachable
        else -> UploadError.Other("$status ${e.response.status.description}")
    }
}

/**
 * Why an upload failed, in the words the images view uses; [maxUploadSize] is the newspaper's
 * `media.max-size`, named when the file is too large.
 */
@Composable
fun uploadErrorText(error: UploadError, maxUploadSize: String?): String = when (error) {
    UploadError.TooLarge -> maxUploadSize?.let { stringResource(Res.string.upload_too_large, formatMaxSize(it)) }
        ?: stringResource(Res.string.upload_too_large_unknown)
    UploadError.Unsupported -> stringResource(Res.string.upload_unsupported)
    UploadError.Damaged -> stringResource(Res.string.upload_damaged)
    UploadError.Unreachable -> stringResource(Res.string.upload_unreachable)
    is UploadError.Other -> stringResource(Res.string.upload_failed, error.message)
}
