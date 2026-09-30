package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiClient
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.editor.Autosaver
import info.unterrainer.presserl.admin.ui.editor.BlockType
import info.unterrainer.presserl.admin.ui.editor.EditorModel
import info.unterrainer.presserl.admin.ui.editor.FieldHelpState
import info.unterrainer.presserl.admin.ui.editor.HeaderField
import info.unterrainer.presserl.admin.ui.editor.HelpPart
import info.unterrainer.presserl.admin.ui.editor.IdSource
import info.unterrainer.presserl.admin.ui.editor.SaveState
import info.unterrainer.presserl.admin.ui.editor.draftOf
import info.unterrainer.presserl.admin.ui.editor.explanation
import info.unterrainer.presserl.admin.ui.editor.helpPart
import info.unterrainer.presserl.admin.ui.editor.label
import info.unterrainer.presserl.admin.ui.editor.sample
import info.unterrainer.presserl.admin.ui.editor.showsImageRights
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class FieldHelpTest {

    @Test
    fun everyEditorPartHasItsOwnHelp() {
        val header = HeaderField.entries.map { it.helpPart }
        val blocks = BlockType.entries.map { it.helpPart }
        val all = header + blocks + listOf(HelpPart.SECTION, HelpPart.LEAD_IMAGE, HelpPart.CAPTION)

        assertEquals(all.size, all.toSet().size, "two editor parts share a help part: $all")
        assertEquals(HelpPart.entries.toSet(), all.toSet())
    }

    @Test
    fun partsFollowTheReaderOrderWithTheImageBlockLast() {
        assertEquals(
            listOf(HelpPart.SECTION, HelpPart.KICKER, HelpPart.HEADLINE, HelpPart.SUBHEADLINE, HelpPart.LEAD_IMAGE,
                HelpPart.CAPTION, HelpPart.LEAD, HelpPart.PARAGRAPH, HelpPart.SUBHEAD, HelpPart.QUOTE, HelpPart.LIST,
                HelpPart.IMAGE),
            HelpPart.entries,
        )
        assertEquals(HelpPart.IMAGE, BlockType.IMAGE.helpPart)
    }

    @Test
    fun theSampleShowsEveryPartOnce() {
        // One sample article for all explanations: every part has its own text in it
        assertEquals(HelpPart.entries.size, HelpPart.entries.map { it.sample }.toSet().size)
        assertEquals(HelpPart.entries.size, HelpPart.entries.map { it.explanation }.toSet().size)
        assertEquals(HelpPart.entries.size, HelpPart.entries.map { it.label }.toSet().size)
    }

    @Test
    fun imageRightsOnBothPhotoParts() {
        assertEquals(listOf(HelpPart.LEAD_IMAGE, HelpPart.IMAGE), HelpPart.entries.filter { it.showsImageRights })
    }

    @Test
    fun onlyOneExplanationIsOpen() {
        val help = FieldHelpState()
        help.hover(HelpPart.KICKER)
        help.hover(HelpPart.HEADLINE)
        assertEquals(HelpPart.HEADLINE, help.open)

        help.click(HelpPart.QUOTE)
        assertEquals(HelpPart.QUOTE, help.open)
        // A pinned explanation stays while the pointer passes other buttons
        help.hover(HelpPart.LEAD)
        help.leave(HelpPart.LEAD)
        assertEquals(HelpPart.QUOTE, help.open)
    }

    @Test
    fun pointingOpensUntilThePointerLeaves() {
        val help = FieldHelpState()
        help.hover(HelpPart.KICKER)
        assertEquals(HelpPart.KICKER, help.open)
        help.leave(HelpPart.KICKER)
        assertNull(help.open)
    }

    @Test
    fun aClickPinsAndASecondClickCloses() {
        val help = FieldHelpState()
        help.hover(HelpPart.KICKER)
        help.click(HelpPart.KICKER)
        help.focus(HelpPart.KICKER)
        help.leave(HelpPart.KICKER)
        assertEquals(HelpPart.KICKER, help.open)

        help.click(HelpPart.KICKER)
        assertNull(help.open)
    }

    @Test
    fun aClickOnTheOwnButtonAfterItsPressDismissedThePinnedExplanationClosesIt() {
        var now = 0L
        val help = FieldHelpState(clock = { now })
        help.click(HelpPart.KICKER)

        // The press outside the explanation dismisses it; hover or focus may reopen it before the click arrives
        help.dismiss()
        help.hover(HelpPart.KICKER)
        now += 100
        help.click(HelpPart.KICKER)

        assertNull(help.open)
    }

    @Test
    fun pointingReopensAClickedShutExplanationOnlyAfterLeaving() {
        val help = FieldHelpState()
        help.click(HelpPart.KICKER)
        help.click(HelpPart.KICKER)

        help.hover(HelpPart.KICKER)
        assertNull(help.open)

        help.leave(HelpPart.KICKER)
        help.hover(HelpPart.KICKER)
        assertEquals(HelpPart.KICKER, help.open)
    }

    @Test
    fun aClickOnTheOwnButtonPinsAnExplanationOpenedByPointing() {
        var now = 0L
        val help = FieldHelpState(clock = { now })
        help.hover(HelpPart.KICKER)

        help.dismiss()
        now += 100
        help.click(HelpPart.KICKER)
        help.leave(HelpPart.KICKER)

        assertEquals(HelpPart.KICKER, help.open)
    }

    @Test
    fun aPressElsewhereClosesAndALaterClickOpensAgain() {
        var now = 0L
        val help = FieldHelpState(clock = { now })
        help.click(HelpPart.KICKER)

        help.dismiss()
        assertNull(help.open)
        now += 2_000
        help.click(HelpPart.KICKER)
        assertEquals(HelpPart.KICKER, help.open)

        help.dismiss()
        help.click(HelpPart.QUOTE)
        assertEquals(HelpPart.QUOTE, help.open)
    }

    @Test
    fun focusOpensUntilFocusLeavesOrEscape() {
        val help = FieldHelpState()
        help.focus(HelpPart.SUBHEADLINE)
        assertEquals(HelpPart.SUBHEADLINE, help.open)
        help.close()
        assertNull(help.open)

        help.focus(HelpPart.SUBHEADLINE)
        help.blur(HelpPart.SUBHEADLINE)
        assertNull(help.open)
    }

    @Test
    fun usingHelpNeitherChangesTheArticleNorSaves() = runTest {
        val requests = mutableListOf<String>()
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(testScheduler)
                addHandler { request ->
                    requests += "${request.method.value} ${request.url.encodedPath}"
                    respondError(HttpStatusCode.InternalServerError)
                }
            },
        )
        val api = ApiClient(HttpClient(engine), "https://news.example.org") { "token" }
        val article = json.decodeFromString<ArticleDto>(ARTICLE)
        val ids = IdSource()
        val model = EditorModel(draftOf(article, ids), ids, clock = { 0L })
        val autosaver = Autosaver(backgroundScope, model.draft.toContent(), article.version, save = { content, version ->
            api.updateArticle(article.id, content, version)
        })
        val draft = model.draft

        val help = FieldHelpState()
        HelpPart.entries.forEach { part ->
            help.hover(part)
            help.leave(part)
            help.focus(part)
            help.click(part)
            help.dismiss()
            help.click(part)
            help.close()
        }
        advanceTimeBy(10_000)
        runCurrent()

        assertSame(draft, model.draft)
        assertFalse(model.canUndo)
        assertEquals(SaveState.Saved, autosaver.state.value)
        assertEquals(emptyList(), requests)
    }
}
