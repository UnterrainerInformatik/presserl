package info.unterrainer.presserl.admin.ui.editor

import info.unterrainer.presserl.admin.api.ApiErrorDto
import info.unterrainer.presserl.admin.api.ArticleContent
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.api.json
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SaveState {
    data object Saved : SaveState
    data object Pending : SaveState
    data object Saving : SaveState
    data class Failed(val retryInSeconds: Int) : SaveState
    data class Invalid(val errors: FieldErrors) : SaveState
    data object Conflict : SaveState
}

/** Server messages sorted to the editor's fields; [blocks] is keyed by block index. */
data class FieldErrors(
    val header: Map<HeaderField, String> = emptyMap(),
    val blocks: Map<Int, String> = emptyMap(),
    val general: List<String> = emptyList(),
)

private val BLOCK_PATH = Regex("""^body\.blocks\[(\d+)]""")

/** Maps `FieldErrorDto.field` paths (`headline`, `body.blocks[2].content[0].text`) to fields. */
fun fieldErrors(errors: List<FieldErrorDto>): FieldErrors {
    val header = mutableMapOf<HeaderField, String>()
    val blocks = mutableMapOf<Int, String>()
    val general = mutableListOf<String>()
    errors.forEach { error ->
        val field = error.field
        val headerField = HeaderField.entries.firstOrNull { it.name.lowercase() == field }
        val block = field?.let { BLOCK_PATH.find(it) }?.groupValues?.get(1)?.toIntOrNull()
        when {
            headerField != null -> header.getOrPut(headerField) { error.message }
            block != null -> blocks.getOrPut(block) { error.message }
            else -> general += error.message
        }
    }
    return FieldErrors(header, blocks, general)
}

/** Field errors of a refused request, or `null` if [e] is no `4xx` answer with an error body. */
suspend fun fieldErrorsOf(e: Throwable): FieldErrors? {
    if (e !is ResponseException || e.response.status.value !in 400..499) return null
    return try {
        fieldErrors(json.decodeFromString<ApiErrorDto>(e.response.bodyAsText()).errors)
    } catch (_: Exception) {
        null
    }
}

/**
 * Saves the editor content with the article version (design D5): [debounceMillis] after the last
 * change, at most one request in flight, [flush] on demand. `400` (and any other refusal with an
 * error body) → [SaveState.Invalid] until the next change; `409` → [SaveState.Conflict], nothing
 * is saved any more; network errors and `5xx` → [SaveState.Failed] and a retry with backoff
 * 2, 4, 8 … 30 s. Timing uses [scope]'s dispatcher, so tests control it with virtual time.
 */
class Autosaver(
    private val scope: CoroutineScope,
    saved: ArticleContent,
    version: Long,
    private val save: suspend (content: ArticleContent, version: Long) -> ArticleDto,
    private val onSaved: (ArticleDto) -> Unit = {},
    private val debounceMillis: Long = 1_500,
) {
    private val _state = MutableStateFlow<SaveState>(SaveState.Saved)
    val state: StateFlow<SaveState> = _state.asStateFlow()

    private var current = saved
    private var saved = saved
    private var version = version
    /** The content the server refused last, with its answer. */
    private var rejected: Pair<ArticleContent, SaveState.Invalid>? = null
    private var failures = 0
    private var timer: Job? = null
    private val inFlight = Mutex()

    /** The editor content changed to [content]. */
    fun changed(content: ArticleContent) {
        if (_state.value == SaveState.Conflict) return
        current = content
        schedule(debounceMillis)
        if (!inFlight.isLocked) _state.value = settled()
    }

    /**
     * An action (publish, take offline) returned [version]. Versions only grow, so a save answered
     * after the action cannot set an older one.
     */
    fun versionChanged(version: Long) {
        this.version = maxOf(this.version, version)
    }

    /** Saves pending changes now; `true` if everything is saved afterwards. */
    suspend fun flush(): Boolean {
        timer?.cancel()
        failures = 0
        saveNow(untilSaved = true)
        return _state.value == SaveState.Saved
    }

    private fun schedule(delayMillis: Long) {
        timer?.cancel()
        timer = scope.launch {
            delay(delayMillis)
            // The request runs outside the timer, so a new change cannot cancel it
            scope.launch { saveNow(untilSaved = false) }
        }
    }

    private fun needsSave() = _state.value != SaveState.Conflict && current != saved && current != rejected?.first

    private fun settled(): SaveState = when {
        _state.value == SaveState.Conflict -> SaveState.Conflict
        current == saved -> SaveState.Saved
        current == rejected?.first -> rejected!!.second
        else -> SaveState.Pending
    }

    /** Saves once, or until nothing is left if [untilSaved]; changes made meanwhile wait for their timer otherwise. */
    private suspend fun saveNow(untilSaved: Boolean) = inFlight.withLock {
        do {
            if (!needsSave()) break
            val content = current
            _state.value = SaveState.Saving
            try {
                val article = save(content, version)
                versionChanged(article.version)
                saved = content
                failures = 0
                onSaved(article)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Browser fetch failures surface as kotlin.Error, not Exception
                _state.value = failed(e, content)
                return@withLock
            }
        } while (untilSaved)
        _state.value = settled()
    }

    private suspend fun failed(e: Throwable, content: ArticleContent): SaveState {
        if (e is ResponseException && e.response.status == HttpStatusCode.Conflict) {
            timer?.cancel()
            return SaveState.Conflict
        }
        fieldErrorsOf(e)?.let { errors ->
            // Sent again only once the content changes
            rejected = content to SaveState.Invalid(errors)
            return settled()
        }
        failures++
        val seconds = minOf(1 shl minOf(failures, 5), MAX_BACKOFF_SECONDS)
        schedule(seconds * 1_000L)
        return SaveState.Failed(seconds)
    }

    private companion object {
        const val MAX_BACKOFF_SECONDS = 30
    }
}
