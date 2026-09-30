package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.IssueWait
import info.unterrainer.presserl.admin.ui.editor.EditorIntent
import info.unterrainer.presserl.admin.ui.editor.EditorModel
import info.unterrainer.presserl.admin.ui.editor.FrontPageWeightModel
import info.unterrainer.presserl.admin.ui.editor.HeaderField
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.draftOf
import info.unterrainer.presserl.admin.ui.editor.mayWeight
import info.unterrainer.presserl.admin.ui.editor.weightErrorOf
import info.unterrainer.presserl.admin.ui.issueWait
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reader visibility marker, "View in reader" condition and the front-page weight in the editor. */
class FrontPageTest {

    private val article = json.decodeFromString<ArticleDto>(ARTICLE)

    // --- DTOs

    @Test
    fun readerVisibleAndWeightAreRead() {
        val weighted = json.decodeFromString<ArticleDto>(
            ARTICLE.trimEnd().removeSuffix("}") + """, "readerVisible": true, "frontPageWeight": 3 }""",
        )
        assertTrue(weighted.readerVisible)
        assertEquals(3, weighted.frontPageWeight)
    }

    @Test
    fun olderServersMeanNotVisibleAndNoWeight() {
        assertFalse(article.readerVisible)
        assertNull(article.frontPageWeight)
        val summary = json.decodeFromString<ArticleSummaryDto>(
            """{ "id": 1, "status": "PUBLISHED", "author": { "username": "a", "displayName": "A" }, "headline": "H",
            "kicker": "", "revision": 1, "hasUnpublishedChanges": false, "updatedAt": "t", "allowedActions": [],
            "readerVisible": true, "frontPageWeight": 1 }""",
        )
        assertTrue(summary.readerVisible)
        assertEquals(1, summary.frontPageWeight)
    }

    // --- waiting marker and "View in reader"

    @Test
    fun publishedArticleOfAPlannedIssueWaitsForIt() {
        assertEquals(IssueWait.ForIssue(2), issueWait("PUBLISHED", readerVisible = false, issueNumber = 2))
    }

    @Test
    fun publishedArticleWithoutIssueIsInNoIssue() {
        assertEquals(IssueWait.NoIssue, issueWait("PUBLISHED", readerVisible = false, issueNumber = null))
    }

    @Test
    fun liveArticlesAndOtherStatusesHaveNoMarker() {
        assertNull(issueWait("PUBLISHED", readerVisible = true, issueNumber = 1))
        for (status in listOf("DRAFT", "SUBMITTED", "OFFLINE")) {
            assertNull(issueWait(status, readerVisible = false, issueNumber = 2), status)
        }
    }

    @Test
    fun viewInReaderFollowsReaderVisible() {
        // The editor offers "View in reader" exactly for article.readerVisible
        assertEquals("PUBLISHED", article.status)
        assertFalse(article.readerVisible)
    }

    // --- weight field

    @Test
    fun onlyChiefAndPublisherWeight() {
        assertTrue(mayWeight(listOf("EDITOR_IN_CHIEF")))
        assertTrue(mayWeight(listOf("READER", "PUBLISHER")))
        assertFalse(mayWeight(listOf("READER")))
        assertFalse(mayWeight(emptyList()))
    }

    @Test
    fun inputKeepsDigitsOnly() {
        val model = FrontPageWeightModel(null) { error("not saved") }

        model.edit(" 1a2-")
        assertEquals("12", model.text)
        model.edit("123456")
        assertEquals("1234", model.text)
    }

    @Test
    fun commitSavesTheNumberAndClearsWithAnEmptyField() = runTest {
        val sent = mutableListOf<Int?>()
        val model = FrontPageWeightModel(2) { weight ->
            sent += weight
            article.copy(frontPageWeight = weight)
        }

        assertNull(model.commit(), "unchanged is not saved")
        model.edit("1")
        assertEquals(1, model.commit()?.frontPageWeight)
        model.edit("")
        assertNull(model.commit()?.frontPageWeight)

        assertEquals(listOf(1, null), sent)
        assertEquals("", model.text)
    }

    @Test
    fun serverMessageIsShownAtTheField() = runTest {
        assertEquals(
            "weight must be a whole number from 1 to 999, or null",
            weightErrorOf(listOf(FieldErrorDto("other", "x"), FieldErrorDto("weight", "weight must be a whole number from 1 to 999, or null"))),
        )
        assertNull(weightErrorOf(listOf(FieldErrorDto(null, "general"))))

        val model = FrontPageWeightModel(null) { throw IllegalStateException("offline") }
        model.edit("0")
        assertNull(model.commit())
        assertEquals("offline", model.error)
        assertEquals("0", model.text)
        model.edit("5")
        assertNull(model.error)
    }

    @Test
    fun savingTheWeightDoesNotTouchUndo() = runTest {
        val ids = IdSource()
        var now = 0L
        val editor = EditorModel(draftOf(article, ids), ids, clock = { now })
        now += 5_000
        editor.dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "Edited"))
        val draft = editor.draft
        val canUndo = editor.canUndo
        val weight = FrontPageWeightModel(null) { article.copy(frontPageWeight = it) }

        weight.edit("1")
        weight.commit()

        assertEquals(draft, editor.draft)
        assertEquals(canUndo, editor.canUndo)
        assertFalse(editor.canRedo)
        editor.dispatch(EditorIntent.Undo)
        assertEquals(article.headline, editor.draft.headline)
        assertEquals("1", weight.text)
    }
}
