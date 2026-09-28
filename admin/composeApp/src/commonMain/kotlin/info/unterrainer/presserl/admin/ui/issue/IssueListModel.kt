package info.unterrainer.presserl.admin.ui.issue

import info.unterrainer.presserl.admin.api.IssueDetailDto
import info.unterrainer.presserl.admin.api.IssueDto
import info.unterrainer.presserl.admin.api.IssueListDto
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Issues screen state: [issues] in the server's order (highest number first); [newDate] is the date typed into the
 * "New issue" dialog, `null` while it is closed; [error] the message of the last failure.
 */
data class IssueListState(
    val loaded: Boolean = false,
    val issues: List<IssueDto> = emptyList(),
    val error: String? = null,
    val newDate: String? = null,
    val newDateInvalid: Boolean = false,
    val busy: Boolean = false,
)

/** The issues: [loadIssues] is `GET /api/issues`, [createIssue] `POST /api/issues` with an optional ISO date. */
class IssueListModel(
    private val scope: CoroutineScope,
    private val loadIssues: suspend () -> IssueListDto,
    private val createIssue: suspend (publicationDate: String?) -> IssueDetailDto,
) {
    private val _state = MutableStateFlow(IssueListState())
    val state: StateFlow<IssueListState> = _state.asStateFlow()

    fun load() {
        scope.launch { reload() }
    }

    /** Opens the "New issue" dialog without a date. */
    fun openNew() {
        _state.update { it.copy(newDate = "", newDateInvalid = false) }
    }

    fun newDate(value: String) {
        _state.update { it.copy(newDate = value, newDateInvalid = false) }
    }

    fun cancelNew() {
        _state.update { it.copy(newDate = null, newDateInvalid = false) }
    }

    /** Creates the issue with the dialog's date; an invalid date sends nothing and is marked. */
    fun submitNew(onCreated: (IssueDetailDto) -> Unit) {
        val current = _state.value
        val typed = current.newDate ?: return
        if (current.busy) return
        val date = when (val input = parseDate(typed)) {
            DateInput.Empty -> null
            is DateInput.Valid -> input.iso
            DateInput.Invalid -> {
                _state.update { it.copy(newDateInvalid = true) }
                return
            }
        }
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                val created = createIssue(date)
                _state.update { it.copy(busy = false, newDate = null) }
                onCreated(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = describe(e)
                _state.update { it.copy(busy = false, error = message) }
                reload(keepError = true)
            }
        }
    }

    private suspend fun reload(keepError: Boolean = false) {
        try {
            val list = loadIssues()
            _state.update { it.copy(loaded = true, issues = list.issues, error = if (keepError) it.error else null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val message = describe(e)
            _state.update { it.copy(error = listOfNotNull(it.error.takeIf { keepError }, message).joinToString(" ")) }
        }
    }
}
