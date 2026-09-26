@file:OptIn(ExperimentalRichTextApi::class)

package info.unterrainer.presserl.admin

import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.document.RichTextBlock
import com.mohamedrejeb.richeditor.document.RichTextBlockType
import com.mohamedrejeb.richeditor.document.RichTextDocument
import com.mohamedrejeb.richeditor.document.RichTextSpanMark
import com.mohamedrejeb.richeditor.model.RichTextState
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.ui.editor.BOLD
import info.unterrainer.presserl.admin.ui.editor.load
import info.unterrainer.presserl.admin.ui.editor.revertLists
import info.unterrainer.presserl.admin.ui.editor.runs
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
