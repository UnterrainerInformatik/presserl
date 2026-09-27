package info.unterrainer.presserl.admin.ui.section

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.MemberDto
import info.unterrainer.presserl.admin.api.MemberListDto
import info.unterrainer.presserl.admin.ui.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Members screen state; [candidates] are the accounts without a role in the section, [error] the
 * message of the last failure (the list is reloaded after it).
 */
data class SectionMembersState(
    val loaded: Boolean = false,
    val assignableRoles: List<String> = emptyList(),
    val members: List<MemberDto> = emptyList(),
    val candidates: List<AccountDto> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
) {
    /** Whether the user may change or remove [member]'s role. */
    fun canChange(member: MemberDto): Boolean = member.role in assignableRoles
}

/**
 * Members of one section: [loadMembers] is `GET /api/sections/{id}/members`, [loadAccounts] the
 * accounts of `GET /api/accounts`, [assign] and [remove] change a membership. Every change reloads
 * the list, so the screen shows the server's state.
 */
class SectionMembersModel(
    private val scope: CoroutineScope,
    private val loadMembers: suspend () -> MemberListDto,
    private val loadAccounts: suspend () -> List<AccountDto>,
    private val assign: suspend (accountId: String, role: String) -> MemberDto,
    private val remove: suspend (accountId: String) -> Unit,
) {
    private val _state = MutableStateFlow(SectionMembersState())
    val state: StateFlow<SectionMembersState> = _state.asStateFlow()

    fun load() {
        scope.launch { reload(error = null) }
    }

    /** Adds [accountId] with [role], or changes the role of an existing member. */
    fun assign(accountId: String, role: String) = change { assign.invoke(accountId, role) }

    fun remove(accountId: String) = change { remove.invoke(accountId) }

    private fun change(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            val error = try {
                action()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                describe(e)
            }
            reload(error)
        }
    }

    private suspend fun reload(error: String?) {
        try {
            val list = loadMembers()
            val accounts = loadAccounts()
            val memberIds = list.members.map { it.accountId }.toSet()
            _state.value = SectionMembersState(
                loaded = true,
                assignableRoles = list.assignableRoles,
                members = list.members,
                candidates = accounts.filter { it.id !in memberIds },
                error = error,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val message = listOfNotNull(error, describe(e)).joinToString(" ")
            _state.update { it.copy(error = message, busy = false) }
        }
    }
}
