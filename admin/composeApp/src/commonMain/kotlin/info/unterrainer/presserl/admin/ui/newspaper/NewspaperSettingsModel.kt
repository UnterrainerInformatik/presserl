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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

const val READER_TEXT_SIZE = "reader.text-size"
const val SPELL_CHECK_HELP = "spell-check.help"
const val ARTICLE_CORRECTIONS = "article.corrections"

/** The reader text sizes in order with their body size in px. */
val TEXT_SIZES = listOf("s" to 17, "m" to 19, "l" to 22, "xl" to 26)

/** The spell-check help levels, from most to least help. */
val SPELL_CHECK_HELP_LEVELS = listOf("suggestions", "messages", "marks")

/**
 * State of the newspaper settings screen. [textSize] is the newspaper's override of `reader.text-size`, `null` for the
 * installation default; [effectiveTextSize] the size the reader uses. [spellCheckHelp] and [effectiveSpellCheckHelp]
 * are the same for `spell-check.help`, [corrections] and [effectiveCorrections] for the switch `article.corrections`.
 */
data class NewspaperSettingsState(
    val loading: Boolean = true,
    val textSize: String? = null,
    val effectiveTextSize: String? = null,
    val spellCheckHelp: String? = null,
    val effectiveSpellCheckHelp: String? = null,
    val corrections: Boolean? = null,
    val effectiveCorrections: Boolean? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** The newspaper's override of the writable setting [key] as JSON, `null` for the installation default. */
    fun override(key: String): JsonPrimitive? = when (key) {
        READER_TEXT_SIZE -> textSize?.let(::JsonPrimitive)
        SPELL_CHECK_HELP -> spellCheckHelp?.let(::JsonPrimitive)
        ARTICLE_CORRECTIONS -> corrections?.let(::JsonPrimitive)
        else -> error("not a writable setting: $key")
    }

    fun withOverride(key: String, value: JsonPrimitive?): NewspaperSettingsState = when (key) {
        READER_TEXT_SIZE -> copy(textSize = value?.contentOrNull)
        SPELL_CHECK_HELP -> copy(spellCheckHelp = value?.contentOrNull)
        ARTICLE_CORRECTIONS -> copy(corrections = value?.booleanOrNull)
        else -> error("not a writable setting: $key")
    }
}

/**
 * What a switch setting shows: the [selected] choice (`null` for the installation default), the [effective] value and
 * whether the user can change it now ([enabled]).
 */
data class SwitchView(val selected: Boolean?, val effective: Boolean?, val enabled: Boolean)

/** The corrections switch for a user who [mayConfigure] it (`CONFIGURE_CORRECTIONS`); read-only for everyone else. */
fun NewspaperSettingsState.correctionsView(mayConfigure: Boolean): SwitchView =
    SwitchView(corrections, effectiveCorrections, enabled = mayConfigure && !loading && !saving)

/**
 * Loads the settings with [load] and saves each choice right away with [save] (`PUT /api/newspaper/settings`), sending
 * only the chosen key. A refused save restores that key's previous choice and shows the server's message.
 */
class NewspaperSettingsModel(
    private val scope: CoroutineScope,
    private val load: suspend () -> NewspaperDto,
    private val save: suspend (Map<String, JsonElement>) -> NewspaperDto,
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
     * Chooses [value] for the enumerated writable setting [key] (`null`: installation default) and saves it as JSON
     * string; ignored while loading or saving.
     */
    fun choose(key: String, value: String?) = saveChoice(key, value?.let(::JsonPrimitive))

    /** Chooses [value] for the switch [key] (`null`: installation default) and saves it as JSON boolean. */
    fun chooseSwitch(key: String, value: Boolean?) = saveChoice(key, value?.let(::JsonPrimitive))

    private fun saveChoice(key: String, value: JsonPrimitive?) {
        val current = _state.value
        val previous = current.override(key)
        if (current.loading || current.saving || value == previous) return
        _state.update { it.withOverride(key, value).copy(saving = true, error = null) }
        scope.launch {
            try {
                val newspaper = save(mapOf(key to (value ?: JsonNull)))
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
        corrections = newspaper.overrides.boolean(ARTICLE_CORRECTIONS),
        effectiveCorrections = newspaper.settings.boolean(ARTICLE_CORRECTIONS),
    )

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}
