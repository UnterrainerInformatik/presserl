package info.unterrainer.presserl.admin.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.front_page_help
import info.unterrainer.presserl.admin.resources.front_page_label
import info.unterrainer.presserl.admin.ui.apiErrorsOf
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The field of the weight endpoint's error body. */
const val WEIGHT_FIELD = "weight"

/** Editors-in-chief and publishers set front-page weights; the server does not report it in `allowedActions`. */
fun mayWeight(roles: List<String>): Boolean = "EDITOR_IN_CHIEF" in roles || "PUBLISHER" in roles

/**
 * The editor's front-page weight field. It is saved on its own through [save], never through autosave, and is not
 * part of the draft, so it takes no part in undo and redo. The input keeps digits only (at most four, so the
 * server's range check answers out-of-range numbers); an empty field clears the weight.
 *
 * @param saved the weight as last received from the server
 */
class FrontPageWeightModel(saved: Int?, private val save: suspend (Int?) -> ArticleDto) {

    var text by mutableStateOf(saved?.toString() ?: "")
        private set

    /** The server's (or the connection's) message for the last save, `null` when it succeeded. */
    var error by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    private var saved: Int? = saved

    fun edit(input: String) {
        text = input.filter { it in '0'..'9' }.take(MAX_DIGITS)
        error = null
    }

    /**
     * Saves the field unless it holds the saved weight already; returns the updated article, `null` when nothing was
     * saved. A refusal leaves the text as typed and shows the server's message for [WEIGHT_FIELD].
     */
    suspend fun commit(): ArticleDto? {
        val weight = text.toIntOrNull()
        if (weight == saved && error == null) return null
        busy = true
        return try {
            val updated = save(weight)
            saved = updated.frontPageWeight
            text = saved?.toString() ?: ""
            error = null
            updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = weightErrorOf(apiErrorsOf(e)) ?: describe(e)
            null
        } finally {
            busy = false
        }
    }

    private companion object {
        const val MAX_DIGITS = 4
    }
}

/** The message for [WEIGHT_FIELD] among the errors of a refused request. */
fun weightErrorOf(errors: List<FieldErrorDto>?): String? = errors?.firstOrNull { it.field == WEIGHT_FIELD }?.message

/**
 * "Front page": the weight with a short explanation; saved on focus loss and on Enter, [onSaved] receives the
 * updated article.
 */
@Composable
fun FrontPageWeightField(model: FrontPageWeightModel, onSaved: (ArticleDto) -> Unit) {
    val scope = rememberCoroutineScope()
    fun commit() {
        scope.launch { model.commit()?.let(onSaved) }
    }
    OutlinedTextField(
        value = model.text,
        onValueChange = model::edit,
        singleLine = true,
        enabled = !model.busy,
        label = { Text(stringResource(Res.string.front_page_label)) },
        isError = model.error != null,
        supportingText = { Text(model.error ?: stringResource(Res.string.front_page_help)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().onFocusChanged { if (!it.isFocused) commit() },
    )
}
