package info.unterrainer.presserl.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.api.RevisionSummaryDto
import info.unterrainer.presserl.admin.article.Block
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.block_image
import info.unterrainer.presserl.admin.resources.change_added
import info.unterrainer.presserl.admin.resources.change_image_replaced
import info.unterrainer.presserl.admin.resources.change_removed
import info.unterrainer.presserl.admin.resources.changed_at
import info.unterrainer.presserl.admin.resources.changes_legend
import info.unterrainer.presserl.admin.resources.changes_none
import info.unterrainer.presserl.admin.resources.changes_title
import info.unterrainer.presserl.admin.resources.field_body
import info.unterrainer.presserl.admin.resources.field_caption
import info.unterrainer.presserl.admin.resources.field_headline
import info.unterrainer.presserl.admin.resources.field_kicker
import info.unterrainer.presserl.admin.resources.field_lead
import info.unterrainer.presserl.admin.resources.field_subheadline
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.published_at
import info.unterrainer.presserl.admin.resources.revision_by
import info.unterrainer.presserl.admin.resources.revision_changes
import info.unterrainer.presserl.admin.resources.revision_live
import info.unterrainer.presserl.admin.resources.revision_n
import info.unterrainer.presserl.admin.resources.revisions
import info.unterrainer.presserl.admin.ui.diff.BlockChange
import info.unterrainer.presserl.admin.ui.diff.ComparedField
import info.unterrainer.presserl.admin.ui.diff.DiffKind
import info.unterrainer.presserl.admin.ui.diff.DiffPart
import info.unterrainer.presserl.admin.ui.diff.RevisionComparison
import info.unterrainer.presserl.admin.ui.diff.changed
import info.unterrainer.presserl.admin.ui.diff.loadComparison
import info.unterrainer.presserl.admin.ui.diff.plainText
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.draftOf
import info.unterrainer.presserl.admin.ui.media.Thumbnails
import org.jetbrains.compose.resources.stringResource

/**
 * Revision history of an article, newest first as returned by the server, each with its author; every revision but
 * the first offers "Changes" ([onChanges]), the comparison with the revision before it.
 */
