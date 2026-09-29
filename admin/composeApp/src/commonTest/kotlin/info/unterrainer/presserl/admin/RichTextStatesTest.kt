@file:OptIn(ExperimentalRichTextApi::class)

package info.unterrainer.presserl.admin

import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.document.RichTextBlock
import com.mohamedrejeb.richeditor.document.RichTextBlockType
import com.mohamedrejeb.richeditor.document.RichTextDocument
import com.mohamedrejeb.richeditor.document.RichTextSpanMark
import com.mohamedrejeb.richeditor.model.RichTextState
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.article.replaceInRuns
import info.unterrainer.presserl.admin.ui.editor.BOLD
import info.unterrainer.presserl.admin.ui.editor.load
import info.unterrainer.presserl.admin.ui.editor.revertLists
import info.unterrainer.presserl.admin.ui.editor.runs
import kotlin.test.Test
import info.unterrainer.presserl.admin.ui.spell.showsText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichTextStatesTest {

    @Test
    fun runsWithLineBreaksAndBoldRoundTrip() {
        val runs = listOf(Run("It started "), Run("in\nMay", bold = true), Run(".\n\nThe end"))
        val state = RichTextState().apply { load(runs) }

        assertEquals(runs, state.runs())
    }

    @Test
    fun emptyRunsLoadAsAnEmptyEditor() {
        assertEquals(emptyList(), RichTextState().apply { load(emptyList()) }.runs())
    }

    @Test
    fun boldToggledOnASelectionBecomesABoldRun() {
        val state = RichTextState().apply { load(listOf(Run("Big pumpkin"))) }
        state.selection = androidx.compose.ui.text.TextRange(0, 3)
        state.toggleSpanStyle(BOLD)

        assertEquals(listOf(Run("Big", bold = true), Run(" pumpkin")), state.runs())
    }

    @Test
    fun listParagraphsAreTurnedBackIntoTypedText() {
        val state = RichTextState()
        state.setRichTextDocument(
            RichTextDocument(
                listOf(
                    RichTextBlock("Winners"),
                    RichTextBlock("Anna", RichTextBlockType.ListItem(ordered = true), listOf(RichTextSpanMark.Bold(0..3))),
                    RichTextBlock("Ben", RichTextBlockType.ListItem(ordered = true)),
                    RichTextBlock("pumpkins", RichTextBlockType.ListItem(ordered = false)),
                ),
            ),
        )

        state.revertLists()

        assertEquals(listOf(Run("Winners\n1. "), Run("Anna", bold = true), Run("\n2. Ben\n- pumpkins")), state.runs())
        assertEquals(RichTextDocument(listOf(RichTextBlock("Winners"), RichTextBlock("1. Anna", spans = listOf(RichTextSpanMark.Bold(3..6))),
            RichTextBlock("2. Ben"), RichTextBlock("- pumpkins"))), state.toRichTextDocument())
        assertEquals(11, state.selection.start)
    }

    @Test
    fun theEditorShowsExactlyTheTextOfItsRuns() {
        // The spell-check marks are drawn at offsets of the runs text over the editor's text layout
        val runs = listOf(Run("Der "), Run("Hund", bold = true), Run(" ist gros.\nZweite Zeile"))
        val state = RichTextState().apply { load(runs) }

        assertTrue(showsText(state.annotatedString.text, runs.joinToString("") { it.text }))
        assertFalse(showsText("Der Hund ist gros! Zweite Zeile", runs.joinToString("") { it.text }))
    }

    @Test
    fun replacingInsideABoldRunKeepsItBold() {
        val runs = listOf(Run("Der Hund ist "), Run("gros", bold = true), Run("."))

        assertEquals(listOf(Run("Der Hund ist "), Run("groß", bold = true), Run(".")), replaceInRuns(runs, 13, 17, "groß"))
    }

    @Test
    fun replacingPlainTextNextToBoldStaysPlain() {
        val runs = listOf(Run("Wir haben "), Run("einen", bold = true), Run(" hund."))

        assertEquals(listOf(Run("Wir haben "), Run("einen", bold = true), Run(" Hund.")), replaceInRuns(runs, 16, 20, "Hund"))
    }

    @Test
    fun aReplacementLoadedIntoTheEditorKeepsBoldRuns() {
        val state = RichTextState().apply { load(listOf(Run("Ein "), Run("groser", bold = true), Run(" Hund"))) }
        val replaced = replaceInRuns(state.runs(), 4, 10, "großer")
        state.load(replaced, caret = 10)

        assertEquals(listOf(Run("Ein "), Run("großer", bold = true), Run(" Hund")), state.runs())
        assertEquals(androidx.compose.ui.text.TextRange(10), state.selection)
    }
}
