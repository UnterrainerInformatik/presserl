package info.unterrainer.presserl.admin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.action_failed
import info.unterrainer.presserl.admin.resources.add_block
import info.unterrainer.presserl.admin.resources.add_item
import info.unterrainer.presserl.admin.resources.block_list
import info.unterrainer.presserl.admin.resources.block_paragraph
import info.unterrainer.presserl.admin.resources.block_quote
import info.unterrainer.presserl.admin.resources.block_subhead
import info.unterrainer.presserl.admin.resources.bold
import info.unterrainer.presserl.admin.resources.cancel
import info.unterrainer.presserl.admin.resources.conflict_text
import info.unterrainer.presserl.admin.resources.delete
import info.unterrainer.presserl.admin.resources.delete_text
import info.unterrainer.presserl.admin.resources.delete_title
import info.unterrainer.presserl.admin.resources.empty_body
import info.unterrainer.presserl.admin.resources.field_headline
import info.unterrainer.presserl.admin.resources.field_kicker
import info.unterrainer.presserl.admin.resources.field_lead
import info.unterrainer.presserl.admin.resources.field_subheadline
import info.unterrainer.presserl.admin.resources.leave
import info.unterrainer.presserl.admin.resources.leave_text
import info.unterrainer.presserl.admin.resources.leave_title
import info.unterrainer.presserl.admin.resources.list_item
import info.unterrainer.presserl.admin.resources.load_current
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.move_down
import info.unterrainer.presserl.admin.resources.move_up
import info.unterrainer.presserl.admin.resources.publish
import info.unterrainer.presserl.admin.resources.read_only
import info.unterrainer.presserl.admin.resources.redo
import info.unterrainer.presserl.admin.resources.remove_block
import info.unterrainer.presserl.admin.resources.remove_item
import info.unterrainer.presserl.admin.resources.revisions
import info.unterrainer.presserl.admin.resources.save_failed
import info.unterrainer.presserl.admin.resources.save_invalid
import info.unterrainer.presserl.admin.resources.save_pending
import info.unterrainer.presserl.admin.resources.save_saved
import info.unterrainer.presserl.admin.resources.save_saving
import info.unterrainer.presserl.admin.resources.stay
import info.unterrainer.presserl.admin.resources.take_offline
import info.unterrainer.presserl.admin.resources.undo
import info.unterrainer.presserl.admin.resources.unpublished_changes
import info.unterrainer.presserl.admin.resources.view_in_reader
import info.unterrainer.presserl.admin.ui.ArticleView
import info.unterrainer.presserl.admin.ui.BackButton
import info.unterrainer.presserl.admin.ui.Banner
import info.unterrainer.presserl.admin.ui.LoadFailed
import info.unterrainer.presserl.admin.ui.attempt
import info.unterrainer.presserl.admin.ui.describe
import info.unterrainer.presserl.admin.ui.statusText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

/**
 * The article editor at level `standard`. [readerUrl] gives the reader page of an article;
 * [onBack] and [onRevisions] are called once pending changes are saved.
 */
@Composable
fun EditorScreen(api: ApiClient, articleId: Long, readerUrl: (Long) -> String, onBack: () -> Unit, onRevisions: () -> Unit) {
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
            else -> Editor(api, loaded, readerUrl, onBack, onRevisions, onReload = { loads++ })
        }
    }
}

@Composable
private fun Editor(
    api: ApiClient,
    loaded: ArticleDto,
    readerUrl: (Long) -> String,
    onBack: () -> Unit,
    onRevisions: () -> Unit,
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
    val actions = actionsFor(article.allowedActions)
    val saveState by autosaver.state.collectAsState()
    var actionErrors by remember { mutableStateOf(FieldErrors()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf<(() -> Unit)?>(null) }

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

    val saveErrors = (saveState as? SaveState.Invalid)?.errors ?: FieldErrors()
    val errors = FieldErrors(
        header = saveErrors.header + actionErrors.header,
        blocks = saveErrors.blocks + actionErrors.blocks,
        general = saveErrors.general + actionErrors.general,
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
                TextButton(onClick = { uriHandler.openUri(readerUrl(article.id)) }) { Text(stringResource(Res.string.view_in_reader) + " ↗") }
            }
        }
        if (saveState == SaveState.Conflict) {
            Banner(stringResource(Res.string.conflict_text)) {
                Button(onClick = onReload) { Text(stringResource(Res.string.load_current)) }
            }
        }
        errors.general.forEach { Banner(stringResource(Res.string.action_failed, it)) }
        if (!actions.editable) Banner(stringResource(Res.string.read_only), color = MaterialTheme.colorScheme.secondaryContainer)

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (actions.editable) EditableArticle(model, errors, enabled = saveState != SaveState.Conflict) else ArticleView(model.draft)
        }

        HorizontalDivider()
        BottomBar(model, actions, saveState, busy, onPublish = { act(saveFirst = true) { api.publishArticle(article.id) } },
            onTakeOffline = { act(saveFirst = false) { api.takeArticleOffline(article.id) } },
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
    onTakeOffline: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            if (actions.editable) {
                OutlinedButton(onClick = { model.dispatch(EditorIntent.Undo) }, enabled = model.canUndo) { Text("↶ " + stringResource(Res.string.undo)) }
                OutlinedButton(onClick = { model.dispatch(EditorIntent.Redo) }, enabled = model.canRedo) { Text("↷ " + stringResource(Res.string.redo)) }
                Text(saveStateText(saveState), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), itemVerticalAlignment = Alignment.CenterVertically) {
            if (actions.delete) {
                TextButton(onClick = onDelete, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(Res.string.delete))
                }
            }
            if (actions.takeOffline) OutlinedButton(onClick = onTakeOffline, enabled = !busy) { Text(stringResource(Res.string.take_offline)) }
            if (actions.publish) Button(onClick = onPublish, enabled = !busy && saveState != SaveState.Conflict) { Text(stringResource(Res.string.publish)) }
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
)

