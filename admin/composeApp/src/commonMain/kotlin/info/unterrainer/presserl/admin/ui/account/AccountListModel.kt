package info.unterrainer.presserl.admin.ui.account

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Actions on an account, named like the server's `allowedActions`. */
enum class AccountAction { RESET_PASSWORD, LOCK, UNLOCK }

/** The actions the server offers for this account, in its order; unknown names are skipped. */
fun AccountDto.actions(): List<AccountAction> =
    allowedActions.mapNotNull { name -> AccountAction.entries.firstOrNull { it.name == name } }

/** An action waiting for the user's confirmation. */
data class PendingAction(val account: AccountDto, val action: AccountAction)

/**
 * Account list state; [error] is the message of the last refused action (the list stays as it was), [reset] the
 * result of a password reset until the slip has taken it over.
 */
data class AccountListState(
    val accounts: List<AccountDto>,
    val pending: PendingAction? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val reset: CreatedAccountDto? = null,
)

/**
 * Account row actions: [request] asks for confirmation, [confirm] sends `POST /api/accounts/{id}/password-reset`,
 * `/lock` or `/unlock` and replaces the account's row with the server's answer, [cancel] sends nothing.
 */
class AccountListModel(
    private val scope: CoroutineScope,
    accounts: List<AccountDto>,
    private val resetPassword: suspend (accountId: String) -> CreatedAccountDto,
    private val lock: suspend (accountId: String) -> AccountDto,
    private val unlock: suspend (accountId: String) -> AccountDto,
) {
    private val _state = MutableStateFlow(AccountListState(accounts))
    val state: StateFlow<AccountListState> = _state.asStateFlow()

    fun request(account: AccountDto, action: AccountAction) {
        if (_state.value.busy) return
        _state.update { it.copy(pending = PendingAction(account, action), error = null) }
    }

    fun cancel() {
        _state.update { it.copy(pending = null) }
    }

    fun confirm() {
        val pending = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(pending = null, busy = true, error = null) }
        val id = pending.account.id
        scope.launch {
            try {
                when (pending.action) {
                    AccountAction.RESET_PASSWORD -> {
                        val created = resetPassword(id)
                        _state.update { it.copy(accounts = it.accounts.replace(created.account), busy = false, reset = created) }
                    }
                    AccountAction.LOCK -> replace(lock(id))
                    AccountAction.UNLOCK -> replace(unlock(id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Browser fetch failures surface as kotlin.Error, not Exception
                val message = describe(e)
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }

    /** The slip has taken over the reset result; the password is dropped here. */
    fun resetShown() {
        _state.update { it.copy(reset = null) }
    }

    private fun replace(account: AccountDto) {
        _state.update { it.copy(accounts = it.accounts.replace(account), busy = false) }
    }

    private fun List<AccountDto>.replace(account: AccountDto): List<AccountDto> =
        map { if (it.id == account.id) account else it }
}
