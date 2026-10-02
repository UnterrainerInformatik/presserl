package info.unterrainer.presserl.admin.ui.issue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.IssueDetailDto
import info.unterrainer.presserl.admin.api.IssueDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.add_articles
import info.unterrainer.presserl.admin.resources.cancel
import info.unterrainer.presserl.admin.resources.candidate_in_issue
import info.unterrainer.presserl.admin.resources.choose_date
import info.unterrainer.presserl.admin.resources.clear_date
import info.unterrainer.presserl.admin.resources.create
import info.unterrainer.presserl.admin.resources.date_picker_confirm
import info.unterrainer.presserl.admin.resources.delete
import info.unterrainer.presserl.admin.resources.delete_issue_text
import info.unterrainer.presserl.admin.resources.delete_issue_title
import info.unterrainer.presserl.admin.resources.field_publication_date
import info.unterrainer.presserl.admin.resources.issue_article_count
import info.unterrainer.presserl.admin.resources.issue_articles
import info.unterrainer.presserl.admin.resources.issue_label
import info.unterrainer.presserl.admin.resources.issue_live
import info.unterrainer.presserl.admin.resources.issue_live_switch
import info.unterrainer.presserl.admin.resources.issue_newest
import info.unterrainer.presserl.admin.resources.issue_newest_hint
import info.unterrainer.presserl.admin.resources.issue_no_articles
import info.unterrainer.presserl.admin.resources.issue_no_date
import info.unterrainer.presserl.admin.resources.issue_not_live
import info.unterrainer.presserl.admin.resources.lead_story
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.move_down
import info.unterrainer.presserl.admin.resources.move_up
import info.unterrainer.presserl.admin.resources.new_issue
import info.unterrainer.presserl.admin.resources.no_candidates
import info.unterrainer.presserl.admin.resources.no_headline
import info.unterrainer.presserl.admin.resources.no_issues
import info.unterrainer.presserl.admin.resources.not_shown_to_readers
import info.unterrainer.presserl.admin.resources.open_issue_in_reader
import info.unterrainer.presserl.admin.resources.open_issue_print
import info.unterrainer.presserl.admin.resources.publication_date_hint
import info.unterrainer.presserl.admin.resources.publication_date_invalid
import info.unterrainer.presserl.admin.resources.remove
import info.unterrainer.presserl.admin.resources.save_date
import info.unterrainer.presserl.admin.ui.BackButton
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.IconLabel
import info.unterrainer.presserl.admin.ui.Icons
import info.unterrainer.presserl.admin.ui.LoadFailed
import info.unterrainer.presserl.admin.ui.SymbolIcon
import info.unterrainer.presserl.admin.ui.formatDate
import info.unterrainer.presserl.admin.ui.section.ColorMarker
import info.unterrainer.presserl.admin.ui.issueWaitText
import info.unterrainer.presserl.admin.ui.statusText
import org.jetbrains.compose.resources.stringResource

