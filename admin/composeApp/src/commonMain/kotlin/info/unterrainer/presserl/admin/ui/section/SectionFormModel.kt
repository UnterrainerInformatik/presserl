package info.unterrainer.presserl.admin.ui.section

import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionRequest
import info.unterrainer.presserl.admin.ui.apiErrorsOf
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SectionField(val wire: String) {
    NAME("name"),
    COLOR("color"),
}

/** Form state of "New section" / "Edit"; [errors] are the server's messages per field, [general] the rest. */
data class SectionFormState(
    val name: String,
    val color: String,
    val errors: Map<SectionField, String> = emptyMap(),
    val general: String? = null,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

/**
 * "New section" ([section] `null`, [defaultColor] preselected) or "Edit" of [section]. [save] sends
 * `POST /api/sections` or `PUT /api/sections/{id}`; refusals end up in the state, the input is kept.
 */
class SectionFormModel(
    private val scope: CoroutineScope,
    val section: SectionDto?,
    defaultColor: String,
    private val save: suspend (SectionRequest) -> SectionDto,
) {
    private val _state = MutableStateFlow(SectionFormState(section?.name ?: "", section?.color ?: defaultColor))
    val state: StateFlow<SectionFormState> = _state.asStateFlow()

    fun name(value: String) {
        _state.update { it.copy(name = value, errors = it.errors - SectionField.NAME) }
    }

    fun color(value: String) {
        _state.update { it.copy(color = value, errors = it.errors - SectionField.COLOR) }
    }

    fun submit(onSaved: (SectionDto) -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true, general = null) }
        scope.launch {
            try {
                val saved = save(SectionRequest(current.name.trim(), current.color))
                _state.update { it.copy(saving = false) }
                onSaved(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val errors = apiErrorsOf(e)
                if (errors == null) {
                    val message = describe(e)
                    _state.update { it.copy(saving = false, general = message) }
                } else {
                    val fields = mutableMapOf<SectionField, String>()
                    val general = mutableListOf<String>()
                    errors.forEach { error ->
                        val field = SectionField.entries.firstOrNull { it.wire == error.field }
                        if (field != null) fields.getOrPut(field) { error.message } else general += error.message
                    }
                    _state.update { it.copy(saving = false, errors = fields, general = general.joinToString(" ").ifEmpty { null }) }
                }
            }
        }
    }
}
