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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.changed_at
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.new_article
import info.unterrainer.presserl.admin.resources.no_articles
import info.unterrainer.presserl.admin.resources.no_headline
import info.unterrainer.presserl.admin.resources.tab_all
import info.unterrainer.presserl.admin.resources.tab_mine
import info.unterrainer.presserl.admin.resources.unpublished_changes
import info.unterrainer.presserl.admin.ui.section.ColorMarker
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

enum class ListTab { MINE, ALL }

@Composable
fun ArticleListScreen(api: ApiClient, tab: ListTab, onTab: (ListTab) -> Unit, onOpen: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var articles by remember(tab) { mutableStateOf<List<ArticleSummaryDto>?>(null) }
    var error by remember(tab) { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(tab, loads) {
        error = null
        articles = attempt({ error = it }) { api.articles(mine = tab == ListTab.MINE) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.weight(1f)) {
                Tab(selected = tab == ListTab.MINE, onClick = { onTab(ListTab.MINE) }, text = { Text(stringResource(Res.string.tab_mine)) })
                Tab(selected = tab == ListTab.ALL, onClick = { onTab(ListTab.ALL) }, text = { Text(stringResource(Res.string.tab_all)) })
            }
            Button(
                enabled = !creating,
                onClick = {
                    creating = true
                    scope.launch {
                        attempt({ error = it }) { api.createArticle() }?.let { onOpen(it.id) }
                        creating = false
                    }
                },
            ) { Text(stringResource(Res.string.new_article)) }
        }
        val current = articles
        when {
            error != null -> LoadFailed(error!!, onReload = { loads++ })
            current == null -> Text(stringResource(Res.string.loading))
            current.isEmpty() -> Text(stringResource(Res.string.no_articles))
            else -> LazyColumn {
                items(current, key = { it.id }) { article ->
                    ArticleRow(article, onClick = { onOpen(article.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ArticleRow(article: ArticleSummaryDto, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(article.headline.ifEmpty { stringResource(Res.string.no_headline) }, style = MaterialTheme.typography.titleMedium)
        val details = listOfNotNull(
            article.section?.name,
            statusText(article.status),
            stringResource(Res.string.unpublished_changes).takeIf { article.hasUnpublishedChanges },
            article.pendingLevel?.let { waitingText(it) },
            article.author.displayName,
            stringResource(Res.string.changed_at, formatTimestamp(article.updatedAt)),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            article.section?.let { ColorMarker(it.color, size = 12.dp) }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
