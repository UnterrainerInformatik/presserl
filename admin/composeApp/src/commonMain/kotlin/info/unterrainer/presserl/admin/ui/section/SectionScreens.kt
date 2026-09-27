package info.unterrainer.presserl.admin.ui.section

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.MemberDto
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionListDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.add
import info.unterrainer.presserl.admin.resources.add_member
import info.unterrainer.presserl.admin.resources.cancel
import info.unterrainer.presserl.admin.resources.choose_account
import info.unterrainer.presserl.admin.resources.delete
import info.unterrainer.presserl.admin.resources.delete_section_text
import info.unterrainer.presserl.admin.resources.delete_section_title
import info.unterrainer.presserl.admin.resources.edit
import info.unterrainer.presserl.admin.resources.edit_section
import info.unterrainer.presserl.admin.resources.field_color
import info.unterrainer.presserl.admin.resources.field_section_name
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.move_down
import info.unterrainer.presserl.admin.resources.move_up
import info.unterrainer.presserl.admin.resources.new_section
import info.unterrainer.presserl.admin.resources.no_members
import info.unterrainer.presserl.admin.resources.no_sections
import info.unterrainer.presserl.admin.resources.remove
import info.unterrainer.presserl.admin.resources.remove_member_text
import info.unterrainer.presserl.admin.resources.remove_member_title
import info.unterrainer.presserl.admin.resources.save
import info.unterrainer.presserl.admin.resources.section_members
import info.unterrainer.presserl.admin.resources.section_not_empty
import info.unterrainer.presserl.admin.ui.BackButton
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.LoadFailed
import info.unterrainer.presserl.admin.ui.attempt
import info.unterrainer.presserl.admin.ui.colorText
import info.unterrainer.presserl.admin.ui.sectionRoleText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The sections by position. With `canManage` it offers "New section", "Edit", "Delete" (after an
 * in-app confirmation) and moving; a section whose `assignableRoles` is not empty opens its members.
 */
