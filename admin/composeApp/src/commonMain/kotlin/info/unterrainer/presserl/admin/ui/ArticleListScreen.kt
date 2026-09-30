package info.unterrainer.presserl.admin.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.SectionRefDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.changed_at
import info.unterrainer.presserl.admin.resources.loading
import info.unterrainer.presserl.admin.resources.locked
import info.unterrainer.presserl.admin.resources.new_article
import info.unterrainer.presserl.admin.resources.no_articles
import info.unterrainer.presserl.admin.resources.no_headline
import info.unterrainer.presserl.admin.resources.no_section
import info.unterrainer.presserl.admin.resources.sort_changed
import info.unterrainer.presserl.admin.resources.sort_label
import info.unterrainer.presserl.admin.resources.sort_newest
import info.unterrainer.presserl.admin.resources.sort_section
import info.unterrainer.presserl.admin.resources.tab_all
import info.unterrainer.presserl.admin.resources.tab_mine
import info.unterrainer.presserl.admin.resources.tab_queue
import info.unterrainer.presserl.admin.resources.unpublished_changes
import info.unterrainer.presserl.admin.ui.section.ColorMarker
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

enum class ListTab { MINE, ALL, QUEUE }

/** Orders of "My articles" and "All articles", named like the server's `sort` values in [wire]. */
enum class ArticleSort(val wire: String) { CHANGED("changed"), NEWEST("newest"), SECTION("section") }

/** The parameters of `GET /api/articles` for a list tab other than the queue. */
data class ArticleListRequest(val mine: Boolean, val sort: String)

fun ListTab.request(sort: ArticleSort): ArticleListRequest = ArticleListRequest(mine = this == ListTab.MINE, sort = sort.wire)

/** An entry of an article list: a section heading (only when sorted by section) or an article. */
sealed interface ArticleListItem {
    data class Heading(val section: SectionRefDto?) : ArticleListItem
    data class Entry(val article: ArticleSummaryDto) : ArticleListItem
}

/**
 * The [articles] as returned by the server; with [ArticleSort.SECTION], a heading precedes the first article of each
 * section (the server already groups them).
 */
fun listItems(articles: List<ArticleSummaryDto>, sort: ArticleSort): List<ArticleListItem> {
    if (sort != ArticleSort.SECTION) return articles.map { ArticleListItem.Entry(it) }
    val items = mutableListOf<ArticleListItem>()
    articles.forEachIndexed { index, article ->
        if (index == 0 || articles[index - 1].section?.id != article.section?.id) items += ArticleListItem.Heading(article.section)
        items += ArticleListItem.Entry(article)
    }
    return items
}

/** The tabs offered: [ListTab.QUEUE] only while the last queue response ([queue], `null` before the first) is non-empty. */
fun visibleTabs(queue: List<ArticleSummaryDto>?): List<ListTab> =
    if (queue.isNullOrEmpty()) listOf(ListTab.MINE, ListTab.ALL) else ListTab.entries

/** The tab to show once [queue] arrived: [ListTab.MINE] instead of a queue that became empty, otherwise [tab]. */
fun tabAfterQueue(tab: ListTab, queue: List<ArticleSummaryDto>): ListTab =
    if (tab == ListTab.QUEUE && queue.isEmpty()) ListTab.MINE else tab

/**
 * The article lists. The review queue (`awaitingMe`) is fetched on every entry and reload whatever
 * [tab] is selected and handed to [onQueue]; [queue] is the last response, kept by the caller so
 * the tab survives a visit to the editor. With [ListTab.QUEUE] selected it is also the list shown.
 * "My articles" and "All articles" are requested in the order [sort], which the caller keeps while the app is open.
 */
