package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.IssueDetailDto
import info.unterrainer.presserl.admin.api.IssueRefDto
import info.unterrainer.presserl.admin.ui.issue.IssueCalls
import info.unterrainer.presserl.admin.ui.issue.IssueDetailModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IssueDetailModelTest {

    private val schulfest = article(5, "Schulfest", IssueRefDto(1, 1))
    private val all = listOf(article(9, "Nine", ISSUE_2), article(4, "Four", ISSUE_2), schulfest, article(7, "Draft", null))

    /** The server's state of issue 2. */
    private var order = listOf(9L, 4L)
    private var date: String? = null
    private var published = false
    private var deleted = false
    private var fail = false
    private val sentOrders = mutableListOf<List<Long>>()
    private val sentDates = mutableListOf<String?>()

    private fun issue() = IssueDetailDto(
        id = 2,
        number = 2,
        publicationDate = date,
        published = published,
        publishedAt = if (published) "2026-10-12T08:00:00Z" else null,
        articleCount = order.size,
        newest = true,
        articles = order.map { id -> all.first { it.id == id }.copy(issue = ISSUE_2) },
    )

    private fun refuse() {
        if (fail) throw IllegalStateException("refused")
    }

    private fun TestScope.model() = IssueDetailModel(
        backgroundScope,
        IssueCalls(
            load = { issue() },
            setDate = { value -> refuse(); sentDates += value; date = value; issue() },
            publish = { refuse(); published = true; issue() },
            unpublish = { refuse(); published = false; issue() },
            setArticles = { ids -> refuse(); sentOrders += ids; order = ids; issue() },
            delete = { refuse(); deleted = true },
            articles = { all },
        ),
    )

    private fun TestScope.loaded(): IssueDetailModel {
        val model = model()
        model.load()
        runCurrent()
        return model
    }

    @Test
    fun moveUpMakesTheLeadStory() = runTest {
        val model = loaded()

        model.moveUp(1)
        runCurrent()

        assertEquals(listOf(listOf(4L, 9L)), sentOrders)
        assertEquals(listOf(4L, 9L), model.state.value.issue?.articles?.map { it.id })
    }

    @Test
    fun moveDownAndBounds() = runTest {
        val model = loaded()

        model.moveUp(0)
        model.moveDown(1)
        runCurrent()
        assertTrue(sentOrders.isEmpty())

        model.moveDown(0)
        runCurrent()
        assertEquals(listOf(listOf(4L, 9L)), sentOrders)
    }

    @Test
    fun removeSendsTheRest() = runTest {
        val model = loaded()

        model.remove(9)
        runCurrent()

        assertEquals(listOf(listOf(4L)), sentOrders)
        assertEquals(listOf(4L), model.state.value.issue?.articles?.map { it.id })
    }

    @Test
    fun pickerOffersOtherArticlesAndAddMovesOneFromAnotherIssue() = runTest {
        val model = loaded()

        model.openPicker()
        runCurrent()
        val candidates = model.state.value.candidates!!
        assertEquals(listOf(5L, 7L), candidates.map { it.id })
        assertEquals(1, candidates.first().issue?.number)

        model.add(schulfest)
        runCurrent()

        assertNull(model.state.value.candidates)
        assertEquals(listOf(listOf(9L, 4L, 5L)), sentOrders)
        assertEquals(listOf(9L, 4L, 5L), model.state.value.issue?.articles?.map { it.id })
    }

    @Test
    fun dateIsSavedAndCleared() = runTest {
        val model = loaded()

        model.dateText("2026-10-12")
        model.saveDate()
        runCurrent()
        assertEquals("2026-10-12", model.state.value.issue?.publicationDate)

        model.clearDate()
        runCurrent()
        assertEquals(listOf("2026-10-12", null), sentDates)
        assertEquals("", model.state.value.dateText)
    }

    @Test
    fun invalidDateSendsNothing() = runTest {
        val model = loaded()

        model.dateText("2026-13-40")
        model.saveDate()
        runCurrent()

        assertTrue(sentDates.isEmpty())
        assertTrue(model.state.value.dateInvalid)
    }

    @Test
    fun publishAndUnpublish() = runTest {
        val model = loaded()

        model.setLive(true)
        runCurrent()
        assertTrue(model.state.value.issue!!.published)
        assertFalse(model.state.value.canDelete)

        model.setLive(false)
        runCurrent()
        assertFalse(model.state.value.issue!!.published)
        assertTrue(model.state.value.canDelete)
    }

    @Test
    fun deleteAsksFirstAndCanBeCancelled() = runTest {
        val model = loaded()
        var back = false

        model.requestDelete()
        assertTrue(model.state.value.confirmDelete)
        model.cancelDelete()
        assertFalse(model.state.value.confirmDelete)
        assertFalse(deleted)

        model.requestDelete()
        model.confirmDelete { back = true }
        runCurrent()
        assertTrue(deleted)
        assertTrue(back)
    }

    @Test
    fun liveIssueOffersNoDelete() = runTest {
        published = true
        val model = loaded()

        model.requestDelete()

        assertFalse(model.state.value.confirmDelete)
    }

    @Test
    fun failureShowsTheMessageReloadsAndKeepsTheTypedDate() = runTest {
        val model = loaded()
        fail = true

        model.dateText("2026-10-12")
        model.moveUp(1)
        runCurrent()

        val state = model.state.value
        assertEquals("refused", state.error)
        assertEquals(listOf(9L, 4L), state.issue?.articles?.map { it.id })
        assertEquals("2026-10-12", state.dateText)
        assertFalse(state.busy)
    }

    private companion object {
        val ISSUE_2 = IssueRefDto(2, 2)

        fun article(id: Long, headline: String, issue: IssueRefDto?) = ArticleSummaryDto(
            id = id,
            status = if (issue == null) "DRAFT" else "PUBLISHED",
            author = AuthorDto("papa", "Papa"),
            issue = issue,
            headline = headline,
            kicker = "",
            revision = 1,
            hasUnpublishedChanges = false,
            updatedAt = "2026-09-26T10:05:00Z",
            allowedActions = emptyList(),
        )
    }
}
