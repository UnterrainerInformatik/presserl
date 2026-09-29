package info.unterrainer.presserl.admin.ui.spell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.SpellMatchDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource

/** A finding in a field's text: [start] until [end] (UTF-16 indices) is [word]. */
data class SpellFinding(val start: Int, val end: Int, val word: String, val message: String, val replacements: List<String>)

/**
 * Spell check of one editor or form (design D5). Fields are identified by a key (`headline`, `block:<id>`, …).
 * [changed] checks a field [debounceMillis] after its last change, [checkNow] at once (on open). Findings are shown
 * only for the text they were computed for, without the words ignored in this instance. At most [parallel] requests
 * run at a time; answers are cached per text. After a failure a field is asked again at its next change once
 * [retryMillis] have passed; [notice] is `true` from the first failure until the next answer, and only once per
 * instance. Nothing is sent when [enabled] is `false`. [check] answers `null` when the check is unavailable; timing
 * uses [scope]'s dispatcher and [clock] (milliseconds), so tests control both with virtual time.
 */
class SpellChecker(
    private val scope: CoroutineScope,
    val enabled: Boolean,
    private val check: suspend (String) -> List<SpellMatchDto>?,
    private val clock: () -> Long,
    private val debounceMillis: Long = 1_000,
    private val retryMillis: Long = 60_000,
    private val cacheSize: Int = 100,
    parallel: Int = 2,
) {
    private data class Checked(val text: String, val matches: List<SpellMatchDto>)

    private val results = mutableStateMapOf<String, Checked>()
    private val ignored = mutableStateMapOf<String, Unit>()
    private val latest = mutableMapOf<String, String>()
    private val timers = mutableMapOf<String, Job>()
    private val failedAt = mutableMapOf<String, Long>()
    private val cache = LinkedHashMap<String, List<SpellMatchDto>>()
    private val permits = Semaphore(parallel)
    private var noticeShown = false

    /** The check failed and has not answered since; shown once per instance. */
    var notice by mutableStateOf(false)
        private set

    /** The field [key] now holds [text]; it is checked once the text has not changed for [debounceMillis]. */
    fun changed(key: String, text: String) = schedule(key, text, debounceMillis)

    /** Checks [text] of [key] without waiting, e.g. when the editor opens. */
    fun checkNow(key: String, text: String) = schedule(key, text, 0)

    /** The findings to mark in [key] while it shows [text]; empty while that text is not checked yet. */
    fun findings(key: String, text: String): List<SpellFinding> {
        val checked = results[key]?.takeIf { it.text == text } ?: return emptyList()
        return checked.matches.mapNotNull { match ->
            val end = match.offset + match.length
            if (match.offset < 0 || match.length <= 0 || end > text.length) return@mapNotNull null
            val word = text.substring(match.offset, end)
            if (word in ignored) null else SpellFinding(match.offset, end, word, match.message, match.replacements)
        }
    }

    /** Hides every finding on [word] (case-sensitive) in all fields of this instance. */
    fun ignore(word: String) {
        ignored[word] = Unit
    }

    private fun schedule(key: String, text: String, delayMillis: Long) {
        if (!enabled) return
        if (latest[key] == text && (results[key]?.text == text || timers[key]?.isActive == true)) return
        latest[key] = text
        timers[key]?.cancel()
        timers[key] = scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            // The request runs outside the timer, so a new change cannot cancel an answer on its way
            scope.launch { run(key, text) }
        }
    }

    private suspend fun run(key: String, text: String) {
        if (text.isBlank()) return store(key, text, emptyList())
        cached(text)?.let { return store(key, text, it) }
        val failed = failedAt[key]
        if (failed != null && clock() - failed < retryMillis) return
        val matches = permits.withPermit {
            // Another field may have asked for the same text meanwhile
            cached(text) ?: check(text)
        }
        if (matches == null) {
            failedAt[key] = clock()
            if (!noticeShown) {
                noticeShown = true
                notice = true
            }
            return
        }
        failedAt.remove(key)
        notice = false
        cache.remove(text)
        cache[text] = matches
        if (cache.size > cacheSize) cache.remove(cache.keys.first())
        store(key, text, matches)
    }

    private fun cached(text: String): List<SpellMatchDto>? = cache.remove(text)?.also { cache[text] = it }

    private fun store(key: String, text: String, matches: List<SpellMatchDto>) {
        // An answer for text that has changed meanwhile is of no use
        if (latest[key] == text) results[key] = Checked(text, matches)
    }
}

/** A [SpellChecker] asking the backend through [api]; with [enabled] `false` it never sends anything. */
fun spellChecker(scope: CoroutineScope, enabled: Boolean, api: ApiClient): SpellChecker {
    val start = TimeSource.Monotonic.markNow()
    return SpellChecker(scope, enabled, check = api::spellCheck, clock = { start.elapsedNow().inWholeMilliseconds })
}
