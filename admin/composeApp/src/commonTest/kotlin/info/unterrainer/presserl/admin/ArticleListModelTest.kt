package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.SectionRefDto
import info.unterrainer.presserl.admin.ui.ArticleListItem
import info.unterrainer.presserl.admin.ui.ArticleListRequest
import info.unterrainer.presserl.admin.ui.ArticleSort
import info.unterrainer.presserl.admin.ui.ListTab
import info.unterrainer.presserl.admin.ui.listItems
import info.unterrainer.presserl.admin.ui.request
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleListModelTest {

    private val sport = SectionRefDto(1, "Sport", "sport", "green")
    private val kultur = SectionRefDto(2, "Kultur", "kultur", "blue")

    private fun summary(id: Long, section: SectionRefDto) = ArticleSummaryDto(
        id = id,
        status = "DRAFT",
        author = AuthorDto("papa", "Papa"),
        section = section,
        headline = "A$id",
        kicker = "",
        revision = 1,
        hasUnpublishedChanges = false,
        updatedAt = "t",
        allowedActions = emptyList(),
    )

    @Test
    fun theChosenOrderIsRequested() {
        assertEquals(ArticleListRequest(mine = true, sort = "changed"), ListTab.MINE.request(ArticleSort.CHANGED))
        assertEquals(ArticleListRequest(mine = false, sort = "newest"), ListTab.ALL.request(ArticleSort.NEWEST))
        assertEquals(ArticleListRequest(mine = false, sort = "section"), ListTab.ALL.request(ArticleSort.SECTION))
    }

    @Test
    fun bySectionAHeadingPrecedesEachSection() {
        val articles = listOf(summary(4, sport), summary(3, sport), summary(2, kultur), summary(1, kultur))

        assertEquals(
            listOf(
                ArticleListItem.Heading(sport),
                ArticleListItem.Entry(articles[0]),
                ArticleListItem.Entry(articles[1]),
                ArticleListItem.Heading(kultur),
                ArticleListItem.Entry(articles[2]),
                ArticleListItem.Entry(articles[3]),
            ),
            listItems(articles, ArticleSort.SECTION),
        )
    }

    @Test
    fun otherOrdersHaveNoHeadings() {
        val articles = listOf(summary(2, kultur), summary(1, sport))

        assertEquals(articles.map { ArticleListItem.Entry(it) }, listItems(articles, ArticleSort.NEWEST))
        assertEquals(articles.map { ArticleListItem.Entry(it) }, listItems(articles, ArticleSort.CHANGED))
    }
}
