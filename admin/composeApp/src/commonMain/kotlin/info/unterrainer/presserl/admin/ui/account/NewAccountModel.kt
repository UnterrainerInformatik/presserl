package info.unterrainer.presserl.admin.ui.account

import info.unterrainer.presserl.admin.api.CreateAccountRequest
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionRoleDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form fields of the account forms, named like the server's error fields. */
enum class AccountField(val wire: String) {
    FIRST_NAME("firstName"),
    LAST_NAME("lastName"),
    USERNAME("username"),
    ROLES("roles"),
    SECTION_ROLES("sectionRoles"),
    SECTIONLESS_REPORTER("sectionlessReporter"),
}

/** Form state of "New account"; [errors] are the server's messages per field, [general] the rest. */
data class NewAccountState(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val usernameEdited: Boolean = false,
    val roles: Set<String> = emptySet(),
    /** The chosen section role per section id; sections without a choice are absent. */
    val sectionRoles: Map<Long, String> = emptyMap(),
    /** The choice "Redakteur (ohne Ressort)". */
    val sectionlessReporter: Boolean = false,
    val errors: Map<AccountField, String> = emptyMap(),
    val general: String? = null,
    val creating: Boolean = false,
) {
    val canCreate: Boolean
        get() = firstName.isNotBlank() && username.isNotBlank() &&
            (roles.isNotEmpty() || sectionRoles.isNotEmpty() || sectionlessReporter) && !creating
}

/**
 * "New account" form (design D9). While the user has not edited the username, it follows the first
 * name: [debounceMillis] after the last change the server is asked for a suggestion, and answers to
 * outdated first names are ignored. Roles are sent in the order of [assignableRoles], section roles
 * in the order of [sections]; only sections with assignable roles are offered. The choice
 * "Redakteur (ohne Ressort)" is offered and sent only with [mayAssignSectionlessReporter]. Timing uses
 * [scope]'s dispatcher, so tests control it with virtual time.
 */
class NewAccountModel(
    private val scope: CoroutineScope,
    val assignableRoles: List<String>,
    sections: List<SectionDto> = emptyList(),
    private val suggest: suspend (firstName: String) -> String,
    private val create: suspend (CreateAccountRequest) -> CreatedAccountDto,
    private val debounceMillis: Long = 300,
    val mayAssignSectionlessReporter: Boolean = false,
) {
    val sections: List<SectionDto> = sections.filter { it.assignableRoles.isNotEmpty() }

    private val _state = MutableStateFlow(NewAccountState())
    val state: StateFlow<NewAccountState> = _state.asStateFlow()

    private var suggestion: Job? = null

    fun firstName(value: String) {
        _state.update { it.copy(firstName = value, errors = it.errors - AccountField.FIRST_NAME) }
        if (!_state.value.usernameEdited) follow(value)
    }

    fun lastName(value: String) {
        _state.update { it.copy(lastName = value, errors = it.errors - AccountField.LAST_NAME) }
    }

    /** A username typed by the user; clearing the field lets it follow the first name again. */
    fun username(value: String) {
        suggestion?.cancel()
        _state.update { it.copy(username = value, usernameEdited = value.isNotEmpty(), errors = it.errors - AccountField.USERNAME) }
        if (value.isEmpty()) follow(_state.value.firstName)
    }

    fun role(role: String, selected: Boolean) {
        if (role !in assignableRoles) return
        _state.update {
            it.copy(roles = if (selected) it.roles + role else it.roles - role, errors = it.errors - AccountField.ROLES - AccountField.SECTION_ROLES)
        }
    }

    /** Selects or clears "Redakteur (ohne Ressort)"; ignored when the user may not assign it. */
    fun sectionlessReporter(selected: Boolean) {
        if (!mayAssignSectionlessReporter) return
        _state.update {
            it.copy(sectionlessReporter = selected, errors = it.errors - AccountField.ROLES - AccountField.SECTIONLESS_REPORTER)
        }
    }

    /** Chooses [role] in the section, or no role for `null`. */
    fun sectionRole(sectionId: Long, role: String?) {
        val section = sections.firstOrNull { it.id == sectionId } ?: return
        if (role != null && role !in section.assignableRoles) return
        _state.update {
            it.copy(
                sectionRoles = if (role == null) it.sectionRoles - sectionId else it.sectionRoles + (sectionId to role),
                errors = it.errors - AccountField.SECTION_ROLES - AccountField.ROLES,
            )
        }
    }

    /** Sends the form; [onCreated] gets the new account with its password, refusals end up in the state. */
    fun submit(onCreated: (CreatedAccountDto) -> Unit) {
        val current = _state.value
        if (!current.canCreate) return
        suggestion?.cancel()
        _state.update { it.copy(creating = true, general = null) }
        scope.launch {
            val request = CreateAccountRequest(
                firstName = current.firstName.trim(),
                lastName = current.lastName.trim(),
                username = current.username,
                roles = assignableRoles.filter { it in current.roles },
                sectionRoles = sections.mapNotNull { section -> current.sectionRoles[section.id]?.let { SectionRoleDto(section.id, it) } },
                sectionlessReporter = if (mayAssignSectionlessReporter) current.sectionlessReporter else null,
            )
            try {
                val created = create(request)
                _state.update { it.copy(creating = false) }
                onCreated(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val (fields, general) = accountRefusal(e)
                _state.update { it.copy(creating = false, errors = fields, general = general) }
            }
        }
    }

    private fun follow(firstName: String) {
        suggestion?.cancel()
        if (firstName.isBlank()) {
            _state.update { it.copy(username = "") }
            return
        }
        suggestion = scope.launch {
            delay(debounceMillis)
            val username = try {
                suggest(firstName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // No suggestion; the user can still type a username
                return@launch
            }
            _state.update {
                if (it.firstName == firstName && !it.usernameEdited) {
                    it.copy(username = username, errors = it.errors - AccountField.USERNAME)
                } else {
                    it
                }
            }
        }
    }
}
