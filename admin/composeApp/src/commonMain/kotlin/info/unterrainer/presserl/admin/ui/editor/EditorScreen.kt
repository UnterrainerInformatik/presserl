package info.unterrainer.presserl.admin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.ReviewDto
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionRefDto
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.article.replaceInRuns
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.correcting_notice
import info.unterrainer.presserl.admin.resources.last_changed_by
import info.unterrainer.presserl.admin.resources.show_changes
import info.unterrainer.presserl.admin.resources.action_failed
import info.unterrainer.presserl.admin.resources.add_block
import info.unterrainer.presserl.admin.resources.add_item
import info.unterrainer.presserl.admin.resources.approve
import info.unterrainer.presserl.admin.resources.block_image
import info.unterrainer.presserl.admin.resources.block_list
import info.unterrainer.presserl.admin.resources.block_paragraph
import info.unterrainer.presserl.admin.resources.block_quote
import info.unterrainer.presserl.admin.resources.block_subhead
import info.unterrainer.presserl.admin.resources.bold
import info.unterrainer.presserl.admin.resources.cancel
import info.unterrainer.presserl.admin.resources.choose_section
import info.unterrainer.presserl.admin.resources.conflict_text
import info.unterrainer.presserl.admin.resources.delete
import info.unterrainer.presserl.admin.resources.delete_text
import info.unterrainer.presserl.admin.resources.delete_title
import info.unterrainer.presserl.admin.resources.empty_body
import info.unterrainer.presserl.admin.resources.field_caption
import info.unterrainer.presserl.admin.resources.field_headline
import info.unterrainer.presserl.admin.resources.field_kicker
import info.unterrainer.presserl.admin.resources.field_lead
import info.unterrainer.presserl.admin.resources.field_lead_image
import info.unterrainer.presserl.admin.resources.lead_image_choose
import info.unterrainer.presserl.admin.resources.lead_image_remove
import info.unterrainer.presserl.admin.resources.lead_image_replace
import info.unterrainer.presserl.admin.resources.field_section
import info.unterrainer.presserl.admin.resources.field_subheadline
import info.unterrainer.presserl.admin.resources.leave
import info.unterrainer.presserl.admin.resources.leave_text
import info.unterrainer.presserl.admin.resources.leave_title
import info.unterrainer.presserl.admin.resources.list_item
import info.unterrainer.presserl.admin.resources.load_current
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.locked_notice
import info.unterrainer.presserl.admin.resources.move_down
import info.unterrainer.presserl.admin.resources.move_up
import info.unterrainer.presserl.admin.resources.publish
import info.unterrainer.presserl.admin.resources.read_only
import info.unterrainer.presserl.admin.resources.redo
import info.unterrainer.presserl.admin.resources.reject
import info.unterrainer.presserl.admin.resources.reject_confirm
import info.unterrainer.presserl.admin.resources.reject_hint
import info.unterrainer.presserl.admin.resources.reject_title
import info.unterrainer.presserl.admin.resources.rejected_by
import info.unterrainer.presserl.admin.resources.remove_block
import info.unterrainer.presserl.admin.resources.remove_item
import info.unterrainer.presserl.admin.resources.reviews_heading
import info.unterrainer.presserl.admin.resources.revisions
import info.unterrainer.presserl.admin.resources.save_failed
import info.unterrainer.presserl.admin.resources.save_invalid
import info.unterrainer.presserl.admin.resources.save_pending
import info.unterrainer.presserl.admin.resources.save_saved
import info.unterrainer.presserl.admin.resources.save_saving
import info.unterrainer.presserl.admin.resources.stay
import info.unterrainer.presserl.admin.resources.submit
import info.unterrainer.presserl.admin.resources.take_offline
import info.unterrainer.presserl.admin.resources.undo
import info.unterrainer.presserl.admin.resources.unlock
import info.unterrainer.presserl.admin.resources.unpublished_changes
import info.unterrainer.presserl.admin.resources.view_in_reader
import info.unterrainer.presserl.admin.resources.withdraw
import info.unterrainer.presserl.admin.ui.ArticleView
import info.unterrainer.presserl.admin.ui.approvalLevelText
import info.unterrainer.presserl.admin.ui.BackButton
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.IconLabel
import info.unterrainer.presserl.admin.ui.Icons
import info.unterrainer.presserl.admin.ui.LoadFailed
import info.unterrainer.presserl.admin.ui.attempt
import info.unterrainer.presserl.admin.ui.decisionText
import info.unterrainer.presserl.admin.ui.describe
import info.unterrainer.presserl.admin.ui.formatTimestamp
import info.unterrainer.presserl.admin.ui.media.LeadImagePreview
import info.unterrainer.presserl.admin.ui.media.MediaPickerDialog
import info.unterrainer.presserl.admin.ui.media.MediaPreview
import info.unterrainer.presserl.admin.ui.media.PictureIcon
import info.unterrainer.presserl.admin.ui.media.Thumbnails
import info.unterrainer.presserl.admin.ui.section.ColorMarker
import info.unterrainer.presserl.admin.ui.spell.CheckField
import info.unterrainer.presserl.admin.ui.spell.SpellChecker
import info.unterrainer.presserl.admin.ui.spell.SpellCheckedTextField
import info.unterrainer.presserl.admin.ui.spell.SpellNotice
import info.unterrainer.presserl.admin.ui.spell.SpellSuggestionRow
import info.unterrainer.presserl.admin.ui.spell.findingAt
import info.unterrainer.presserl.admin.ui.spell.spellChecker
import info.unterrainer.presserl.admin.ui.spell.spellMarksOverlay
import info.unterrainer.presserl.admin.ui.statusText
import info.unterrainer.presserl.admin.ui.SymbolIcon
import info.unterrainer.presserl.admin.ui.waitingText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