private val EditorBlock.type: BlockType
    get() = when (this) {
        is EditorBlock.Paragraph -> BlockType.PARAGRAPH
        is EditorBlock.Subhead -> BlockType.SUBHEAD
        is EditorBlock.Quote -> BlockType.QUOTE
        is EditorBlock.BulletList -> BlockType.LIST
    }

@Composable
private fun EditableArticle(model: EditorModel, errors: FieldErrors, enabled: Boolean) {
    HeaderField.entries.forEach { field ->
        val value = model.draft[field]
        val error = errors.header[field]
        OutlinedTextField(
            value = value,
            onValueChange = { changed ->
                // Enter adds nothing; pasted line breaks become spaces in the model
                if (changed.filterNot { it == '\n' || it == '\r' } != value) model.dispatch(EditorIntent.EditHeader(field, changed))
            },
            label = { Text(stringResource(HEADER_LABELS.getValue(field))) },
            singleLine = field != HeaderField.LEAD,
            minLines = if (field == HeaderField.LEAD) 2 else 1,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            enabled = enabled,
            textStyle = if (field == HeaderField.HEADLINE) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AddBlockButton(afterId = null, model, enabled)
    if (model.draft.blocks.isEmpty()) Text(stringResource(Res.string.empty_body), style = MaterialTheme.typography.bodyMedium)
    val blocks = model.draft.blocks
    blocks.forEachIndexed { index, block ->
        key(block.id) {
            BlockCard(model, block, first = index == 0, last = index == blocks.lastIndex, error = errors.blocks[index], enabled)
            AddBlockButton(afterId = block.id, model, enabled)
        }
    }
}

@Composable
private fun AddBlockButton(afterId: Long?, model: EditorModel, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { Text("+ " + stringResource(Res.string.add_block)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BlockType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(BLOCK_LABELS.getValue(type))) },
                    onClick = {
                        open = false
                        model.dispatch(EditorIntent.AddBlock(afterId, type))
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
private fun BlockCard(model: EditorModel, block: EditorBlock, first: Boolean, last: Boolean, error: String?, enabled: Boolean) {
    var boldTarget by remember { mutableStateOf<BoldTarget?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(BLOCK_LABELS.getValue(block.type)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                if (block !is EditorBlock.Subhead) {
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
                    Text("↑ " + stringResource(Res.string.move_up))
                }
                TextButton(onClick = { model.dispatch(EditorIntent.MoveBlock(block.id, 1)) }, enabled = enabled && !last) {
                    Text("↓ " + stringResource(Res.string.move_down))
                }
                TextButton(onClick = { model.dispatch(EditorIntent.RemoveBlock(block.id)) }, enabled = enabled) {
                    Text("✕ " + stringResource(Res.string.remove_block))
                }
            }
            when (block) {
                is EditorBlock.Subhead -> OutlinedTextField(
                    value = block.text,
                    onValueChange = { changed ->
                        if (changed.filterNot { it == '\n' || it == '\r' } != block.text) model.dispatch(EditorIntent.EditSubhead(block.id, changed))
                    },
                    singleLine = true,
                    isError = error != null,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                is EditorBlock.Paragraph, is EditorBlock.Quote -> RichRunsField(
                    model, block.id, itemId = null, enabled, isError = error != null,
                    onFocus = { boldTarget = BoldTarget(null, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                is EditorBlock.BulletList -> {
                    block.items.forEachIndexed { index, item ->
                        key(item.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("•", Modifier.width(20.dp), style = MaterialTheme.typography.bodyLarge)
                                RichRunsField(
                                    model, block.id, item.id, enabled, isError = error != null,
                                    onFocus = { boldTarget = BoldTarget(item.id, it) },
                                    label = Res.string.list_item to index + 1,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { model.dispatch(EditorIntent.RemoveListItem(block.id, item.id)) }, enabled = enabled) {
                                    Text("✕", modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { model.dispatch(EditorIntent.AddListItem(block.id, block.items.last().id)) }, enabled = enabled) {
                        Text("+ " + stringResource(Res.string.add_item))
                    }
                }
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
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
    OutlinedRichTextEditor(
        state = state,
        enabled = enabled,
        isError = isError,
        label = label?.let { (resource, number) -> { Text(stringResource(resource, number)) } },
        minLines = if (itemId == null) 3 else 1,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus(state) },
    )
}

private fun Draft.runsOf(blockId: Long, itemId: Long?): List<Run> = when (val block = blocks.firstOrNull { it.id == blockId }) {
    is EditorBlock.Paragraph -> block.runs
    is EditorBlock.Quote -> block.runs
    is EditorBlock.BulletList -> block.items.firstOrNull { it.id == itemId }?.runs.orEmpty()
    is EditorBlock.Subhead, null -> emptyList()
}
