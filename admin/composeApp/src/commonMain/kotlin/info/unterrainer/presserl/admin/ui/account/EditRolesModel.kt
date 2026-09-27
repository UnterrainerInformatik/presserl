package info.unterrainer.presserl.admin.ui.account

import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.EditRolesRequest
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionRoleDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Newspaper roles in the server's order. */
private val NEWSPAPER_ROLES = listOf("PUBLISHER", "EDITOR_IN_CHIEF", "READER")

/** A newspaper role offered in "Edit roles"; a role that is not [editable] is shown read-only. */
data class RoleChoice(val role: String, val editable: Boolean)

/** A section offered in "Edit roles"; when not [editable], the account's role there is shown read-only. */
data class SectionChoice(val section: SectionDto, val editable: Boolean)

/** Form state of "Edit roles"; [errors] are the server's messages per field, [general] the rest. */
data class EditRolesState(
    val roles: Set<String>,
    /** The chosen section role per section id; sections without a role are absent. */
    val sectionRoles: Map<Long, String>,
    val changed: Boolean = false,
    val errors: Map<AccountField, String> = emptyMap(),
    val general: String? = null,
    val saving: Boolean = false,
) {
    val canSave: Boolean
        get() = changed && (roles.isNotEmpty() || sectionRoles.isNotEmpty()) && !saving
}

/**
 * "Edit roles" form (design D6), preset from [account]. Newspaper roles are editable when in [assignableRoles],
 * section roles where the section's `assignableRoles` contain the account's current role (or it has none there);
 * the account's other roles are shown read-only, and sections where nothing is assignable and the account holds no
 * role are left out. [submit] sends the complete roles, read-only ones included.
 */
class EditRolesModel(
    private val scope: CoroutineScope,
    val account: AccountDto,
    assignableRoles: List<String>,
    sections: List<SectionDto>,
    private val save: suspend (accountId: String, EditRolesRequest) -> AccountDto,
) {
    private val initialRoles = account.roles.toSet()
    private val initialSectionRoles = account.sectionRoles.associate { it.sectionId to it.role }

    val roleChoices: List<RoleChoice> = (NEWSPAPER_ROLES + account.roles.filter { it !in NEWSPAPER_ROLES })
        .filter { it in assignableRoles || it in initialRoles }
        .map { RoleChoice(it, editable = it in assignableRoles) }

    val sectionChoices: List<SectionChoice> = sections.mapNotNull { section ->
        val current = initialSectionRoles[section.id]
        val editable = section.assignableRoles.isNotEmpty() && (current == null || current in section.assignableRoles)
        if (editable || current != null) SectionChoice(section, editable) else null
    }

    private val _state = MutableStateFlow(EditRolesState(initialRoles, initialSectionRoles))
    val state: StateFlow<EditRolesState> = _state.asStateFlow()

    fun role(role: String, selected: Boolean) {
        if (roleChoices.none { it.role == role && it.editable }) return
        change { it.copy(roles = if (selected) it.roles + role else it.roles - role) }
    }

    /** Chooses [role] in the section, or no role for `null`. */
    fun sectionRole(sectionId: Long, role: String?) {
        val choice = sectionChoices.firstOrNull { it.section.id == sectionId && it.editable } ?: return
        if (role != null && role !in choice.section.assignableRoles) return
        change { it.copy(sectionRoles = if (role == null) it.sectionRoles - sectionId else it.sectionRoles + (sectionId to role)) }
    }

    /** Sends the complete roles; [onSaved] gets the account with its new roles, refusals end up in the state. */
    fun submit(onSaved: (AccountDto) -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true, general = null) }
        val sectionOrder = sectionChoices.map { it.section.id }
        val request = EditRolesRequest(
            roles = NEWSPAPER_ROLES.filter { it in current.roles } + current.roles.filter { it !in NEWSPAPER_ROLES },
            sectionRoles = current.sectionRoles.entries
                .sortedBy { (id, _) -> sectionOrder.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
                .map { (id, role) -> SectionRoleDto(id, role) },
        )
        scope.launch {
            try {
                val saved = save(account.id, request)
                _state.update { it.copy(saving = false) }
                onSaved(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val (fields, general) = accountRefusal(e)
                _state.update { it.copy(saving = false, errors = fields, general = general) }
            }
        }
    }

    private fun change(edit: (EditRolesState) -> EditRolesState) {
        _state.update { state ->
            edit(state).let {
                it.copy(
                    changed = it.roles != initialRoles || it.sectionRoles != initialSectionRoles,
                    errors = it.errors - AccountField.ROLES - AccountField.SECTION_ROLES,
                )
            }
        }
    }
}
