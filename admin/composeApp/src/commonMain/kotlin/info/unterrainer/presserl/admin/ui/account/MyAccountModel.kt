package info.unterrainer.presserl.admin.ui.account

import info.unterrainer.presserl.admin.api.DeletionRequestDto
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What "My account" asks to confirm. */
enum class DeletionStep { REQUEST, WITHDRAW }

/**
 * "My account" state: [requestedAt] is the ISO-8601 instant of the pending deletion request (`null` for none),
 * [pending] the step waiting for confirmation, [error] the message of the last failed call (the state stays as it was).
 */
data class MyAccountState(
    val requestedAt: String?,
    val pending: DeletionStep? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * The user's own deletion request: [ask] offers "Request account deletion" without a pending request and "Withdraw
 * request" with one, both waiting for [confirm] (sends `POST` or `DELETE /api/me/deletion-request`) or [cancel] (sends
 * nothing). [onChanged] gets the new request time after every successful call.
 */
class MyAccountModel(
    private val scope: CoroutineScope,
    requestedAt: String?,
    private val request: suspend () -> DeletionRequestDto,
    private val withdraw: suspend () -> DeletionRequestDto,
    private val onChanged: (String?) -> Unit,
) {
    private val _state = MutableStateFlow(MyAccountState(requestedAt))
    val state: StateFlow<MyAccountState> = _state.asStateFlow()

    fun ask() {
        if (_state.value.busy) return
        val step = if (_state.value.requestedAt == null) DeletionStep.REQUEST else DeletionStep.WITHDRAW
        _state.update { it.copy(pending = step, error = null) }
    }

    fun cancel() {
        _state.update { it.copy(pending = null) }
    }

    fun confirm() {
        val step = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(pending = null, busy = true, error = null) }
        scope.launch {
            try {
                val answer = if (step == DeletionStep.REQUEST) request() else withdraw()
                _state.update { it.copy(requestedAt = answer.deletionRequestedAt, busy = false) }
                onChanged(answer.deletionRequestedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Browser fetch failures surface as kotlin.Error, not Exception
                val message = describe(e)
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }
}
