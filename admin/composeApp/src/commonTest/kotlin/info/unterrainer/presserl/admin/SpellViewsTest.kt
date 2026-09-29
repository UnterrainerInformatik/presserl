package info.unterrainer.presserl.admin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import info.unterrainer.presserl.admin.ui.spell.SpellFinding
import info.unterrainer.presserl.admin.ui.spell.SpellMarks
import info.unterrainer.presserl.admin.ui.spell.findingAt
import info.unterrainer.presserl.admin.ui.spell.replaceFinding
import info.unterrainer.presserl.admin.ui.spell.rowMessage
import info.unterrainer.presserl.admin.ui.spell.spellMarkStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SpellViewsTest {

    private val gros = SpellFinding(13, 17, "gros", "Tippfehler", listOf("groß"))
    private val hund = SpellFinding(4, 8, "hund", "Großschreibung", listOf("Hund"))
    private val style = spellMarkStyle(Color.Red)

    @Test
    fun marksChangeNeitherTextNorOffsets() {
        val transformed = SpellMarks(listOf(hund, gros), style).filter(AnnotatedString("Der hund ist gros."))

        assertEquals("Der hund ist gros.", transformed.text.text)
        assertSame(OffsetMapping.Identity, transformed.offsetMapping)
        assertEquals(listOf(4 to 8, 13 to 17), transformed.text.spanStyles.map { it.start to it.end })
        transformed.text.spanStyles.forEach { assertEquals(style, it.item) }
    }

    @Test
    fun findingsBeyondTheTextAreNotMarked() {
        val transformed = SpellMarks(listOf(gros), style).filter(AnnotatedString("Der"))

        assertEquals(emptyList(), transformed.text.spanStyles)
    }

    @Test
    fun theCaretSelectsTheFindingItStandsIn() {
        val findings = listOf(hund, gros)

        assertEquals(gros, findingAt(findings, TextRange(13)))
        assertEquals(gros, findingAt(findings, TextRange(17)))
        assertEquals(hund, findingAt(findings, TextRange(6)))
        assertNull(findingAt(findings, TextRange(10)))
        assertNull(findingAt(findings, TextRange(13, 15)))
    }

    @Test
    fun aSuggestionReplacesExactlyTheFinding() {
        assertEquals("Der Hund ist groß.", replaceFinding("Der Hund ist gros.", gros, "groß"))
    }

    @Test
    fun theRowShowsTheMessageOnlyWhenTheServerSentOne() {
        assertEquals("Tippfehler", rowMessage(gros))
        // spell-check help "marks": no message and no replacements, so the row offers Ignore only
        val marked = SpellFinding(13, 17, "gros", "", emptyList())
        assertNull(rowMessage(marked))
        assertNull(rowMessage(marked.copy(message = " ")))
    }
}