@Composable
fun ArticleListScreen(
    api: ApiClient,
    tab: ListTab,
    queue: List<ArticleSummaryDto>?,
    onQueue: (List<ArticleSummaryDto>) -> Unit,
    onTab: (ListTab) -> Unit,
    onOpen: (Long) -> Unit,
    sort: ArticleSort = ArticleSort.CHANGED,
    onSort: (ArticleSort) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var articles by remember(tab, sort) { mutableStateOf<List<ArticleSummaryDto>?>(null) }
    var error by remember(tab, sort) { mutableStateOf<String?>(null) }
    // whether this entry's queue response arrived; until then a selected queue shows "loading"
    var queueLoaded by remember { mutableStateOf(false) }
    var queueError by remember { mutableStateOf<String?>(null) }
    var loads by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val currentTab by rememberUpdatedState(tab)

    LaunchedEffect(loads) {
        queueError = null
        attempt({ queueError = it }) { api.articles(awaitingMe = true) }?.let { fetched ->
            onQueue(fetched)
            queueLoaded = true
            val next = tabAfterQueue(currentTab, fetched)
            if (next != currentTab) onTab(next)
        }
    }
    LaunchedEffect(tab, sort, loads) {
        if (tab == ListTab.QUEUE) return@LaunchedEffect
        error = null
        val request = tab.request(sort)
        articles = attempt({ error = it }) { api.articles(mine = request.mine, sort = request.sort) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val tabs = visibleTabs(queue)
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0), modifier = Modifier.weight(1f)) {
                tabs.forEach { entry ->
                    val label = when (entry) {
                        ListTab.MINE -> stringResource(Res.string.tab_mine)
                        ListTab.ALL -> stringResource(Res.string.tab_all)
                        ListTab.QUEUE -> stringResource(Res.string.tab_queue, queue.orEmpty().size)
                    }
                    Tab(selected = tab == entry, onClick = { onTab(entry) }, text = { Text(label) })
                }
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
        if (tab != ListTab.QUEUE) SortChooser(sort, onSort)
        val current = if (tab == ListTab.QUEUE) queue.takeIf { queueLoaded } else articles
        val failure = if (tab == ListTab.QUEUE) queueError else error
        when {
            failure != null -> LoadFailed(failure, onReload = { loads++ })
            current == null -> Text(stringResource(Res.string.loading))
            current.isEmpty() -> Text(stringResource(Res.string.no_articles))
            else -> LazyColumn {
                items(listItems(current, if (tab == ListTab.QUEUE) ArticleSort.CHANGED else sort), key = { item ->
                    when (item) {
                        is ArticleListItem.Heading -> "section-${item.section?.id}"
                        is ArticleListItem.Entry -> item.article.id
                    }
                }) { item ->
                    when (item) {
                        is ArticleListItem.Heading -> SectionHeading(item.section)
                        is ArticleListItem.Entry -> {
                            ArticleRow(item.article, onClick = { onOpen(item.article.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChooser(sort: ArticleSort, onSort: (ArticleSort) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.sort_label), style = MaterialTheme.typography.bodyMedium)
        ArticleSort.entries.forEach { option ->
            val label = stringResource(
                when (option) {
                    ArticleSort.CHANGED -> Res.string.sort_changed
                    ArticleSort.NEWEST -> Res.string.sort_newest
                    ArticleSort.SECTION -> Res.string.sort_section
                },
            )
            if (option == sort) {
                FilledTonalButton(onClick = {}) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSort(option) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun SectionHeading(section: SectionRefDto?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp, start = 4.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        section?.let { ColorMarker(it.color) }
        Text(section?.name ?: stringResource(Res.string.no_section), style = MaterialTheme.typography.titleSmall)
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
            issueWaitText(article.status, article.readerVisible, article.issue?.number),
            frontPageText(article.frontPageWeight),
            stringResource(Res.string.unpublished_changes).takeIf { article.hasUnpublishedChanges },
            article.pendingLevel?.let { waitingText(it) },
            stringResource(Res.string.locked).takeIf { article.locked },
            article.author.displayName,
            stringResource(Res.string.changed_at, formatTimestamp(article.updatedAt)),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            article.section?.let { ColorMarker(it.color, size = 12.dp) }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
