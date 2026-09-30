package info.unterrainer.presserl.admin.ui.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.AccountListDto
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.TrustScopeDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.account_action_failed
import info.unterrainer.presserl.admin.resources.account_locked
import info.unterrainer.presserl.admin.resources.action_edit_roles
import info.unterrainer.presserl.admin.resources.action_lock
import info.unterrainer.presserl.admin.resources.action_reset_password
import info.unterrainer.presserl.admin.resources.action_trust
import info.unterrainer.presserl.admin.resources.action_unlock
import info.unterrainer.presserl.admin.resources.cancel
import info.unterrainer.presserl.admin.resources.confirm_lock_text
import info.unterrainer.presserl.admin.resources.confirm_lock_title
import info.unterrainer.presserl.admin.resources.confirm_reset_text
import info.unterrainer.presserl.admin.resources.confirm_reset_title
import info.unterrainer.presserl.admin.resources.confirm_trust_text
import info.unterrainer.presserl.admin.resources.confirm_trust_title
import info.unterrainer.presserl.admin.resources.confirm_unlock_text
import info.unterrainer.presserl.admin.resources.confirm_unlock_title
import info.unterrainer.presserl.admin.resources.create_account
import info.unterrainer.presserl.admin.resources.done
import info.unterrainer.presserl.admin.resources.edit_roles_title
import info.unterrainer.presserl.admin.resources.field_first_name
import info.unterrainer.presserl.admin.resources.field_last_name
import info.unterrainer.presserl.admin.resources.field_roles
import info.unterrainer.presserl.admin.resources.field_section_roles
import info.unterrainer.presserl.admin.resources.field_username
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.new_account
import info.unterrainer.presserl.admin.resources.no_roles
import info.unterrainer.presserl.admin.resources.print
import info.unterrainer.presserl.admin.resources.roles_read_only_hint
import info.unterrainer.presserl.admin.resources.save
import info.unterrainer.presserl.admin.resources.section_role_none
import info.unterrainer.presserl.admin.resources.slip_address
import info.unterrainer.presserl.admin.resources.slip_heading
import info.unterrainer.presserl.admin.resources.slip_note
import info.unterrainer.presserl.admin.resources.slip_password
import info.unterrainer.presserl.admin.resources.slip_qr_hint
import info.unterrainer.presserl.admin.resources.slip_username
import info.unterrainer.presserl.admin.resources.trust_switches
import info.unterrainer.presserl.admin.resources.trusted_by
import info.unterrainer.presserl.admin.resources.username_hint
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.BackButton
import info.unterrainer.presserl.admin.ui.LoadFailed
import info.unterrainer.presserl.admin.ui.approvalLevelLabel
import info.unterrainer.presserl.admin.ui.attempt
import info.unterrainer.presserl.admin.ui.roleText
import info.unterrainer.presserl.admin.ui.section.ColorMarker
import info.unterrainer.presserl.admin.ui.sectionRoleText
import info.unterrainer.presserl.admin.ui.sectionlessReporterLabel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/** The accounts, and the sections to name section roles and to offer them in "New account" and "Edit roles". */
private data class AccountsView(val accounts: AccountListDto, val sections: List<SectionDto>)

@Composable
fun AccountListScreen(
    api: ApiClient,
    onNew: (assignableRoles: List<String>, sections: List<SectionDto>, mayAssignSectionlessReporter: Boolean) -> Unit,
    onEditRoles: (
        account: AccountDto,
        assignableRoles: List<String>,
        sections: List<SectionDto>,
        mayAssignSectionlessReporter: Boolean,
    ) -> Unit,
    onReset: (CreatedAccountDto) -> Unit,
) {
    var view by remember { mutableStateOf<AccountsView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }

    LaunchedEffect(loads) {
        error = null
        view = attempt({ error = it }) { AccountsView(api.accounts(), api.sections().sections) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val current = view
        val canCreate = current != null &&
            (current.accounts.assignableRoles.isNotEmpty() || current.sections.any { it.assignableRoles.isNotEmpty() } ||
                current.accounts.mayAssignSectionlessReporter)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                enabled = canCreate,
                onClick = {
                    current?.let { onNew(it.accounts.assignableRoles, it.sections, it.accounts.mayAssignSectionlessReporter) }
                },
            ) {
                Text(stringResource(Res.string.new_account))
            }
        }
        when {
            error != null -> LoadFailed(error!!, onReload = { loads++ })
            current == null -> Text(stringResource(Res.string.loading))
            else -> key(current) {
                AccountList(
                    api,
                    current,
                    onEditRoles = {
                        onEditRoles(it, current.accounts.assignableRoles, current.sections, current.accounts.mayAssignSectionlessReporter)
                    },
                    onReset,
                )
            }
        }
    }
}