@Composable
fun SectionListScreen(
    api: ApiClient,
    onNew: (defaultColor: String) -> Unit,
    onEdit: (SectionDto) -> Unit,
    onOpen: (SectionDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<SectionListDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var moveError by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SectionDto?>(null) }
    var deleted by remember { mutableStateOf<Pair<SectionDto, SectionDeleteResult>?>(null) }

    LaunchedEffect(loads) {
        error = null
        list = attempt({ error = it }) { api.sections() }
    }

    fun move(index: Int, delta: Int) {
        val sections = list?.sections ?: return
        val ids = sections.map { it.id }.toMutableList()
        ids.add(index + delta, ids.removeAt(index))
        busy = true
        moveError = null
        deleted = null
        scope.launch {
            val reordered = attempt({ moveError = it }) { api.reorderSections(ids) }
            busy = false
            if (reordered != null) list = reordered else loads++
        }
    }

    fun delete(section: SectionDto) {
        busy = true
        moveError = null
        deleted = null
        scope.launch {
            deleted = section to deleteSection { api.deleteSection(section.id) }
            busy = false
            loads++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val current = list
        if (current?.canManage == true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onNew(defaultSectionColor(current.sections.size)) }) {
                    Text(stringResource(Res.string.new_section))
                }
            }
        }
        moveError?.let { Banner(it) }
        deleted?.let { (section, result) ->
            when (result) {
                SectionDeleteResult.Deleted -> {}
                SectionDeleteResult.NotEmpty -> Banner(stringResource(Res.string.section_not_empty, section.name))
                is SectionDeleteResult.Failed -> Banner(result.message)
            }
        }
        when {
            error != null -> LoadFailed(error!!, onReload = { loads++ })
            current == null -> Text(stringResource(Res.string.loading))
            current.sections.isEmpty() -> Text(stringResource(Res.string.no_sections))
            else -> LazyColumn {
                itemsIndexed(current.sections, key = { _, section -> section.id }) { index, section ->
                    SectionRow(
                        section,
                        canManage = current.canManage,
                        first = index == 0,
                        last = index == current.sections.lastIndex,
                        enabled = !busy,
                        onOpen = { onOpen(section) }.takeIf { section.assignableRoles.isNotEmpty() },
                        onEdit = { onEdit(section) },
                        onDelete = { confirmDelete = section },
                        onMove = { delta -> move(index, delta) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    confirmDelete?.let { section ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(Res.string.delete_section_title)) },
            text = { Text(stringResource(Res.string.delete_section_text, section.name)) },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = null
                    delete(section)
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionRow(
    section: SectionDto,
    canManage: Boolean,
    first: Boolean,
    last: Boolean,
    enabled: Boolean,
    onOpen: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (delta: Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val open = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).then(open).padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColorMarker(section.color)
            Text(section.name, style = MaterialTheme.typography.titleMedium)
        }
        if (canManage) {
            TextButton(onClick = { onMove(-1) }, enabled = enabled && !first) { Text("↑ " + stringResource(Res.string.move_up)) }
            TextButton(onClick = { onMove(1) }, enabled = enabled && !last) { Text("↓ " + stringResource(Res.string.move_down)) }
            OutlinedButton(onClick = onEdit) { Text(stringResource(Res.string.edit)) }
            OutlinedButton(onClick = onDelete, enabled = enabled) { Text(stringResource(Res.string.delete)) }
        }
    }
}

/** "New section" ([section] `null`) or "Edit"; [onSaved] follows a successful save. */
@Composable
fun SectionFormScreen(api: ApiClient, section: SectionDto?, defaultColor: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(section) {
        SectionFormModel(scope, section, defaultColor) { request ->
            if (section == null) api.createSection(request) else api.updateSection(section.id, request)
        }
    }
    val state by model.state.collectAsState()

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text(
            stringResource(if (section == null) Res.string.new_section else Res.string.edit_section),
            style = MaterialTheme.typography.headlineSmall,
        )
        state.general?.let { Banner(it) }
        val nameError = state.errors[SectionField.NAME]
        OutlinedTextField(
            value = state.name,
            onValueChange = { changed -> model.name(changed.filterNot { it == '\n' || it == '\r' }) },
            label = { Text(stringResource(Res.string.field_section_name)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.field_color), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SECTION_COLORS.forEach { color -> Swatch(color, selected = color == state.color, onClick = { model.color(color) }) }
        }
        state.errors[SectionField.COLOR]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(enabled = state.canSave, onClick = { model.submit { onSaved() } }) { Text(stringResource(Res.string.save)) }
    }
}

@Composable
private fun Swatch(color: String, selected: Boolean, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .border(2.dp, outline, RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorMarker(color, size = 24.dp)
        Text(colorText(color))
    }
}

/** Members of [section]: add, change role and remove (after an in-app confirmation). */
@Composable
fun SectionMembersScreen(api: ApiClient, section: SectionDto, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(section.id) {
        SectionMembersModel(
            scope,
            loadMembers = { api.members(section.id) },
            loadAccounts = { api.accounts().accounts },
            assign = { accountId, role -> api.assignMember(section.id, accountId, role) },
            remove = { accountId -> api.removeMember(section.id, accountId) },
        )
    }
    val state by model.state.collectAsState()
    var confirmRemove by remember { mutableStateOf<MemberDto?>(null) }

    LaunchedEffect(model) { model.load() }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorMarker(section.color, size = 20.dp)
            Text(stringResource(Res.string.section_members, section.name), style = MaterialTheme.typography.headlineSmall)
        }
        state.error?.let { Banner(it) }
        when {
            !state.loaded -> if (state.error == null) Text(stringResource(Res.string.loading))
            else -> {
                if (state.members.isEmpty()) Text(stringResource(Res.string.no_members))
                state.members.forEach { member ->
                    MemberRow(
                        member,
                        state.assignableRoles.takeIf { state.canChange(member) } ?: emptyList(),
                        enabled = !state.busy,
                        onRole = { role -> model.assign(member.accountId, role) },
                        onRemove = { confirmRemove = member },
                    )
                    HorizontalDivider()
                }
                if (state.assignableRoles.isNotEmpty()) {
                    AddMember(state.candidates, state.assignableRoles, enabled = !state.busy, onAdd = model::assign)
                }
            }
        }
    }

    confirmRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(Res.string.remove_member_title)) },
            text = { Text(stringResource(Res.string.remove_member_text, member.username, sectionRoleText(member.role), section.name)) },
            confirmButton = {
                Button(onClick = {
                    confirmRemove = null
                    model.remove(member.accountId)
                }) { Text(stringResource(Res.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun MemberRow(member: MemberDto, roles: List<String>, enabled: Boolean, onRole: (String) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(member.username, style = MaterialTheme.typography.titleMedium)
        val names = listOf(member.firstName, member.lastName).filter { it.isNotEmpty() }.joinToString(" ")
        Text(listOf(names, sectionRoleText(member.role)).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        if (roles.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleChoice(roles, member.role, enabled, onRole)
                TextButton(onClick = onRemove, enabled = enabled) { Text("✕ " + stringResource(Res.string.remove)) }
            }
        }
    }
}

/** One button per role; the chosen one is highlighted. */
@Composable
fun RoleChoice(roles: List<String>, selected: String?, enabled: Boolean, onRole: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        roles.forEach { role ->
            if (role == selected) {
                FilledTonalButton(onClick = {}, enabled = enabled) { Text(sectionRoleText(role)) }
            } else {
                OutlinedButton(onClick = { onRole(role) }, enabled = enabled) { Text(sectionRoleText(role)) }
            }
        }
    }
}

@Composable
private fun AddMember(candidates: List<AccountDto>, roles: List<String>, enabled: Boolean, onAdd: (accountId: String, role: String) -> Unit) {
    var account by remember { mutableStateOf<AccountDto?>(null) }
    var role by remember { mutableStateOf(roles.last()) }
    var open by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(stringResource(Res.string.add_member), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled && candidates.isNotEmpty()) {
                Text(account?.let { label(it) } ?: stringResource(Res.string.choose_account))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                candidates.forEach { candidate ->
                    DropdownMenuItem(text = { Text(label(candidate)) }, onClick = {
                        account = candidate
                        open = false
                    })
                }
            }
        }
        RoleChoice(roles, role, enabled) { role = it }
        Button(
            enabled = enabled && account != null && account in candidates,
            onClick = {
                account?.let { onAdd(it.id, role) }
                account = null
            },
        ) { Text(stringResource(Res.string.add)) }
    }
}

private fun label(account: AccountDto): String =
    listOf(account.username, listOf(account.firstName, account.lastName).filter { it.isNotEmpty() }.joinToString(" "))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
