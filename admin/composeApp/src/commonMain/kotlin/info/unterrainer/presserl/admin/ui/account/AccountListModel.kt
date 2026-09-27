package info.unterrainer.presserl.admin.ui.account

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.api.TrustScopeDto
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Actions on an account, named like the server's `allowedActions`; [EDIT_ROLES] opens a form instead of a confirmation. */
enum class AccountAction { EDIT_ROLES, RESET_PASSWORD, LOCK, UNLOCK }

/** The actions the server offers for this account, in its order; unknown names are skipped. */
fun AccountDto.actions(): List<AccountAction> =
    allowedActions.mapNotNull { name -> AccountAction.entries.firstOrNull { it.name == name } }

/** The approval levels this app knows, as sent in trust entries; entries with other levels are ignored. */
val TRUST_LEVELS = listOf("PUBLISHER", "EDITOR_IN_CHIEF", "SECTION_EDITOR")

/** The account's trust entries with a known level, in the server's order. */
fun AccountDto.knownTrusts(): List<TrustScopeDto> = trusts.filter { it.level in TRUST_LEVELS }

/** A trust switch the user may flip on this account: the entry and whether it is on. */
data class TrustSwitch(val scope: TrustScopeDto, val on: Boolean)

/** One switch per entry of `trustScopes` with a known level, in the server's order. */
fun AccountDto.trustSwitches(): List<TrustSwitch> =
    trustScopes.filter { it.level in TRUST_LEVELS }.map { TrustSwitch(it, it in trusts) }

/** Something waiting for the user's confirmation. */
sealed interface Pending {
    val account: AccountDto
}

/** An account action waiting for confirmation. */
data class PendingAction(override val account: AccountDto, val action: AccountAction) : Pending

/** Turning on trust [scope] for the account, waiting for confirmation. */
data class PendingTrust(override val account: AccountDto, val scope: TrustScopeDto) : Pending

/**
 * Account list state; [error] is the message of the last refused action (the list stays as it was), [reset] the
 * result of a password reset until the slip has taken it over.
 */
data class AccountListState(
    val accounts: List<AccountDto>,
    val pending: Pending? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val reset: CreatedAccountDto? = null,
)

/**
 * Account row actions: [request] asks for confirmation, [confirm] sends `POST /api/accounts/{id}/password-reset`,
 * `/lock` or `/unlock` and replaces the account's row with the server's answer, [cancel] sends nothing. Trust
 * switches: [requestTrust] asks for confirmation before turning trust on and sends `PUT /api/accounts/{id}/trust` at
 * once when turning it off.
 */
class AccountListModel(
    private val scope: CoroutineScope,
    accounts: List<AccountDto>,
    private val resetPassword: suspend (accountId: String) -> CreatedAccountDto,
    private val lock: suspend (accountId: String) -> AccountDto,
    private val unlock: suspend (accountId: String) -> AccountDto,
    private val setTrust: suspend (accountId: String, level: String, sectionId: Long?, trusted: Boolean) -> AccountDto,
) {
    private val _state = MutableStateFlow(AccountListState(accounts))
    val state: StateFlow<AccountListState> = _state.asStateFlow()

    fun request(account: AccountDto, action: AccountAction) {
        if (_state.value.busy || action == AccountAction.EDIT_ROLES) return
        _state.update { it.copy(pending = PendingAction(account, action), error = null) }
    }

    /** Flips trust [scope] of [account] to [on]; unknown levels are ignored. */
    fun requestTrust(account: AccountDto, scope: TrustScopeDto, on: Boolean) {
        if (_state.value.busy || scope.level !in TRUST_LEVELS) return
        if (on) {
            _state.update { it.copy(pending = PendingTrust(account, scope), error = null) }
        } else {
            _state.update { it.copy(busy = true, error = null) }
            send { replace(setTrust(account.id, scope.level, scope.sectionId, false)) }
        }
    }

    fun cancel() {
        _state.update { it.copy(pending = null) }
    }

    fun confirm() {
        val pending = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(pending = null, busy = true, error = null) }
        val id = pending.account.id
        send {
            when (pending) {
                is PendingTrust -> replace(setTrust(id, pending.scope.level, pending.scope.sectionId, true))
                is PendingAction -> when (pending.action) {
                    AccountAction.RESET_PASSWORD -> {
                        val created = resetPassword(id)
                        _state.update { it.copy(accounts = it.accounts.replace(created.account), busy = false, reset = created) }
                    }
                    AccountAction.LOCK -> replace(lock(id))
                    AccountAction.UNLOCK -> replace(unlock(id))
                    AccountAction.EDIT_ROLES -> error("EDIT_ROLES is never confirmed")
                }
            }
        }
    }

    /** Runs [request] (state already busy); a failure clears busy and sets the error, the list stays as it was. */
    private fun send(request: suspend () -> Unit) {
        scope.launch {
            try {
                request()
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