/**
 * The article editor at level `standard`. [readerUrl] gives the reader page of an article;
 * [onBack], [onRevisions] and [onShowChanges] (the comparison of a revision with its predecessor) are called once
 * pending changes are saved.
 * [username] is the logged-in user's, to tell a correction of someone else's article.
 */
@Composable
fun EditorScreen(
    api: ApiClient,
    articleId: Long,
    readerUrl: (Long) -> String,
    onBack: () -> Unit,
    onRevisions: () -> Unit,
    spellCheck: Boolean = false,
    username: String = "",
    onShowChanges: (revision: Int) -> Unit = {},
) {
    // A new load (after a conflict) starts with fresh editor state
    var loads by remember { mutableStateOf(0) }
    key(loads) {
        var article by remember { mutableStateOf<ArticleDto?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { article = attempt({ error = it }) { api.article(articleId) } }
        val loaded = article
        when {
            error != null -> Column {
                BackButton(onBack)
                LoadFailed(error!!, onReload = { loads++ })
            }
            loaded == null -> Text(stringResource(Res.string.loading))
            else -> Editor(
                api, loaded, readerUrl, spellCheck, username, onBack, onRevisions, onShowChanges,
                onReload = { loads++ },
            )
        }
    }
}

@Composable
private fun Editor(
    api: ApiClient,
    loaded: ArticleDto,
    readerUrl: (Long) -> String,
    spellCheck: Boolean,
    username: String,
    onBack: () -> Unit,
    onRevisions: () -> Unit,
    onShowChanges: (Int) -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var article by remember { mutableStateOf(loaded) }
    val model = remember {
        val ids = IdSource()
        val start = TimeSource.Monotonic.markNow()
        EditorModel(draftOf(loaded, ids), ids, clock = { start.elapsedNow().inWholeMilliseconds })
    }
    val autosaver = remember {
        Autosaver(
            scope,
            saved = model.draft.toContent(),
            version = loaded.version,
            save = { content, version -> api.updateArticle(loaded.id, content, version) },
            onSaved = { article = it },
        )
    }
    val thumbnails = remember { Thumbnails { api.mediaRendition(it, "thumbnail") } }
    // Only the editable fields ask; a read-only article composes none
    val checker = remember { spellChecker(scope, spellCheck, api) }
    val actions = actionsFor(article.allowedActions)
    val notice = correctionNotice(article, username)
    // Loaded once for the chooser; without them the chooser shows the current section only
    var sections by remember { mutableStateOf<List<SectionDto>?>(null) }
    if (actions.editable) LaunchedEffect(Unit) { sections = attempt({}) { api.sections().sections } }
    val saveState by autosaver.state.collectAsState()
    var actionErrors by remember { mutableStateOf(FieldErrors()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    // The reject dialog is open while rejectNote is not null
    var rejectNote by remember { mutableStateOf<String?>(null) }
    var rejectError by remember { mutableStateOf<String?>(null) }
    var reviews by remember { mutableStateOf<List<ReviewDto>>(emptyList()) }
    var reviewLoads by remember { mutableStateOf(0) }
    LaunchedEffect(reviewLoads) { attempt({}) { api.reviews(loaded.id) }?.let { reviews = it } }

    if (actions.editable) {
        LaunchedEffect(model) {
            snapshotFlow { model.draft }.collect { autosaver.changed(it.toContent()) }
        }
    }

    /** Saves pending changes, then goes on; asks first if they cannot be saved. */
    fun leave(then: () -> Unit) {
        scope.launch {
            if (!actions.editable || autosaver.flush() || autosaver.state.value == SaveState.Conflict) then() else confirmLeave = then
        }
    }

    fun act(saveFirst: Boolean, action: suspend () -> ArticleDto) {
        busy = true
        actionErrors = FieldErrors()
        scope.launch {
            try {
                if (!saveFirst || autosaver.flush()) {
                    val updated = action()
                    article = updated
                    autosaver.versionChanged(updated.version)
                    reviewLoads++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                actionErrors = fieldErrorsOf(e) ?: FieldErrors(general = listOf(describe(e)))
            } finally {
                busy = false
            }
        }
    }

    /**
     * Approve and reject: pending changes are saved first and the decision carries the version the editor holds; a
     * `409` shows the conflict notice.
     */
    fun decide(decision: suspend (version: Long) -> ArticleDto) {
        busy = true
        actionErrors = FieldErrors()
        scope.launch {
            try {
                autosaver.decide(decision)?.let { updated ->
                    article = updated
                    reviewLoads++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                actionErrors = fieldErrorsOf(e) ?: FieldErrors(general = listOf(describe(e)))
            } finally {
                busy = false
            }
        }
    }

    /** Rejects with the note; a refused note keeps the dialog open with the server's message. */
    fun reject(note: String) {
        busy = true
        rejectError = null
        scope.launch {
            try {
                val updated = autosaver.decide { version -> api.rejectArticle(article.id, note, version) }
                if (updated != null) {
                    article = updated
                    reviewLoads++
                }
                rejectNote = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val noteError = noteErrorOf(e)
                if (noteError != null) {
                    rejectError = noteError
                } else {
                    rejectNote = null
                    actionErrors = fieldErrorsOf(e) ?: FieldErrors(general = listOf(describe(e)))
                }
            } finally {
                busy = false
            }
        }
    }

    val saveErrors = (saveState as? SaveState.Invalid)?.errors ?: FieldErrors()
    val errors = FieldErrors(
        header = saveErrors.header + actionErrors.header,
        blocks = saveErrors.blocks + actionErrors.blocks,
        general = saveErrors.general + actionErrors.general,
        section = actionErrors.section ?: saveErrors.section,
        leadImage = actionErrors.leadImage ?: saveErrors.leadImage,
    )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            BackButton { leave(onBack) }
            Text(
                listOfNotNull(statusText(article.status), stringResource(Res.string.unpublished_changes).takeIf { article.hasUnpublishedChanges })
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = { leave(onRevisions) }) { Text(stringResource(Res.string.revisions)) }
            if (article.status == "PUBLISHED") {
                val uriHandler = LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri(readerUrl(article.id)) }) { IconLabel(Icons.OpenInNew, stringResource(Res.string.view_in_reader), iconAfter = true) }
            }
        }
        if (saveState == SaveState.Conflict) {
            Banner(stringResource(Res.string.conflict_text)) {
                Button(onClick = onReload) { Text(stringResource(Res.string.load_current)) }
            }
        }
        errors.general.forEach { Banner(stringResource(Res.string.action_failed, it)) }
        notice.correcting?.let {
            Banner(stringResource(Res.string.correcting_notice, it.displayName), color = MaterialTheme.colorScheme.tertiaryContainer)
        }
        notice.lastChangedBy?.let { editor ->
            Banner(stringResource(Res.string.last_changed_by, editor.displayName), color = MaterialTheme.colorScheme.secondaryContainer) {
                OutlinedButton(onClick = { leave { onShowChanges(article.revision) } }) { Text(stringResource(Res.string.show_changes)) }
            }
        }
        if (article.locked) {
            Banner(stringResource(Res.string.locked_notice), color = MaterialTheme.colorScheme.errorContainer)
        }
        val pendingLevel = article.pendingLevel
        if (pendingLevel != null) {
            Banner(waitingText(pendingLevel), color = MaterialTheme.colorScheme.secondaryContainer)
        } else if (!actions.editable) {
            Banner(stringResource(Res.string.read_only), color = MaterialTheme.colorScheme.secondaryContainer)
        }
        rejectionToShow(reviews, pendingLevel)?.let { rejection ->
            Banner(
                stringResource(Res.string.rejected_by, rejection.reviewer.displayName, rejection.note.orEmpty()),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (actions.editable) {
                // One explanation at a time for the whole editor
                val help = remember { FieldHelpState() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        val section = article.section
                        // A correction keeps the article's section
                        if (notice.correcting != null && section != null) {
                            SectionLabel(section.name, section.color)
                        } else {
                            SectionChooser(model, section, sections.orEmpty(), errors.section, enabled = saveState != SaveState.Conflict)
                        }
                    }
                    FieldHelp(HelpPart.SECTION, help)
                }
                // Where the open media picker hands its image; null while it is closed
                var picking by remember { mutableStateOf<ImageTarget?>(null) }
                val images = ImageSlot(thumbnails, onChoose = { picking = it })
                SpellNotice(checker)
                EditableArticle(model, errors, images, help, checker, enabled = saveState != SaveState.Conflict)
                picking?.let { target ->
                    MediaPickerDialog(
                        api,
                        thumbnails,
                        onPick = { media ->
                            picking = null
                            if (saveState != SaveState.Conflict) model.useImage(target, media)
                        },
                        onDismiss = { picking = null },
                    )
                }
            } else {
                article.section?.let { SectionLabel(it.name, it.color) }
                ArticleView(model.draft, thumbnails)
            }
            if (reviews.isNotEmpty()) Reviews(reviews)
        }

        HorizontalDivider()
        BottomBar(model, actions, saveState, busy,
            onPublish = { act(saveFirst = true) { api.publishArticle(article.id) } },
            onSubmit = { act(saveFirst = true) { api.submitArticle(article.id) } },
            onApprove = { decide { version -> api.approveArticle(article.id, version) } },
            onReject = { rejectNote = ""; rejectError = null },
            onWithdraw = { act(saveFirst = false) { api.withdrawArticle(article.id) } },
            onTakeOffline = { act(saveFirst = false) { api.takeArticleOffline(article.id) } },
            onUnlock = { act(saveFirst = false) { api.unlockArticle(article.id) } },
            onDelete = { confirmDelete = true })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.delete_title)) },
            text = { Text(stringResource(Res.string.delete_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        busy = true
                        scope.launch {
                            val deleted = attempt({ actionErrors = FieldErrors(general = listOf(it)) }) { api.deleteArticle(article.id) }
                            busy = false
                            if (deleted != null) onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    rejectNote?.let { note ->
        val noteChecker = remember { spellChecker(scope, spellCheck, api) }
        AlertDialog(
            onDismissRequest = { if (!busy) rejectNote = null },
            title = { Text(stringResource(Res.string.reject_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpellCheckedTextField(
                        value = note,
                        onChange = { rejectNote = limitNote(it); rejectError = null },
                        checker = noteChecker,
                        key = "rejectNote",
                        label = { Text(stringResource(Res.string.reject_hint)) },
                        isError = rejectError != null,
                        supportingText = rejectError?.let { { Text(it) } },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SpellNotice(noteChecker)
                }
            },
            confirmButton = {
                Button(onClick = { reject(note) }, enabled = !busy && canConfirmReject(note)) {
                    Text(stringResource(Res.string.reject_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { rejectNote = null }, enabled = !busy) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    confirmLeave?.let { then ->
        AlertDialog(
            onDismissRequest = { confirmLeave = null },
            title = { Text(stringResource(Res.string.leave_title)) },
            text = { Text(stringResource(Res.string.leave_text)) },
            confirmButton = { TextButton(onClick = { confirmLeave = null; then() }) { Text(stringResource(Res.string.leave)) } },
            dismissButton = { Button(onClick = { confirmLeave = null }) { Text(stringResource(Res.string.stay)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomBar(
    model: EditorModel,
    actions: EditorActions,
    saveState: SaveState,
    busy: Boolean,
    onPublish: () -> Unit,
    onSubmit: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onWithdraw: () -> Unit,
    onTakeOffline: () -> Unit,
    onUnlock: () -> Unit,
    onDelete: () -> Unit,
) {
    val history = @Composable {
        if (actions.editable) {
            OutlinedButton(onClick = { model.dispatch(EditorIntent.Undo) }, enabled = model.canUndo) { IconLabel(Icons.Undo, stringResource(Res.string.undo)) }
            OutlinedButton(onClick = { model.dispatch(EditorIntent.Redo) }, enabled = model.canRedo) { IconLabel(Icons.Redo, stringResource(Res.string.redo)) }
            Text(saveStateText(saveState), style = MaterialTheme.typography.bodyMedium)
        }
    }
    val articleActions = @Composable {
        if (actions.delete) {
            TextButton(onClick = onDelete, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(Res.string.delete))
            }
        }
        if (actions.takeOffline) OutlinedButton(onClick = onTakeOffline, enabled = !busy) { Text(stringResource(Res.string.take_offline)) }
        if (actions.unlock) OutlinedButton(onClick = onUnlock, enabled = !busy) { Text(stringResource(Res.string.unlock)) }
        if (actions.withdraw) OutlinedButton(onClick = onWithdraw, enabled = !busy) { Text(stringResource(Res.string.withdraw)) }
        if (actions.reject) OutlinedButton(onClick = onReject, enabled = !busy) { Text(stringResource(Res.string.reject)) }
        if (actions.approve) Button(onClick = onApprove, enabled = !busy) { Text(stringResource(Res.string.approve)) }
        if (actions.submit) Button(onClick = onSubmit, enabled = !busy && saveState != SaveState.Conflict) { Text(stringResource(Res.string.submit)) }
        if (actions.publish) Button(onClick = onPublish, enabled = !busy && saveState != SaveState.Conflict) { Text(stringResource(Res.string.publish)) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (maxWidth < NARROW_BAR) {
            // One wrapping group: side by side the actions would squeeze undo and redo into broken words
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                history()
                articleActions()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    history()
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), itemVerticalAlignment = Alignment.CenterVertically) {
                    articleActions()
                }
            }
        }
    }
}

/** Below this width the bottom bar wraps as one group. */
private val NARROW_BAR = 720.dp

/** The article's approvals and rejections, newest first, with their notes. */
@Composable
private fun Reviews(reviews: List<ReviewDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(stringResource(Res.string.reviews_heading), style = MaterialTheme.typography.titleSmall)
        reviews.forEach { review ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOf(decisionText(review.decision), approvalLevelText(review.level), review.reviewer.displayName,
                        formatTimestamp(review.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (review.decision == "REJECTED") FontWeight.SemiBold else FontWeight.Normal,
                )
                review.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun saveStateText(state: SaveState): String = when (state) {
    SaveState.Saved -> stringResource(Res.string.save_saved)
    SaveState.Pending, SaveState.Conflict -> stringResource(Res.string.save_pending)
    SaveState.Saving -> stringResource(Res.string.save_saving)
    is SaveState.Failed -> stringResource(Res.string.save_failed, state.retryInSeconds)
    is SaveState.Invalid -> stringResource(Res.string.save_invalid)
}

private val HEADER_LABELS = mapOf(
    HeaderField.KICKER to Res.string.field_kicker,
    HeaderField.HEADLINE to Res.string.field_headline,
    HeaderField.SUBHEADLINE to Res.string.field_subheadline,
    HeaderField.LEAD to Res.string.field_lead,
)

private val BLOCK_LABELS = mapOf(
    BlockType.PARAGRAPH to Res.string.block_paragraph,
    BlockType.SUBHEAD to Res.string.block_subhead,
    BlockType.QUOTE to Res.string.block_quote,
    BlockType.LIST to Res.string.block_list,
    BlockType.IMAGE to Res.string.block_image,
)

private val EditorBlock.type: BlockType
    get() = when (this) {
        is EditorBlock.Paragraph -> BlockType.PARAGRAPH
        is EditorBlock.Subhead -> BlockType.SUBHEAD
        is EditorBlock.Quote -> BlockType.QUOTE
        is EditorBlock.BulletList -> BlockType.LIST
        is EditorBlock.Image -> BlockType.IMAGE
    }

/** The section as a colour marker and name, for read-only articles. */
@Composable
private fun SectionLabel(name: String, color: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorMarker(color, size = 12.dp)
        Text(stringResource(Res.string.field_section) + ": " + name, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Chooses the article's section among the [sections] the user may write in (`canWrite`), in position order;
 * [current] is the section as last saved, shown while the list is not loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionChooser(model: EditorModel, current: SectionRefDto?, sections: List<SectionDto>, error: String?, enabled: Boolean) {
    val selectedId = model.draft.sectionId
    val selected = sections.firstOrNull { it.id == selectedId }?.let { it.name to it.color }
        ?: current?.takeIf { it.id == selectedId }?.let { it.name to it.color }
    val writable = sections.filter { it.canWrite }
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it && enabled && writable.isNotEmpty() }) {
        OutlinedTextField(
            value = selected?.first ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text(stringResource(Res.string.field_section)) },
            placeholder = { Text(stringResource(Res.string.choose_section)) },
            leadingIcon = selected?.let { { ColorMarker(it.second, size = 12.dp) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            writable.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section.name) },
                    leadingIcon = { ColorMarker(section.color, size = 12.dp) },
                    onClick = {
                        open = false
                        model.dispatch(EditorIntent.ChooseSection(section.id))
                    },
                )
            }
        }
    }
}

/**
 * What the image fields (lead image, image blocks, "Add block") need besides the model; [onChoose] opens the media
 * picker for the target.
 */
private class ImageSlot(val thumbnails: Thumbnails, val onChoose: (ImageTarget) -> Unit)

@Composable
private fun EditableArticle(
    model: EditorModel,
    errors: FieldErrors,
    images: ImageSlot,
    help: FieldHelpState,
    checker: SpellChecker,
    enabled: Boolean,
) {
    HeaderField.entries.forEach { field ->
        // The reader's order: the lead image follows the headline block
        if (field == HeaderField.LEAD) LeadImageField(model, images, errors.leadImage, help, checker, enabled)
        val value = model.draft[field]
        val error = errors.header[field]
        SpellCheckedTextField(
            value = value,
            onChange = { changed ->
                // Enter adds nothing; pasted line breaks become spaces in the model
                if (changed.filterNot { it == '\n' || it == '\r' } != value) model.dispatch(EditorIntent.EditHeader(field, changed))
            },
            onReplace = { model.dispatch(EditorIntent.EditHeader(field, it, ownStep = true)) },
            checker = checker,
            key = field.name.lowercase(),
            label = { Text(stringResource(HEADER_LABELS.getValue(field))) },
            trailingIcon = { FieldHelp(field.helpPart, help) },
            singleLine = field != HeaderField.LEAD,
            minLines = if (field == HeaderField.LEAD) 2 else 1,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            enabled = enabled,
            textStyle = if (field == HeaderField.HEADLINE) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AddBlockButton(afterId = null, model, images, enabled)
    if (model.draft.blocks.isEmpty()) Text(stringResource(Res.string.empty_body), style = MaterialTheme.typography.bodyMedium)
    val blocks = model.draft.blocks
    blocks.forEachIndexed { index, block ->
        key(block.id) {
            BlockCard(model, block, first = index == 0, last = index == blocks.lastIndex, error = errors.blocks[index], images, help, checker, enabled)
            AddBlockButton(afterId = block.id, model, images, enabled)
        }
    }
}

/**
 * The lead image: a big choose button (symbol and word) that opens the media picker, the preview, the caption,
 * replace and remove. Closing the picker without a choice keeps the previous image.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeadImageField(model: EditorModel, slot: ImageSlot, error: String?, help: FieldHelpState, checker: SpellChecker, enabled: Boolean) {
    val image = model.draft.leadImage
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.field_lead_image), style = MaterialTheme.typography.labelLarge)
            FieldHelp(HelpPart.LEAD_IMAGE, help)
        }
        if (image != null) {
            LeadImagePreview(image, slot.thumbnails)
            SpellCheckedTextField(
                value = image.caption,
                onChange = { changed ->
                    if (changed.filterNot { it == '\n' || it == '\r' } != image.caption) model.dispatch(EditorIntent.EditCaption(changed))
                },
                onReplace = { model.dispatch(EditorIntent.EditCaption(it, ownStep = true)) },
                checker = checker,
                key = "caption",
                label = { Text(stringResource(Res.string.field_caption)) },
                trailingIcon = { FieldHelp(HelpPart.CAPTION, help) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            val choose = if (image == null) Res.string.lead_image_choose else Res.string.lead_image_replace
            Button(
                onClick = { slot.onChoose(ImageTarget.LeadImage) },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            ) {
                PictureIcon()
                Spacer(Modifier.width(8.dp))
                Text(stringResource(choose), style = MaterialTheme.typography.titleSmall)
            }
            if (image != null) {
                OutlinedButton(onClick = { model.dispatch(EditorIntent.RemoveLeadImage) }, enabled = enabled) {
                    IconLabel(Icons.Close, stringResource(Res.string.lead_image_remove))
                }
            }
        }
    }
}

/**
 * "+ Add block" after [afterId] (`null`: at the start). "Image" opens the media picker and inserts an image block
 * with the picked media.
 */
@Composable
private fun AddBlockButton(afterId: Long?, model: EditorModel, images: ImageSlot, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { Text("+ " + stringResource(Res.string.add_block)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BlockType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(BLOCK_LABELS.getValue(type))) },
                    onClick = {
                        open = false
                        if (type == BlockType.IMAGE) images.onChoose(ImageTarget.NewBlock(afterId))
                        else model.dispatch(EditorIntent.AddBlock(afterId, type))
                    },
                )
            }
        }
    }
}

/** The rich-text field of a card that Bold applies to: the one focused last. */
private data class BoldTarget(val itemId: Long?, val state: RichTextState)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockCard(
    model: EditorModel,
    block: EditorBlock,
    first: Boolean,
    last: Boolean,
    error: String?,
    images: ImageSlot,
    help: FieldHelpState,
    checker: SpellChecker,
    enabled: Boolean,
) {
    var boldTarget by remember { mutableStateOf<BoldTarget?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(BLOCK_LABELS.getValue(block.type)), style = MaterialTheme.typography.labelLarge)
                    FieldHelp(block.type.helpPart, help)
                }
                if (block !is EditorBlock.Subhead && block !is EditorBlock.Image) {
                    val target = boldTarget
                    val isBold = target?.state?.currentSpanStyle?.fontWeight == FontWeight.Bold
                    val toggle = {
                        if (target != null) {
                            target.state.toggleSpanStyle(BOLD)
                            model.dispatch(EditorIntent.ToggleBold(block.id, target.itemId, target.state.runs()))
                        }
                    }
                    val label = @Composable { Text(stringResource(Res.string.bold), fontWeight = FontWeight.Bold) }
                    // Taking focus would collapse the selection in the text field before bold applies
                    val keepFocus = Modifier.focusProperties { canFocus = false }
                    if (isBold) FilledTonalButton(onClick = toggle, enabled = enabled, modifier = keepFocus) { label() }
                    else OutlinedButton(onClick = toggle, enabled = enabled && target != null, modifier = keepFocus) { label() }
                }
                TextButton(onClick = { model.dispatch(EditorIntent.MoveBlock(block.id, -1)) }, enabled = enabled && !first) {
                    IconLabel(Icons.Up, stringResource(Res.string.move_up))
                }
                TextButton(onClick = { model.dispatch(EditorIntent.MoveBlock(block.id, 1)) }, enabled = enabled && !last) {
                    IconLabel(Icons.Down, stringResource(Res.string.move_down))
                }
                TextButton(onClick = { model.dispatch(EditorIntent.RemoveBlock(block.id)) }, enabled = enabled) {
                    IconLabel(Icons.Close, stringResource(Res.string.remove_block))
                }
            }
            when (block) {
                is EditorBlock.Subhead -> SpellCheckedTextField(
                    value = block.text,
                    onChange = { changed ->
                        if (changed.filterNot { it == '\n' || it == '\r' } != block.text) model.dispatch(EditorIntent.EditSubhead(block.id, changed))
                    },
                    onReplace = { model.dispatch(EditorIntent.EditSubhead(block.id, it, ownStep = true)) },
                    checker = checker,
                    key = "block:${block.id}",
                    singleLine = true,
                    isError = error != null,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                is EditorBlock.Paragraph, is EditorBlock.Quote -> RichRunsField(
                    model, block.id, itemId = null, checker, enabled, isError = error != null,
                    onFocus = { boldTarget = BoldTarget(null, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                is EditorBlock.BulletList -> {
                    block.items.forEachIndexed { index, item ->
                        key(item.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("•", Modifier.width(20.dp), style = MaterialTheme.typography.bodyLarge)
                                RichRunsField(
                                    model, block.id, item.id, checker, enabled, isError = error != null,
                                    onFocus = { boldTarget = BoldTarget(item.id, it) },
                                    label = Res.string.list_item to index + 1,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { model.dispatch(EditorIntent.RemoveListItem(block.id, item.id)) }, enabled = enabled) {
                                    SymbolIcon(Icons.Close, Modifier.padding(horizontal = 4.dp), contentDescription = stringResource(Res.string.remove_item))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { model.dispatch(EditorIntent.AddListItem(block.id, block.items.last().id)) }, enabled = enabled) {
                        Text("+ " + stringResource(Res.string.add_item))
                    }
                }
                is EditorBlock.Image -> ImageBlockFields(model, block, images, error != null, help, checker, enabled)
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** An image block's preview, caption field and replace button, which opens the media picker and keeps the caption. */
@Composable
private fun ImageBlockFields(
    model: EditorModel,
    block: EditorBlock.Image,
    images: ImageSlot,
    isError: Boolean,
    help: FieldHelpState,
    checker: SpellChecker,
    enabled: Boolean,
) {
    MediaPreview(block.mediaId, 0, 0, block.caption, images.thumbnails)
    SpellCheckedTextField(
        value = block.caption,
        onChange = { changed ->
            if (changed.filterNot { it == '\n' || it == '\r' } != block.caption) model.dispatch(EditorIntent.SetImageCaption(block.id, changed))
        },
        onReplace = { model.dispatch(EditorIntent.SetImageCaption(block.id, it, ownStep = true)) },
        checker = checker,
        key = imageCaptionKey(block.id),
        label = { Text(stringResource(Res.string.field_caption)) },
        trailingIcon = { FieldHelp(HelpPart.CAPTION, help) },
        singleLine = true,
        isError = isError,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = { images.onChoose(ImageTarget.Block(block.id)) }, enabled = enabled) {
        PictureIcon()
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.lead_image_replace))
    }
}

/**
 * A paragraph, quote or list item edited with compose-rich-editor. The field keeps its own
 * [RichTextState]; typing flows into the model, undo/redo flows back ([EditorModel.restored]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RichRunsField(
    model: EditorModel,
    blockId: Long,
    itemId: Long?,
    checker: SpellChecker,
    enabled: Boolean,
    isError: Boolean,
    onFocus: (RichTextState) -> Unit,
    modifier: Modifier = Modifier,
    label: Pair<StringResource, Int>? = null,
) {
    // Read from the model at call time: the composed value may lag behind fast typing
    val current = { model.draft.runsOf(blockId, itemId) }
    val state = remember { RichTextState().apply { load(current()) } }
    LaunchedEffect(model.restored) {
        val runs = current()
        if (state.runs() != runs) state.load(runs)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.annotatedString }.collect {
            state.revertLists()
            val runs = state.runs()
            if (runs != current()) model.dispatch(EditorIntent.EditRuns(blockId, itemId, runs))
        }
    }
    val text = current().joinToString("") { it.text }
    val key = if (itemId == null) "block:$blockId" else "item:$itemId"
    CheckField(checker, key, text)
    val findings = if (enabled) checker.findings(key, text) else emptyList()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    val padding = RichTextEditorDefaults.outlinedRichTextEditorPadding()
    Column(modifier.onFocusChanged { focused = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedRichTextEditor(
            state = state,
            enabled = enabled,
            isError = isError,
            label = label?.let { (resource, number) -> { Text(stringResource(resource, number)) } },
            minLines = if (itemId == null) 3 else 1,
            textStyle = MaterialTheme.typography.bodyLarge,
            onTextLayout = { layout = it },
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth()
                .spellMarksOverlay(
                    findings, text, { layout }, MaterialTheme.colorScheme.error,
                    originX = padding.calculateLeftPadding(LayoutDirection.Ltr),
                    // The outlined editor moves down by 8 dp to make room for a label
                    originY = padding.calculateTopPadding() + if (label != null) 8.dp else 0.dp,
                )
                .onFocusChanged { if (it.isFocused) onFocus(state) },
        )
        val finding = if (focused) findingAt(findings, state.selection) else null
        if (finding != null) {
            SpellSuggestionRow(
                finding,
                onReplace = { replacement ->
                    val runs = replaceInRuns(current(), finding.start, finding.end, replacement)
                    state.load(runs, caret = finding.start + replacement.length)
                    model.dispatch(EditorIntent.EditRuns(blockId, itemId, runs, ownStep = true))
                },
                onIgnore = { checker.ignore(finding.word) },
            )
        }
    }
}

private fun Draft.runsOf(blockId: Long, itemId: Long?): List<Run> = when (val block = blocks.firstOrNull { it.id == blockId }) {
    is EditorBlock.Paragraph -> block.runs
    is EditorBlock.Quote -> block.runs
    is EditorBlock.BulletList -> block.items.firstOrNull { it.id == itemId }?.runs.orEmpty()
    is EditorBlock.Subhead, is EditorBlock.Image, null -> emptyList()
}

/** The spell-check key of an image block's caption, distinct from the subhead's `block:<id>` and the lead caption's. */
internal fun imageCaptionKey(blockId: Long): String = "caption:$blockId"
