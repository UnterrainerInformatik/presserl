package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.ReviewDto
import info.unterrainer.presserl.admin.api.SpellMatchDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.ui.editor.BlockType
import info.unterrainer.presserl.admin.ui.editor.Draft
import info.unterrainer.presserl.admin.ui.editor.EditorActions
import info.unterrainer.presserl.admin.ui.editor.EditorBlock
import info.unterrainer.presserl.admin.ui.editor.EditorIntent
import info.unterrainer.presserl.admin.ui.editor.EditorModel
import info.unterrainer.presserl.admin.ui.editor.HeaderField
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.REJECT_NOTE_MAX
import info.unterrainer.presserl.admin.ui.editor.actionsFor
import info.unterrainer.presserl.admin.ui.editor.canConfirmReject
import info.unterrainer.presserl.admin.ui.editor.limitNote
import info.unterrainer.presserl.admin.ui.editor.rejectionToShow
import info.unterrainer.presserl.admin.ui.editor.draftOf
import info.unterrainer.presserl.admin.ui.spell.SpellChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditorModelTest {

    private val article = json.decodeFromString<ArticleDto>(ARTICLE)
    private var now = 0L
    private val ids = IdSource()
    private val model = EditorModel(draftOf(article, ids), ids, clock = { now })
    private val draft get() = model.draft

    private fun dispatch(intent: EditorIntent, afterMillis: Long = 5_000) {
        now += afterMillis
        model.dispatch(intent)
    }

    @Test
    fun anUnchangedDraftSavesTheSameContent() {
        assertEquals(article.body, draft.toContent().body)
        assertEquals("The pumpkin is huge", draft.toContent().headline)
        assertEquals(4, draft.toContent().sectionId)
        assertEquals(draftOf(article, IdSource()).toContent(), draft.toContent())
    }

    @Test
    fun choosingASectionIsSavedAndUndoable() {
        dispatch(EditorIntent.ChooseSection(7))
        assertEquals(7, draft.toContent().sectionId)
        assertTrue(model.canUndo)

        dispatch(EditorIntent.Undo)
        assertEquals(4, draft.toContent().sectionId)

        dispatch(EditorIntent.Redo)
        assertEquals(7, draft.toContent().sectionId)
    }

    @Test
    fun choosingTheCurrentSectionChangesNothing() {
        dispatch(EditorIntent.ChooseSection(4))
        assertFalse(model.canUndo)
    }

    @Test
    fun headerFieldsStaySingleLineAndWithinTheirLimit() {
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "Big\npumpkin"))
        assertEquals("Big pumpkin", draft.headline)

        dispatch(EditorIntent.EditHeader(HeaderField.KICKER, "k".repeat(250)))
        dispatch(EditorIntent.EditHeader(HeaderField.LEAD, "l".repeat(1_200)))
        assertEquals(200, draft.kicker.length)
        assertEquals(1_000, draft.lead.length)
    }

    @Test
    fun undoOfARemovedBlockRestoresItsPositionAndBoldRuns() {
        val paragraph = draft.blocks[0]
        dispatch(EditorIntent.RemoveBlock(paragraph.id))
        assertEquals(3, draft.blocks.size)

        dispatch(EditorIntent.Undo)

        assertEquals(paragraph, draft.blocks[0])
        assertEquals(listOf(Run("It started "), Run("in May", bold = true), Run(".")), (draft.blocks[0] as EditorBlock.Paragraph).runs)
        assertEquals(article.body, draft.toContent().body)
    }

    @Test
    fun typingInOneFieldWithinASecondIsOneStep() {
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "T"))
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "Th"), afterMillis = 400)
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "The"), afterMillis = 400)
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "The end"), afterMillis = 1_500)

        dispatch(EditorIntent.Undo)
        assertEquals("The", draft.headline)
        dispatch(EditorIntent.Undo)
        assertEquals("The pumpkin is huge", draft.headline)
        assertFalse(model.canUndo)
    }

    @Test
    fun typingInAnotherFieldStartsANewStep() {
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "H"))
        dispatch(EditorIntent.EditHeader(HeaderField.KICKER, "K"), afterMillis = 100)

        dispatch(EditorIntent.Undo)
        assertEquals("Garden", draft.kicker)
        assertEquals("H", draft.headline)
    }

    @Test
    fun boldToggleIsItsOwnStepEvenWhileTyping() {
        val paragraph = draft.blocks[0].id
        dispatch(EditorIntent.EditRuns(paragraph, null, listOf(Run("It started"))))
        dispatch(EditorIntent.ToggleBold(paragraph, null, listOf(Run("It started", bold = true))), afterMillis = 100)
        dispatch(EditorIntent.EditRuns(paragraph, null, listOf(Run("It started!", bold = true))), afterMillis = 100)

        dispatch(EditorIntent.Undo)
        assertEquals(listOf(Run("It started", bold = true)), (draft.blocks[0] as EditorBlock.Paragraph).runs)
        dispatch(EditorIntent.Undo)
        assertEquals(listOf(Run("It started")), (draft.blocks[0] as EditorBlock.Paragraph).runs)
    }

    @Test
    fun aChosenSuggestionIsItsOwnUndoStepEvenRightAfterTyping() {
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "Der Hund ist gro"))
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "Der Hund ist gros"), afterMillis = 300)
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "Der Hund ist groß", ownStep = true), afterMillis = 300)
        assertEquals("Der Hund ist groß", draft.headline)

        dispatch(EditorIntent.Undo)
        assertEquals("Der Hund ist gros", draft.headline)
        dispatch(EditorIntent.Undo)
        assertEquals(article.headline, draft.headline)
    }

    @Test
    fun typingAfterASuggestionStartsANewStep() {
        val paragraph = draft.blocks[0].id
        dispatch(EditorIntent.EditRuns(paragraph, null, listOf(Run("Ein gros"))))
        dispatch(EditorIntent.EditRuns(paragraph, null, listOf(Run("Ein groß")), ownStep = true), afterMillis = 100)
        dispatch(EditorIntent.EditRuns(paragraph, null, listOf(Run("Ein großer"))), afterMillis = 100)

        dispatch(EditorIntent.Undo)
        assertEquals(listOf(Run("Ein groß")), (draft.blocks[0] as EditorBlock.Paragraph).runs)
        dispatch(EditorIntent.Undo)
        assertEquals(listOf(Run("Ein gros")), (draft.blocks[0] as EditorBlock.Paragraph).runs)
    }

    @Test
    fun spellCheckingAloneChangesNothingInTheModel() = kotlinx.coroutines.test.runTest {
        val before = draft
        val checker = SpellChecker(backgroundScope, enabled = true, check = {
            listOf(SpellMatchDto(0, 3, "Tippfehler", listOf("The")))
        }, clock = { testScheduler.currentTime })
        checker.checkNow("headline", draft.headline)
        testScheduler.runCurrent()

        assertEquals(1, checker.findings("headline", draft.headline).size)
        assertSame(before, draft)
        assertFalse(model.canUndo)
    }

    @Test
    fun redoIsClearedByANewEdit() {
        dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "New"))
        dispatch(EditorIntent.Undo)
        assertTrue(model.canRedo)
        dispatch(EditorIntent.Redo)
        assertEquals("New", draft.headline)
        dispatch(EditorIntent.Undo)

        dispatch(EditorIntent.EditHeader(HeaderField.KICKER, "Other"))

        assertFalse(model.canRedo)
        dispatch(EditorIntent.Redo)
        assertEquals("The pumpkin is huge", draft.headline)
    }

    @Test
    fun historyIsBounded() {
        val small = EditorModel(Draft(), ids, clock = { now }, historyLimit = 3)
        repeat(5) { i ->
            now += 5_000
            small.dispatch(EditorIntent.EditHeader(HeaderField.HEADLINE, "v$i"))
        }
        repeat(5) { small.dispatch(EditorIntent.Undo) }
        assertEquals("v1", small.draft.headline)
    }

    @Test
    fun blocksAreAddedAfterABlockAndMoved() {
        val (paragraph, subhead, quote, list) = draft.blocks
        dispatch(EditorIntent.AddBlock(paragraph.id, BlockType.QUOTE))
        val added = draft.blocks[1]
        assertIs<EditorBlock.Quote>(added)
        dispatch(EditorIntent.AddBlock(null, BlockType.SUBHEAD))
        val first = draft.blocks[0]
        assertEquals(EditorBlock.Subhead(first.id, ""), first)

        dispatch(EditorIntent.MoveBlock(subhead.id, -1))
        assertEquals(listOf(first, paragraph, subhead, added, quote, list).map { it.id }, draft.blocks.map { it.id })

        dispatch(EditorIntent.MoveBlock(first.id, -1))
        dispatch(EditorIntent.MoveBlock(list.id, 1))
        dispatch(EditorIntent.Undo)
        dispatch(EditorIntent.Undo)
        dispatch(EditorIntent.Undo)
        assertEquals(listOf(paragraph, subhead, quote, list), draft.blocks)
        assertFalse(model.canUndo)
    }

    @Test
    fun aNewListStartsWithOneEmptyItemAndRemovingTheLastItemRemovesTheList() {
        dispatch(EditorIntent.AddBlock(null, BlockType.LIST))
        val list = draft.blocks[0] as EditorBlock.BulletList
        assertEquals(1, list.items.size)
        assertEquals(emptyList(), list.items[0].runs)

        dispatch(EditorIntent.AddListItem(list.id, list.items[0].id))
        val items = (draft.blocks[0] as EditorBlock.BulletList).items
        assertEquals(2, items.size)
        dispatch(EditorIntent.EditRuns(list.id, items[1].id, listOf(Run("Second"))))
        assertEquals(listOf(Run("Second")), (draft.blocks[0] as EditorBlock.BulletList).items[1].runs)

        dispatch(EditorIntent.RemoveListItem(list.id, items[0].id))
        dispatch(EditorIntent.RemoveListItem(list.id, items[1].id))
        assertEquals(4, draft.blocks.size)
        assertTrue(draft.blocks.none { it.id == list.id })
    }

    @Test
    fun actionsFollowAllowedActions() {
        assertEquals(EditorActions(editable = true, publish = true, takeOffline = false, delete = true), actionsFor(listOf("EDIT", "PUBLISH", "DELETE")))
        assertEquals(EditorActions(editable = false, publish = false, takeOffline = true, delete = false), actionsFor(listOf("TAKE_OFFLINE")))
        assertEquals(EditorActions(editable = false, publish = false, takeOffline = false, delete = false), actionsFor(emptyList()))
    }

    @Test
    fun reporterDraftOffersSubmitInsteadOfPublish() {
        val actions = actionsFor(listOf("EDIT", "SUBMIT", "DELETE"))
        assertEquals(EditorActions(editable = true, publish = false, takeOffline = false, delete = true, submit = true), actions)
    }

    @Test
    fun waitingArticleOffersWithdrawToTheAuthor() {
        val actions = actionsFor(listOf("WITHDRAW", "DELETE"))
        assertEquals(EditorActions(editable = false, publish = false, takeOffline = false, delete = true, withdraw = true), actions)
    }

    @Test
    fun waitingArticleOffersApproveAndRejectToTheApprover() {
        val actions = actionsFor(listOf("APPROVE", "REJECT"))
        assertEquals(
            EditorActions(editable = false, publish = false, takeOffline = false, delete = false, approve = true, reject = true),
            actions,
        )
    }

    @Test
    fun lockedArticleOffersUnlockToThePublisher() {
        val actions = actionsFor(listOf("UNLOCK"))
        assertEquals(EditorActions(editable = false, publish = false, takeOffline = false, delete = false, unlock = true), actions)
    }

    @Test
    fun afterApprovalTheSectionEditorKeepsNoChainAction() {
        val actions = actionsFor(emptyList())
        assertFalse(actions.approve || actions.reject || actions.submit || actions.withdraw)
    }

    private fun review(decision: String, note: String? = null) =
        ReviewDto(decision, "SECTION_EDITOR", 1, AuthorDto("nogroups", "No Groups"), note, "2026-09-27T10:00:00Z")

    @Test
    fun newestRejectionIsShownWhileNothingIsPending() {
        val rejection = review("REJECTED", "Please add who scored.")
        assertEquals(rejection, rejectionToShow(listOf(rejection, review("APPROVED")), pendingLevel = null))
    }

    @Test
    fun noRejectionShownWhileWaitingOrAfterApprovalOrWithoutReviews() {
        val rejection = review("REJECTED", "Please add who scored.")
        assertEquals(null, rejectionToShow(listOf(rejection), pendingLevel = "SECTION_EDITOR"))
        assertEquals(null, rejectionToShow(listOf(review("APPROVED"), rejection), pendingLevel = null))
        assertEquals(null, rejectionToShow(emptyList(), pendingLevel = null))
    }

    @Test
    fun rejectNeedsANote() {
        assertFalse(canConfirmReject(""))
        assertFalse(canConfirmReject("  \n "))
        assertTrue(canConfirmReject("Please add who scored."))
    }

    @Test
    fun noteIsCutAtTheLimitInCodePoints() {
        assertEquals(REJECT_NOTE_MAX, limitNote("x".repeat(REJECT_NOTE_MAX + 5)).length)
        assertEquals("a\nb", limitNote("a\nb"))
        val emoji = "😀".repeat(REJECT_NOTE_MAX + 1)
        assertEquals(2 * REJECT_NOTE_MAX, limitNote(emoji).length)
    }
}
