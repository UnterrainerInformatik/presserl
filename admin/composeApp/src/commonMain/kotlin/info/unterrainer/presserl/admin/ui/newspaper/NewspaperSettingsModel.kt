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

/** The reader text sizes in order with their body size in px. */
val TEXT_SIZES = listOf("s" to 17, "m" to 19, "l" to 22, "xl" to 26)

/**
 * State of the newspaper settings screen. [textSize] is the newspaper's override of `reader.text-size`, `null` for the
 * installation default; [effectiveTextSize] the size the reader uses.
 */
data class NewspaperSettingsState(
    val loading: Boolean = true,
    val textSize: String? = null,
    val effectiveTextSize: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * Loads the settings with [load] and saves each choice right away with [save] (`PUT /api/newspaper/settings`). A
 * refused save keeps the previous choice and shows the server's message.
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

    /** Chooses [textSize] (`null`: installation default) and saves it; ignored while loading or saving. */
    fun textSize(textSize: String?) {
        val current = _state.value
        if (current.loading || current.saving || textSize == current.textSize) return
        _state.update { it.copy(textSize = textSize, saving = true, error = null) }
        scope.launch {
            try {
                val newspaper = save(mapOf(READER_TEXT_SIZE to textSize))
                _state.update { it.from(newspaper).copy(saving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = apiErrorsOf(e)?.joinToString(" ") { it.message }?.ifEmpty { null } ?: describe(e)
                _state.update { it.copy(textSize = current.textSize, saving = false, error = message) }
            }
        }
    }

    private fun NewspaperSettingsState.from(newspaper: NewspaperDto) = copy(
        textSize = newspaper.overrides.string(READER_TEXT_SIZE),
        effectiveTextSize = newspaper.settings.string(READER_TEXT_SIZE),
    )

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