/** The issues, highest number first; "New issue" asks for an optional date and opens the created issue. */
@Composable
fun IssueListScreen(api: ApiClient, onOpen: (issueId: Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember { IssueListModel(scope, loadIssues = { api.issues() }, createIssue = { api.createIssue(it) }) }
    val state by model.state.collectAsState()

    LaunchedEffect(model) { model.load() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = model::openNew, enabled = !state.busy) { Text(stringResource(Res.string.new_issue)) }
        }
        when {
            !state.loaded && state.error != null -> LoadFailed(state.error!!, onReload = model::load)
            !state.loaded -> Text(stringResource(Res.string.loading))
            else -> {
                state.error?.let { Banner(it) }
                if (state.issues.isEmpty()) {
                    Text(stringResource(Res.string.no_issues))
                } else {
                    LazyColumn {
                        items(state.issues, key = { it.id }) { issue ->
                            IssueRow(issue, onClick = { onOpen(issue.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    state.newDate?.let { date ->
        AlertDialog(
            onDismissRequest = model::cancelNew,
            title = { Text(stringResource(Res.string.new_issue)) },
            text = { DateField(date, state.newDateInvalid, onChange = model::newDate) },
            confirmButton = {
                Button(onClick = { model.submitNew { onOpen(it.id) } }, enabled = !state.busy) {
                    Text(stringResource(Res.string.create))
                }
            },
            dismissButton = { TextButton(onClick = model::cancelNew) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun IssueRow(issue: IssueDto, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.issue_label, issue.number), style = MaterialTheme.typography.titleMedium)
            LiveChip(issue.published)
            if (issue.newest) AssistChip(onClick = onClick, label = { Text(stringResource(Res.string.issue_newest)) })
        }
        Text(
            listOf(
                issue.publicationDate?.let(::formatDate) ?: stringResource(Res.string.issue_no_date),
                stringResource(Res.string.issue_article_count, issue.articleCount),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (issue.newest) Text(stringResource(Res.string.issue_newest_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LiveChip(live: Boolean) {
    Text(
        stringResource(if (live) Res.string.issue_live else Res.string.issue_not_live),
        style = MaterialTheme.typography.labelLarge,
        color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DateField(value: String, invalid: Boolean, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { changed -> onChange(changed.filterNot { it == '\n' || it == '\r' }) },
        label = { Text(stringResource(Res.string.field_publication_date)) },
        singleLine = true,
        isError = invalid,
        supportingText = {
            Text(stringResource(if (invalid) Res.string.publication_date_invalid else Res.string.publication_date_hint))
        },
        // typing keeps working; the calendar is the easier way on a phone (design D6)
        trailingIcon = {
            IconButton(onClick = { picking = true }) {
                SymbolIcon(Icons.Calendar, size = 24.dp, contentDescription = stringResource(Res.string.choose_date))
            }
        },
        modifier = modifier,
    )
    if (picking) DatePickerFor(value, onPicked = onChange, onClose = { picking = false })
}

/** A date picker preselected with [value]; empty or invalid opens at the current month with nothing selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerFor(value: String, onPicked: (String) -> Unit, onClose: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (parseDate(value) as? DateInput.Valid)?.let { isoToEpochMillis(it.iso) },
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPicked(epochMillisToIso(it)) }
                    onClose()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.date_picker_confirm)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.cancel)) } },
    ) {
        // the default headline ("Ausgewähltes Datum" in headlineLarge) is cut off on a phone
        DatePicker(
            state,
            headline = {
                Text(
                    state.selectedDateMillis?.let { formatDate(epochMillisToIso(it)) }.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
        )
    }
}

/**
 * One issue: date, live switch, reader links while live, delete while not live, and its articles in order. [siteUrl]
 * is the reader's origin; [onDeleted] returns to the list.
 */
@Composable
fun IssueDetailScreen(api: ApiClient, issueId: Long, siteUrl: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(issueId) {
        IssueDetailModel(
            scope,
            IssueCalls(
                load = { api.issue(issueId) },
                setDate = { api.updateIssueDate(issueId, it) },
                publish = { api.publishIssue(issueId) },
                unpublish = { api.unpublishIssue(issueId) },
                setArticles = { api.setIssueArticles(issueId, it) },
                delete = { api.deleteIssue(issueId) },
                articles = { api.articles() },
            ),
        )
    }
    val state by model.state.collectAsState()

    LaunchedEffect(model) { model.load() }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        val issue = state.issue
        if (issue == null) {
            state.error?.let { LoadFailed(it, onReload = model::load) } ?: Text(stringResource(Res.string.loading))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.issue_label, issue.number), style = MaterialTheme.typography.headlineSmall)
            LiveChip(issue.published)
        }
        state.error?.let { Banner(it) }
        IssueSettings(issue, state, model, siteUrl)
        Text(stringResource(Res.string.issue_articles), style = MaterialTheme.typography.titleMedium)
        if (issue.articles.isEmpty()) Text(stringResource(Res.string.issue_no_articles))
        issue.articles.forEachIndexed { index, article ->
            IssueArticleRow(
                article,
                lead = index == 0,
                first = index == 0,
                last = index == issue.articles.lastIndex,
                enabled = !state.busy,
                onUp = { model.moveUp(index) },
                onDown = { model.moveDown(index) },
                onRemove = { model.remove(article.id) },
            )
            HorizontalDivider()
        }
        OutlinedButton(onClick = model::openPicker, enabled = !state.busy) { Text(stringResource(Res.string.add_articles)) }
    }

    state.candidates?.let { candidates ->
        AlertDialog(
            onDismissRequest = model::closePicker,
            title = { Text(stringResource(Res.string.add_articles)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) Text(stringResource(Res.string.no_candidates))
                    candidates.forEach { candidate ->
                        CandidateRow(candidate, onClick = { model.add(candidate) })
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = model::closePicker) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = model::cancelDelete,
            title = { Text(stringResource(Res.string.delete_issue_title)) },
            text = { Text(stringResource(Res.string.delete_issue_text, state.issue?.number ?: 0)) },
            confirmButton = { Button(onClick = { model.confirmDelete(onDeleted) }) { Text(stringResource(Res.string.delete)) } },
            dismissButton = { TextButton(onClick = model::cancelDelete) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun IssueSettings(issue: IssueDetailDto, state: IssueDetailState, model: IssueDetailModel, siteUrl: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        DateField(state.dateText, state.dateInvalid, onChange = model::dateText)
        issue.publicationDate?.let { Text(formatDate(it), style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = model::saveDate, enabled = !state.busy) { Text(stringResource(Res.string.save_date)) }
        TextButton(onClick = model::clearDate, enabled = !state.busy && issue.publicationDate != null) {
            Text(stringResource(Res.string.clear_date))
        }
    }
    Row(
        Modifier.heightIn(min = 44.dp).toggleable(
            value = issue.published,
            enabled = !state.busy,
            role = Role.Switch,
            onValueChange = model::setLive,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = issue.published, onCheckedChange = null, enabled = !state.busy)
        Text(stringResource(Res.string.issue_live_switch), Modifier.padding(start = 8.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (issue.published) {
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri("$siteUrl/issues/${issue.id}") }) {
                IconLabel(Icons.OpenInNew, stringResource(Res.string.open_issue_in_reader), iconAfter = true)
            }
            TextButton(onClick = { uriHandler.openUri("$siteUrl/print/issue/${issue.id}") }) {
                IconLabel(Icons.OpenInNew, stringResource(Res.string.open_issue_print), iconAfter = true)
            }
        }
        if (state.canDelete) {
            OutlinedButton(onClick = model::requestDelete, enabled = !state.busy) { Text(stringResource(Res.string.delete)) }
        }
    }
}

@Composable
private fun IssueArticleRow(
    article: ArticleSummaryDto,
    lead: Boolean,
    first: Boolean,
    last: Boolean,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(article.headline.ifEmpty { stringResource(Res.string.no_headline) }, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            article.section?.let { ColorMarker(it.color, size = 12.dp) }
            Text(
                listOfNotNull(
                    stringResource(Res.string.lead_story).takeIf { lead },
                    article.section?.name,
                    statusText(article.status),
                    issueWaitText(article.status, article.readerVisible, article.issue?.number)
                        ?: stringResource(Res.string.not_shown_to_readers).takeIf { article.status != "PUBLISHED" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onUp, enabled = enabled && !first) { IconLabel(Icons.Up, stringResource(Res.string.move_up)) }
            TextButton(onClick = onDown, enabled = enabled && !last) { IconLabel(Icons.Down, stringResource(Res.string.move_down)) }
            TextButton(onClick = onRemove, enabled = enabled) { IconLabel(Icons.Close, stringResource(Res.string.remove)) }
        }
    }
}

@Composable
private fun CandidateRow(article: ArticleSummaryDto, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(article.headline.ifEmpty { stringResource(Res.string.no_headline) }, style = MaterialTheme.typography.titleSmall)
        Text(
            listOfNotNull(
                article.section?.name,
                statusText(article.status),
                issueWaitText(article.status, article.readerVisible, article.issue?.number),
                article.issue?.let { stringResource(Res.string.candidate_in_issue, it.number) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
