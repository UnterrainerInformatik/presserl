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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.api.RevisionSummaryDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.changed_at
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.published_at
import info.unterrainer.presserl.admin.resources.revision_live
import info.unterrainer.presserl.admin.resources.revision_n
import info.unterrainer.presserl.admin.resources.revisions
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.draftOf
import org.jetbrains.compose.resources.stringResource

/** Revision history of an article, newest first as returned by the server. */
@Composable
fun RevisionsScreen(api: ApiClient, articleId: Long, onBack: () -> Unit, onOpen: (Int) -> Unit) {
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
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onOpen(revision.number) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RevisionTitle(revision.number, revision.live)
                        Text(revisionDetails(revision.updatedAt, revision.publishedAt), style = MaterialTheme.typography.bodyMedium)
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
                Text(revisionDetails(current.updatedAt, current.publishedAt), style = MaterialTheme.typography.bodyMedium)
                ArticleView(remember(current) { draftOf(current, IdSource()) })
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
private fun revisionDetails(updatedAt: String, publishedAt: String?): String = listOfNotNull(
    stringResource(Res.string.changed_at, formatTimestamp(updatedAt)),
    publishedAt?.let { stringResource(Res.string.published_at, formatTimestamp(it)) },
).joinToString(" · ")
