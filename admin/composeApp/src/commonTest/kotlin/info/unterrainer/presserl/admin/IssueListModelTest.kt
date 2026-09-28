package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.IssueDetailDto
import info.unterrainer.presserl.admin.api.IssueDto
import info.unterrainer.presserl.admin.api.IssueListDto
import info.unterrainer.presserl.admin.ui.issue.DateInput
import info.unterrainer.presserl.admin.ui.issue.IssueListModel
import info.unterrainer.presserl.admin.ui.issue.parseDate
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
class IssueListModelTest {

    /** The server's issues, highest number first. */
    private val issues = mutableListOf(IssueDto(1, 1, null, true, "2026-09-01T08:00:00Z", 12, true))
    private val created = mutableListOf<String?>()
    private var failCreate = false

    private fun TestScope.model() = IssueListModel(
        backgroundScope,
        loadIssues = { IssueListDto(issues.toList()) },
        createIssue = { date ->
            if (failCreate) throw IllegalStateException("refused")
            created += date
            val number = issues.maxOf { it.number } + 1
            issues.indices.forEach { issues[it] = issues[it].copy(newest = false) }
            issues.add(0, IssueDto(number.toLong() * 10, number, date, false, null, 0, true))
            IssueDetailDto(number.toLong() * 10, number, date, false, null, 0, true)
        },
    )

    @Test
    fun blogModeList() = runTest {
        val model = model()
        model.load()
        runCurrent()

        val issue = model.state.value.issues.single()
        assertTrue(model.state.value.loaded)
        assertTrue(issue.published)
        assertTrue(issue.newest)
        assertEquals(12, issue.articleCount)
    }

    @Test
    fun newIssueWithoutDateOpensIt() = runTest {
        issues.add(0, IssueDto(2, 2, null, false, null, 0, true))
        val model = model()
        model.load()
        runCurrent()
        var opened: IssueDetailDto? = null

        model.openNew()
        model.submitNew { opened = it }
        runCurrent()

        assertEquals(listOf<String?>(null), created)
        assertEquals(3, opened?.number)
        assertFalse(opened!!.published)
        assertNull(model.state.value.newDate)
    }

    @Test
    fun newIssueWithDateSendsItPadded() = runTest {
        val model = model()
        model.openNew()
        model.newDate(" 2026-10-2 ")
        model.submitNew {}
        runCurrent()

        assertEquals(listOf<String?>("2026-10-02"), created)
    }

    @Test
    fun invalidDateSendsNothing() = runTest {
        val model = model()
        model.openNew()
        model.newDate("12.10.2026")
        model.submitNew { error("must not be created") }
        runCurrent()

        assertTrue(created.isEmpty())
        assertTrue(model.state.value.newDateInvalid)
        assertEquals("12.10.2026", model.state.value.newDate)

        model.newDate("2026-10-12")
        assertFalse(model.state.value.newDateInvalid)
    }

    @Test
    fun failedCreationKeepsTheDialogAndShowsTheMessage() = runTest {
        failCreate = true
        val model = model()
        model.load()
        runCurrent()
        model.openNew()
        model.newDate("2026-10-12")

        model.submitNew { error("must not be created") }
        runCurrent()

        assertEquals("refused", model.state.value.error)
        assertEquals("2026-10-12", model.state.value.newDate)
        assertFalse(model.state.value.busy)
        assertEquals(1, model.state.value.issues.size)
    }

    @Test
    fun cancelClosesTheDialog() = runTest {
        val model = model()
        model.openNew()
        model.cancelNew()

        assertNull(model.state.value.newDate)
    }

    @Test
    fun parsesCalendarDatesOnly() {
        assertEquals(DateInput.Empty, parseDate("  "))
        assertEquals(DateInput.Valid("2026-10-12"), parseDate("2026-10-12"))
        assertEquals(DateInput.Valid("2028-02-29"), parseDate("2028-2-29"))
        assertEquals(DateInput.Invalid, parseDate("2026-02-29"))
        assertEquals(DateInput.Invalid, parseDate("2100-02-29"))
        assertEquals(DateInput.Valid("2000-02-29"), parseDate("2000-02-29"))
        assertEquals(DateInput.Invalid, parseDate("2026-13-40"))
        assertEquals(DateInput.Invalid, parseDate("2026-04-31"))
        assertEquals(DateInput.Invalid, parseDate("12.10.2026"))
        assertEquals(DateInput.Invalid, parseDate("2026-10-12T00:00"))
    }
}
