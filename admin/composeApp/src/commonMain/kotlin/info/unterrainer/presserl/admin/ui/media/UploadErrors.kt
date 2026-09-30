package info.unterrainer.presserl.admin.ui.media

import androidx.compose.runtime.Composable
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.upload_damaged
import info.unterrainer.presserl.admin.resources.upload_failed
import info.unterrainer.presserl.admin.resources.upload_too_large
import info.unterrainer.presserl.admin.resources.upload_too_large_unknown
import info.unterrainer.presserl.admin.resources.upload_unreachable
import info.unterrainer.presserl.admin.resources.upload_unsupported
import info.unterrainer.presserl.admin.ui.editor.UploadError
import org.jetbrains.compose.resources.stringResource

/**
 * Why an upload failed, in the words the article editor and the images view use; [maxUploadSize] is the newspaper's
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
