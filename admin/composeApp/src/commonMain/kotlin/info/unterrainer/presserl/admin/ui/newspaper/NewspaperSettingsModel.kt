package info.unterrainer.presserl.admin.ui.newspaper

import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.ui.apiErrorsOf
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val READER_TEXT_SIZE = "reader.text-size"
const val SPELL_CHECK_HELP = "spell-check.help"

/** The reader text sizes in order with their body size in px. */
val TEXT_SIZES = listOf("s" to 17, "m" to 19, "l" to 22, "xl" to 26)

/** The spell-check help levels, from most to least help. */
val SPELL_CHECK_HELP_LEVELS = listOf("suggestions", "messages", "marks")

/**
 * State of the newspaper settings screen. [textSize] is the newspaper's override of `reader.text-size`, `null` for the
 * installation default; [effectiveTextSize] the size the reader uses. [spellCheckHelp] and [effectiveSpellCheckHelp]
 * are the same for `spell-check.help`.
 */
data class NewspaperSettingsState(
    val loading: Boolean = true,
    val textSize: String? = null,
    val effectiveTextSize: String? = null,
    val spellCheckHelp: String? = null,
    val effectiveSpellCheckHelp: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** The newspaper's override of the writable setting [key], `null` for the installation default. */
    fun override(key: String): String? = when (key) {
        READER_TEXT_SIZE -> textSize
        SPELL_CHECK_HELP -> spellCheckHelp
        else -> error("not a writable setting: $key")
    }

    fun withOverride(key: String, value: String?): NewspaperSettingsState = when (key) {
        READER_TEXT_SIZE -> copy(textSize = value)
        SPELL_CHECK_HELP -> copy(spellCheckHelp = value)
        else -> error("not a writable setting: $key")
    }
}

/**
 * Loads the settings with [load] and saves each choice right away with [save] (`PUT /api/newspaper/settings`), sending
 * only the chosen key. A refused save restores that key's previous choice and shows the server's message.
 */
class NewspaperSettingsModel(
    private val scope: CoroutineScope,
    private val load: suspend () -> NewspaperDto,
    private val save: suspend (Map<String, String?>) -> NewspaperDto,
) {
    private val _state = MutableStateFlow(NewspaperSettingsState())
    val state: StateFlow<NewspaperSettingsState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val newspaper = load.invoke()
                _state.update { it.from(newspaper).copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = describe(e)
                _state.update { it.copy(loading = false, error = message) }
            }
        }
    }

    /**
     * Chooses [value] for the writable setting [key] (`null`: installation default) and saves it; ignored while loading
     * or saving.
     */
    fun choose(key: String, value: String?) {
        val current = _state.value
        val previous = current.override(key)
        if (current.loading || current.saving || value == previous) return
        _state.update { it.withOverride(key, value).copy(saving = true, error = null) }
        scope.launch {
            try {
                val newspaper = save(mapOf(key to value))
                _state.update { it.from(newspaper).copy(saving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = apiErrorsOf(e)?.joinToString(" ") { it.message }?.ifEmpty { null } ?: describe(e)
                _state.update { it.withOverride(key, previous).copy(saving = false, error = message) }
            }
        }
    }

    private fun NewspaperSettingsState.from(newspaper: NewspaperDto) = copy(
        textSize = newspaper.overrides.string(READER_TEXT_SIZE),
        effectiveTextSize = newspaper.settings.string(READER_TEXT_SIZE),
        spellCheckHelp = newspaper.overrides.string(SPELL_CHECK_HELP),
        effectiveSpellCheckHelp = newspaper.settings.string(SPELL_CHECK_HELP),
    )

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
