package info.unterrainer.presserl.admin.ui.issue

import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.IssueDetailDto
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The calls of one issue's detail screen (`/api/issues/{id}…`) and the article list for the picker. */
class IssueCalls(
    val load: suspend () -> IssueDetailDto,
    val setDate: suspend (publicationDate: String?) -> IssueDetailDto,
    val publish: suspend () -> IssueDetailDto,
    val unpublish: suspend () -> IssueDetailDto,
    val setArticles: suspend (articleIds: List<Long>) -> IssueDetailDto,
    val delete: suspend () -> Unit,
    val articles: suspend () -> List<ArticleSummaryDto>,
)

/**
 * Detail screen state. [dateText] is the publication date field; [candidates] the articles the picker offers,
 * `null` while it is closed; [confirmDelete] shows the in-app confirmation; [error] the message of the last failure.
 */
data class IssueDetailState(
    val issue: IssueDetailDto? = null,
    val dateText: String = "",
    val dateInvalid: Boolean = false,
    val candidates: List<ArticleSummaryDto>? = null,
    val confirmDelete: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false,
) {
    /** Whether the issue may be deleted: loaded and not live. */
    val canDelete: Boolean get() = issue != null && !issue.published
}

/**
 * One issue. Every change of the article list sends the complete new order; the list is changed locally first and
 * then replaced by the server's response. A failure is shown as [IssueDetailState.error] and the issue is reloaded,
 * the typed date stays.
 */
class IssueDetailModel(private val scope: CoroutineScope, private val calls: IssueCalls) {
    private val _state = MutableStateFlow(IssueDetailState())
    val state: StateFlow<IssueDetailState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            try {
                val issue = calls.load()
                _state.update { it.copy(issue = issue, dateText = issue.publicationDate ?: "", error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = describe(e)
                _state.update { it.copy(error = message) }
            }
        }
    }

    fun dateText(value: String) {
        _state.update { it.copy(dateText = value, dateInvalid = false) }
    }

    /** Saves the typed date; an empty field clears it, an invalid one sends nothing and is marked. */
    fun saveDate() {
        when (val input = parseDate(_state.value.dateText)) {
            DateInput.Empty -> sendDate(null)
            is DateInput.Valid -> sendDate(input.iso)
            DateInput.Invalid -> _state.update { it.copy(dateInvalid = true) }
        }
    }

    fun clearDate() {
        _state.update { it.copy(dateText = "", dateInvalid = false) }
        sendDate(null)
    }

    fun setLive(live: Boolean) = change { if (live) calls.publish() else calls.unpublish() }

    fun moveUp(index: Int) = move(index, index - 1)

    fun moveDown(index: Int) = move(index, index + 1)

    fun remove(articleId: Long) {
        val articles = _state.value.issue?.articles ?: return
        sendOrder(articles.filter { it.id != articleId })
    }

    /** Opens the picker with every article not in this issue. */
    fun openPicker() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                val all = calls.articles()
                val own = _state.value.issue?.articles.orEmpty().map { it.id }.toSet()
                _state.update { it.copy(busy = false, candidates = all.filter { article -> article.id !in own }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = describe(e)
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }

    fun closePicker() {
        _state.update { it.copy(candidates = null) }
    }

    /** Appends [article] from the picker; one of another issue moves here. */
    fun add(article: ArticleSummaryDto) {
        val articles = _state.value.issue?.articles ?: return
        _state.update { it.copy(candidates = null) }
        sendOrder(articles + article)
    }

    fun requestDelete() {
        if (_state.value.canDelete) _state.update { it.copy(confirmDelete = true) }
    }

    fun cancelDelete() {
        _state.update { it.copy(confirmDelete = false) }
    }

    /** Deletes the issue after the confirmation; [onDeleted] follows success. */
    fun confirmDelete(onDeleted: () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(confirmDelete = false, busy = true, error = null) }
        scope.launch {
            try {
                calls.delete()
                _state.update { it.copy(busy = false) }
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed(e)
            }
        }
    }

    private fun move(from: Int, to: Int) {
        val articles = _state.value.issue?.articles ?: return
        if (from !in articles.indices || to !in articles.indices) return
        sendOrder(articles.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun sendOrder(articles: List<ArticleSummaryDto>) {
        if (_state.value.busy) return
        _state.update { state -> state.copy(issue = state.issue?.copy(articles = articles, articleCount = articles.size)) }
        change { calls.setArticles(articles.map { it.id }) }
    }

    private fun sendDate(date: String?) = change(keepDate = false) { calls.setDate(date) }

    /**
     * Runs [action], which answers the changed issue. [keepDate] leaves the date field as typed (every change but a
     * saved date).
     */
    private fun change(keepDate: Boolean = true, action: suspend () -> IssueDetailDto) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                val issue = action()
                _state.update {
                    it.copy(issue = issue, busy = false, dateText = if (keepDate) it.dateText else issue.publicationDate ?: "")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed(e)
            }
        }
    }

    /** Shows the failure and reloads the issue; the typed date stays. */
    private suspend fun failed(e: Throwable) {
        val message = describe(e)
        _state.update { it.copy(busy = false, error = message) }
        try {
            val issue = calls.load()
            _state.update { it.copy(issue = issue) }
        } catch (reload: CancellationException) {
            throw reload
        } catch (reload: Throwable) {
            val reloadMessage = describe(reload)
            _state.update { it.copy(error = "$message $reloadMessage") }
        }
    }
}
