package info.unterrainer.presserl.admin.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.ui.editor.UploadError
import info.unterrainer.presserl.admin.ui.editor.uploadErrorOf
import kotlinx.coroutines.CancellationException

/** Signature of `ApiClient.uploadMedia`. */
typealias MediaUpload = suspend (bytes: ByteArray, fileName: String, description: String?, tags: List<String>) -> MediaDto

/**
 * The upload dialog of the images view: the chosen [files], one shared description and set of tags, uploaded one after
 * another by [start]. A failure does not stop the remaining files; failed files can be [retry]d or [remove]d. Every
 * uploaded media goes to [onUploaded] (the grid shows it at the top).
 */
class MediaUploadModel(
    files: List<PickableFile>,
    private val upload: MediaUpload,
    private val onUploaded: (MediaDto) -> Unit,
) {
    /** Where one file stands. */
    sealed interface State {
        data object Waiting : State
        data object Uploading : State
        data class Done(val media: MediaDto) : State
        data class Failed(val error: UploadError) : State
    }

    /** One chosen file and its [state]. */
    class Entry(val file: PickableFile) {
        var state by mutableStateOf<State>(State.Waiting)
            internal set
    }

    var entries by mutableStateOf(files.map(::Entry))
        private set
    val tags = TagChips()
    var description by mutableStateOf("")

    /** Whether [start] was called; description and tags are fixed from then on. */
    var started by mutableStateOf(false)
        private set

    /** Whether files are being uploaded right now. */
    var running by mutableStateOf(false)
        private set

    /** Whether closing the dialog would drop files not uploaded yet (waiting, uploading or failed). */
    val hasPending: Boolean get() = entries.any { it.state !is State.Done }

    /** Whether every file is uploaded. */
    val allDone: Boolean get() = entries.isNotEmpty() && entries.all { it.state is State.Done }

    /**
     * Uploads every waiting file in order, with the description and the tags (text still typed in the tag input is
     * added first); nothing when that text is not a valid tag or an upload is already running.
     */
    suspend fun start() {
        if (running) return
        if (!started && tags.text.isNotBlank() && !tags.add()) return
        started = true
        running = true
        try {
            while (true) {
                val entry = entries.firstOrNull { it.state == State.Waiting } ?: break
                send(entry)
            }
        } finally {
            running = false
        }
    }

    private suspend fun send(entry: Entry) {
        entry.state = State.Uploading
        entry.state = try {
            val media = upload(entry.file.read(), entry.file.name, description.trim().ifEmpty { null }, tags.tags)
            onUploaded(media)
            State.Done(media)
        } catch (e: CancellationException) {
            entry.state = State.Waiting
            throw e
        } catch (e: Throwable) {
            State.Failed(uploadErrorOf(e))
        }
    }

    /** Uploads a failed file again (after the others still waiting). */
    suspend fun retry(entry: Entry) {
        if (entry.state !is State.Failed) return
        entry.state = State.Waiting
        start()
    }

    /** Drops a file that is not being uploaded and not uploaded yet. */
    fun remove(entry: Entry) {
        if (entry.state == State.Uploading || entry.state is State.Done) return
        entries = entries - entry
    }
}

/**
 * Lets the user choose files with [pick] and returns the upload dialog's model for them; `null` (nothing opens,
 * nothing is uploaded) when the picker is cancelled.
 */
suspend fun chooseForUpload(
    pick: suspend () -> List<PickableFile>?,
    upload: MediaUpload,
    onUploaded: (MediaDto) -> Unit,
): MediaUploadModel? = pick()?.takeIf { it.isNotEmpty() }?.let { MediaUploadModel(it, upload, onUploaded) }