@Composable
private fun AccountList(api: ApiClient, view: AccountsView, onEditRoles: (AccountDto) -> Unit, onReset: (CreatedAccountDto) -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember { AccountListModel(scope, view.accounts.accounts, api::resetPassword, api::lock, api::unlock, api::setTrust) }
    val state by model.state.collectAsState()
    val sectionNames = view.sections.associate { it.id to it.name }

    LaunchedEffect(state.reset) {
        state.reset?.let {
            model.resetShown()
            onReset(it)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.error?.let { Banner(stringResource(Res.string.account_action_failed, it)) }
        LazyColumn {
            items(state.accounts, key = { it.id }) { account ->
                AccountRow(
                    account,
                    sectionNames,
                    enabled = !state.busy,
                    onAction = { if (it == AccountAction.EDIT_ROLES) onEditRoles(account) else model.request(account, it) },
                    onTrust = { scope, on -> model.requestTrust(account, scope, on) },
                )
                HorizontalDivider()
            }
        }
    }

    state.pending?.let { pending -> ConfirmPending(pending, sectionNames, onConfirm = model::confirm, onCancel = model::cancel) }
}

/** Asks before an account action or before turning trust on, naming the account. */
@Composable
private fun ConfirmPending(pending: Pending, sectionNames: Map<Long, String>, onConfirm: () -> Unit, onCancel: () -> Unit) {
    when (pending) {
        is PendingTrust -> ConfirmDialog(
            title = stringResource(Res.string.confirm_trust_title),
            text = stringResource(Res.string.confirm_trust_text, pending.account.username, trustText(pending.scope, sectionNames)),
            confirmText = stringResource(Res.string.action_trust),
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
        is PendingAction -> {
            val (title, text) = when (pending.action) {
                AccountAction.RESET_PASSWORD -> Res.string.confirm_reset_title to Res.string.confirm_reset_text
                AccountAction.LOCK -> Res.string.confirm_lock_title to Res.string.confirm_lock_text
                AccountAction.UNLOCK -> Res.string.confirm_unlock_title to Res.string.confirm_unlock_text
                AccountAction.EDIT_ROLES -> return
            }
            ConfirmDialog(
                title = stringResource(title),
                text = stringResource(text, pending.account.username),
                confirmText = actionText(pending.action),
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** The level of a trust entry as its role label, for `SECTION_EDITOR` with the section name ("Ressortleiter · Sport"). */
@Composable
private fun trustText(scope: TrustScopeDto, sectionNames: Map<Long, String>): String {
    val level = approvalLevelLabel(scope.level)?.let { stringResource(it) } ?: scope.level
    return scope.sectionId?.let { "$level · ${sectionNames[it] ?: "#$it"}" } ?: level
}

@Composable
private fun actionText(action: AccountAction): String = stringResource(
    when (action) {
        AccountAction.EDIT_ROLES -> Res.string.action_edit_roles
        AccountAction.RESET_PASSWORD -> Res.string.action_reset_password
        AccountAction.LOCK -> Res.string.action_lock
        AccountAction.UNLOCK -> Res.string.action_unlock
    },
)

@Composable
private fun AccountRow(
    account: AccountDto,
    sectionNames: Map<Long, String>,
    enabled: Boolean,
    onAction: (AccountAction) -> Unit,
    onTrust: (TrustScopeDto, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 12.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val title = listOfNotNull(account.username, stringResource(Res.string.account_locked).takeIf { !account.enabled })
        Text(title.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
        val roles = account.roles.map { roleText(it) } +
            (if (account.sectionlessReporter) listOf(stringResource(sectionlessReporterLabel)) else emptyList()) +
            account.sectionRoles.map { sectionRoleText(it.role) + " · " + (sectionNames[it.sectionId] ?: "#${it.sectionId}") }
        val details = listOf(
            listOf(account.firstName, account.lastName).filter { it.isNotEmpty() }.joinToString(" "),
            if (roles.isEmpty()) stringResource(Res.string.no_roles) else roles.joinToString(", "),
        ).filter { it.isNotEmpty() }
        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        val trusts = account.knownTrusts().map { trustText(it, sectionNames) }
        if (trusts.isNotEmpty()) {
            Text(stringResource(Res.string.trusted_by, trusts.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
        }
        val switches = account.trustSwitches()
        if (switches.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.trust_switches), style = MaterialTheme.typography.bodyMedium)
                switches.forEach { switch ->
                    Row(
                        Modifier.heightIn(min = 44.dp).toggleable(
                            value = switch.on,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = { onTrust(switch.scope, it) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(checked = switch.on, onCheckedChange = null, enabled = enabled)
                        Text(trustText(switch.scope, sectionNames), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        val actions = account.actions()
        if (actions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    TextButton(onClick = { onAction(action) }, enabled = enabled) { Text(actionText(action)) }
                }
            }
        }
    }
}

@Composable
fun NewAccountScreen(
    api: ApiClient,
    assignableRoles: List<String>,
    sections: List<SectionDto>,
    mayAssignSectionlessReporter: Boolean,
    onBack: () -> Unit,
    onCreated: (CreatedAccountDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val model = remember(assignableRoles, sections, mayAssignSectionlessReporter) {
        NewAccountModel(
            scope,
            assignableRoles,
            sections,
            api::usernameSuggestion,
            api::createAccount,
            mayAssignSectionlessReporter = mayAssignSectionlessReporter,
        )
    }
    val state by model.state.collectAsState()

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text(stringResource(Res.string.new_account), style = MaterialTheme.typography.headlineSmall)
        state.general?.let { Banner(it) }
        FormField(state.firstName, model::firstName, stringResource(Res.string.field_first_name), state.errors[AccountField.FIRST_NAME])
        FormField(state.lastName, model::lastName, stringResource(Res.string.field_last_name), state.errors[AccountField.LAST_NAME])
        FormField(
            state.username,
            model::username,
            stringResource(Res.string.field_username),
            state.errors[AccountField.USERNAME],
            hint = stringResource(Res.string.username_hint),
        )
        if (model.assignableRoles.isNotEmpty() || model.mayAssignSectionlessReporter) {
            Text(stringResource(Res.string.field_roles), style = MaterialTheme.typography.titleSmall)
        }
        model.assignableRoles.forEach { role ->
            val selected = role in state.roles
            Row(
                Modifier.heightIn(min = 44.dp).toggleable(value = selected, role = Role.Checkbox, onValueChange = { model.role(role, it) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = null)
                Text(roleText(role), Modifier.padding(start = 8.dp))
            }
        }
        if (model.mayAssignSectionlessReporter) {
            RoleCheckbox(stringResource(sectionlessReporterLabel), state.sectionlessReporter, editable = true) {
                model.sectionlessReporter(it)
            }
        }
        state.errors[AccountField.ROLES]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.errors[AccountField.SECTIONLESS_REPORTER]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (model.sections.isNotEmpty()) Text(stringResource(Res.string.field_section_roles), style = MaterialTheme.typography.titleSmall)
        model.sections.forEach { section ->
            SectionRoleRow(section, state.sectionRoles[section.id]) { model.sectionRole(section.id, it) }
        }
        state.errors[AccountField.SECTION_ROLES]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(enabled = state.canCreate, onClick = { model.submit(onCreated) }) { Text(stringResource(Res.string.create_account)) }
    }
}

/**
 * "Edit roles" (design D6): the account's roles preset, those the user may not change shown read-only. "Save" sends
 * the complete roles and calls [onSaved]; [onBack] ("Cancel") sends nothing.
 */
@Composable
fun EditRolesScreen(
    api: ApiClient,
    account: AccountDto,
    assignableRoles: List<String>,
    sections: List<SectionDto>,
    mayAssignSectionlessReporter: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val model = remember(account, assignableRoles, sections, mayAssignSectionlessReporter) {
        EditRolesModel(scope, account, assignableRoles, sections, mayAssignSectionlessReporter, save = api::editRoles)
    }
    val state by model.state.collectAsState()
    val readOnly = model.roleChoices.any { !it.editable } || model.sectionChoices.any { !it.editable } ||
        !model.mayAssignSectionlessReporter

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text(stringResource(Res.string.edit_roles_title, account.username), style = MaterialTheme.typography.headlineSmall)
        state.general?.let { Banner(it) }
        if (model.roleChoices.isNotEmpty()) Text(stringResource(Res.string.field_roles), style = MaterialTheme.typography.titleSmall)
        model.roleChoices.forEach { choice ->
            val selected = choice.role in state.roles
            Row(
                Modifier.heightIn(min = 44.dp).toggleable(
                    value = selected,
                    enabled = choice.editable && !state.saving,
                    role = Role.Checkbox,
                    onValueChange = { model.role(choice.role, it) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = choice.editable)
                Text(
                    roleText(choice.role),
                    Modifier.padding(start = 8.dp),
                    color = if (choice.editable) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        RoleCheckbox(
            stringResource(sectionlessReporterLabel),
            state.sectionlessReporter,
            editable = model.mayAssignSectionlessReporter && !state.saving,
            readOnly = !model.mayAssignSectionlessReporter,
        ) { model.sectionlessReporter(it) }
        state.errors[AccountField.ROLES]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.errors[AccountField.SECTIONLESS_REPORTER]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (model.sectionChoices.isNotEmpty()) Text(stringResource(Res.string.field_section_roles), style = MaterialTheme.typography.titleSmall)
        model.sectionChoices.forEach { choice ->
            SectionRoleRow(choice.section, state.sectionRoles[choice.section.id], editable = choice.editable) {
                model.sectionRole(choice.section.id, it)
            }
        }
        state.errors[AccountField.SECTION_ROLES]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (readOnly) Text(stringResource(Res.string.roles_read_only_hint), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = state.canSave, onClick = { model.submit { onSaved() } }) { Text(stringResource(Res.string.save)) }
            OutlinedButton(onClick = onBack) { Text(stringResource(Res.string.cancel)) }
        }
    }
}

/** A role as checkbox; [readOnly] greys it out for a role the user may not change. */
@Composable
private fun RoleCheckbox(label: String, selected: Boolean, editable: Boolean, readOnly: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).toggleable(value = selected, enabled = editable, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null, enabled = !readOnly)
        Text(
            label,
            Modifier.padding(start = 8.dp),
            color = if (readOnly) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.Unspecified,
        )
    }
}

/** A section with the choice "none" or one of its assignable roles; when not [editable], only the [selected] role. */
@Composable
private fun SectionRoleRow(section: SectionDto, selected: String?, editable: Boolean = true, onChoose: (String?) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorMarker(section.color)
            Text(section.name, style = MaterialTheme.typography.bodyLarge)
        }
        if (!editable) {
            Text(selected?.let { sectionRoleText(it) } ?: stringResource(Res.string.section_role_none), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            return@FlowRow
        }
        (listOf<String?>(null) + section.assignableRoles).forEach { role ->
            val label = role?.let { sectionRoleText(it) } ?: stringResource(Res.string.section_role_none)
            if (role == selected) {
                FilledTonalButton(onClick = {}) { Text(label) }
            } else {
                OutlinedButton(onClick = { onChoose(role) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun FormField(value: String, onChange: (String) -> Unit, label: String, error: String?, hint: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { changed -> onChange(changed.filterNot { it == '\n' || it == '\r' }) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The slip after a creation or a password reset (design D9/D10): the password lives only in [created] and is gone once
 * the user leaves this screen.
 */
@Composable
fun AccountSlipScreen(newspaperName: String, siteUrl: String, created: CreatedAccountDto, printer: SlipPrinter, onDone: () -> Unit) {
    val heading = stringResource(Res.string.slip_heading)
    val rows = listOf(
        stringResource(Res.string.slip_address) to siteUrl,
        stringResource(Res.string.slip_username) to created.account.username,
        stringResource(Res.string.slip_password) to created.password,
    )
    val note = stringResource(Res.string.slip_note)
    val qrPayload = SlipQr.payload(siteUrl, created.account.username, created.password)
    val qrCode = remember(qrPayload) { QrCode.encode(qrPayload) }
    val qrHint = stringResource(Res.string.slip_qr_hint)

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            FlowRow(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(newspaperName, style = MaterialTheme.typography.headlineMedium)
                        Text(heading, style = MaterialTheme.typography.titleMedium)
                        rows.forEachIndexed { index, (label, value) ->
                            Column {
                                Text(label, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    value,
                                    style = if (index == rows.lastIndex) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QrImage(qrCode, Modifier.size(180.dp))
                    Text(qrHint, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Banner(note, color = MaterialTheme.colorScheme.secondaryContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { printer.print(PrintableSlip(newspaperName, heading, rows, rows.lastIndex, note, qrPayload, qrHint)) }) {
                Text(stringResource(Res.string.print))
            }
            OutlinedButton(onClick = onDone) { Text(stringResource(Res.string.done)) }
        }
    }
}

/** [code] with its quiet zone, black on white regardless of the theme so that scanners read it; modules snap to whole pixels. */
@Composable
private fun QrImage(code: QrCode, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White)) {
        val modules = code.size + 2 * QrCode.QUIET_ZONE
        val cell = maxOf(1f, floor(size.minDimension / modules))
        val origin = (size.minDimension - cell * modules) / 2 + cell * QrCode.QUIET_ZONE
        for (y in 0 until code.size) {
            for ((start, length) in code.darkRuns(y)) {
                drawRect(Color.Black, topLeft = Offset(origin + start * cell, origin + y * cell), size = Size(length * cell, cell))
            }
        }
    }
}