@Composable
fun RevisionsScreen(api: ApiClient, articleId: Long, onBack: () -> Unit, onOpen: (Int) -> Unit, onChanges: (Int) -> Unit = {}) {
    var revisions by remember { mutableStateOf<List<RevisionSummaryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }
    LaunchedEffect(loads) {
        error = null
        revisions = attempt({ error = it }) { api.revisions(articleId) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Text(stringResource(Res.string.revisions), style = MaterialTheme.typography.titleLarge)
        }
        val current = revisions
        when {
            error != null -> LoadFailed(error!!, onReload = { loads++ })
            current == null -> Text(stringResource(Res.string.loading))
            else -> LazyColumn {
                items(current, key = { it.number }) { revision ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).heightIn(min = 44.dp).clickable { onOpen(revision.number) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RevisionTitle(revision.number, revision.live)
                            Text(
                                revisionDetails(revision.updatedAt, revision.publishedAt, revision.author?.let { authorLabel(it) }),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (revision.number > 1) {
                            TextButton(onClick = { onChanges(revision.number) }) { Text(stringResource(Res.string.revision_changes)) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** One revision, read-only. */
@Composable
fun RevisionScreen(api: ApiClient, articleId: Long, number: Int, onBack: () -> Unit) {
    var revision by remember { mutableStateOf<RevisionDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }
    LaunchedEffect(loads) {
        error = null
        revision = attempt({ error = it }) { api.revision(articleId, number) }
    }
    val thumbnails = remember { Thumbnails { api.mediaRendition(it, "thumbnail") } }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            RevisionTitle(number, revision?.live == true)
        }
        val current = revision
        when {
            error != null -> LoadFailed(error!!, onReload = { loads++ })
            current == null -> Text(stringResource(Res.string.loading))
            else -> {
                Text(
                    revisionDetails(current.updatedAt, current.publishedAt, current.author?.let { authorLabel(it) }),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ArticleView(remember(current) { draftOf(current, IdSource()) }, thumbnails)
            }
        }
    }
}

@Composable
private fun RevisionTitle(number: Int, live: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.revision_n, number), style = MaterialTheme.typography.titleMedium)
        if (live) {
            Text(
                stringResource(Res.string.revision_live),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun revisionDetails(updatedAt: String, publishedAt: String?, author: String?): String = listOfNotNull(
    author?.let { stringResource(Res.string.revision_by, it) },
    stringResource(Res.string.changed_at, formatTimestamp(updatedAt)),
    publishedAt?.let { stringResource(Res.string.published_at, formatTimestamp(it)) },
).joinToString(" · ")

/**
 * The comparison of revision [number] with the one before it (design D6): removed words struck through on a light
 * error background, added words underlined on a light primary background, so the marking does not rely on colour
 * alone; unchanged fields without markup. Names the author of the newer revision.
 */
@Composable
fun RevisionDiffScreen(api: ApiClient, articleId: Long, number: Int, onBack: () -> Unit) {
    var comparison by remember { mutableStateOf<RevisionComparison?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }
    LaunchedEffect(loads) {
        error = null
        comparison = attempt({ error = it }) { loadComparison(number) { api.revision(articleId, it) } }
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            val author = comparison?.newer?.author?.let { authorLabel(it) }.orEmpty()
            Text(stringResource(Res.string.changes_title, number, author), style = MaterialTheme.typography.titleLarge)
        }
        val current = comparison
        when {
            error != null -> LoadFailed(error!!, onReload = { loads++ })
            current == null -> Text(stringResource(Res.string.loading))
            else -> Comparison(current)
        }
    }
}

@Composable
private fun Comparison(comparison: RevisionComparison) {
    Text(stringResource(Res.string.changes_legend), style = MaterialTheme.typography.bodyMedium)
    val unchanged = comparison.fields.values.none { it.changed() } && !comparison.leadImageReplaced &&
        comparison.blocks.all { it is BlockChange.Same }
    if (unchanged) Text(stringResource(Res.string.changes_none), style = MaterialTheme.typography.bodyMedium)
    val labels = mapOf(
        ComparedField.KICKER to Res.string.field_kicker,
        ComparedField.HEADLINE to Res.string.field_headline,
        ComparedField.SUBHEADLINE to Res.string.field_subheadline,
        ComparedField.LEAD to Res.string.field_lead,
        ComparedField.LEAD_IMAGE_CAPTION to Res.string.field_caption,
    )
    comparison.fields.forEach { (field, parts) ->
        if (parts.isEmpty() && !(field == ComparedField.LEAD_IMAGE_CAPTION && comparison.leadImageReplaced)) return@forEach
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(labels.getValue(field)), style = MaterialTheme.typography.labelLarge)
            if (field == ComparedField.LEAD_IMAGE_CAPTION && comparison.leadImageReplaced) {
                Text(stringResource(Res.string.change_image_replaced), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(diffText(parts), style = if (field == ComparedField.HEADLINE) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        }
    }
    if (comparison.blocks.isNotEmpty()) {
        Text(stringResource(Res.string.field_body), style = MaterialTheme.typography.labelLarge)
        comparison.blocks.forEach { change -> BlockChangeView(change) }
    }
}

@Composable
private fun BlockChangeView(change: BlockChange) {
    val (block, parts) = when (change) {
        is BlockChange.Same -> change.block to listOf(DiffPart(DiffKind.SAME, change.block.plainText()))
        is BlockChange.Added -> change.block to listOf(DiffPart(DiffKind.ADDED, change.block.plainText()))
        is BlockChange.Removed -> change.block to listOf(DiffPart(DiffKind.REMOVED, change.block.plainText()))
        is BlockChange.Changed -> change.new to change.parts
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val marks = listOfNotNull(
            stringResource(Res.string.change_added).takeIf { change is BlockChange.Added },
            stringResource(Res.string.change_removed).takeIf { change is BlockChange.Removed },
            stringResource(Res.string.change_image_replaced).takeIf { change is BlockChange.Changed && change.imageReplaced },
        )
        val kind = if (block is Block.Image) stringResource(Res.string.block_image) else null
        listOfNotNull(kind, marks.joinToString(" · ").ifEmpty { null }).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        val style = when (block) {
            is Block.Subhead -> MaterialTheme.typography.titleMedium
            is Block.Quote -> MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
            else -> MaterialTheme.typography.bodyLarge
        }
        if (parts.any { it.text.isNotEmpty() }) Text(diffText(parts), style = style)
    }
}

/** The pieces as text: removed struck through, added underlined, each on a light background. */
@Composable
private fun diffText(parts: List<DiffPart>): AnnotatedString {
    val removed = SpanStyle(textDecoration = TextDecoration.LineThrough, background = MaterialTheme.colorScheme.errorContainer)
    val added = SpanStyle(textDecoration = TextDecoration.Underline, background = MaterialTheme.colorScheme.primaryContainer)
    return buildAnnotatedString {
        parts.forEach { part ->
            when (part.kind) {
                DiffKind.SAME -> append(part.text)
                DiffKind.REMOVED -> withStyle(removed) { append(part.text) }
                DiffKind.ADDED -> withStyle(added) { append(part.text) }
            }
        }
    }
}
