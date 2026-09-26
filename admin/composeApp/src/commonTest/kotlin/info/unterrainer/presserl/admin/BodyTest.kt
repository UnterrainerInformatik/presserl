package info.unterrainer.presserl.admin

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.article.Block
import info.unterrainer.presserl.admin.article.Body
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.article.annotatedToRuns
import info.unterrainer.presserl.admin.article.runsToAnnotated
import info.unterrainer.presserl.admin.article.singleLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class BodyTest {

    private val exampleBody = json.decodeFromString<ArticleDto>(ARTICLE).body

    @Test
    fun exampleBodyIsTyped() {
        assertEquals(
            Body(
                blocks = listOf(
                    Block.Paragraph(listOf(Run("It started "), Run("in May", bold = true), Run("."))),
                    Block.Subhead("Watering"),
                    Block.Quote(listOf(Run("Every day!"))),
                    Block.BulletList(listOf(listOf(Run("Water")), listOf(Run("Sun")))),
                ),
            ),
            Body.fromJson(exampleBody),
        )
    }

    @Test
    fun exampleBodyRoundTripsUnchanged() {
        assertEquals(exampleBody, Body.fromJson(exampleBody).toJson())
    }

    @Test
    fun exampleBodyRoundTripsThroughTheEditorText() {
        val body = Body.fromJson(exampleBody)
        val throughEditor = body.copy(
            blocks = body.blocks.map { block ->
                when (block) {
                    is Block.Paragraph -> Block.Paragraph(annotatedToRuns(runsToAnnotated(block.content)))
                    is Block.Quote -> Block.Quote(annotatedToRuns(runsToAnnotated(block.content)))
                    is Block.BulletList -> Block.BulletList(block.items.map { annotatedToRuns(runsToAnnotated(it)) })
                    is Block.Subhead -> block
                }
            },
        )
        assertEquals(exampleBody, throughEditor.toJson())
    }

    @Test
    fun emptyBlocksSerialiseAsAcceptedByTheServer() {
        val body = Body(
            blocks = listOf(
                Block.Paragraph(emptyList()),
                Block.Subhead(""),
                Block.Quote(emptyList()),
                Block.BulletList(listOf(emptyList())),
            ),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{ "version": 1, "blocks": [
                    { "type": "paragraph", "content": [] },
                    { "type": "subhead", "text": "" },
                    { "type": "quote", "content": [] },
                    { "type": "list", "items": [ [] ] } ] }""",
            ).jsonObject,
            body.toJson(),
        )
    }

    @Test
    fun emptyBodyHasVersionAndNoBlocks() {
        assertEquals(Json.parseToJsonElement("""{ "version": 1, "blocks": [] }""").jsonObject, Body(blocks = emptyList()).toJson())
    }

    @Test
    fun boldAtStartAndEnd() {
        val runs = listOf(Run("Big", bold = true), Run(" pumpkin "), Run("today", bold = true))
        assertEquals(runs, annotatedToRuns(runsToAnnotated(runs)))
    }

    @Test
    fun adjacentRunsWithTheSameMarkAreMerged() {
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Big") }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" pumpkin") }
            append("!")
        }
        assertEquals(listOf(Run("Big pumpkin", bold = true), Run("!")), annotatedToRuns(annotated))
        assertEquals(listOf(Run("ab")), annotatedToRuns(runsToAnnotated(listOf(Run("a"), Run("b")))))
    }

    @Test
    fun otherFormattingIsDroppedAndSemiBoldCountsAsBold() {
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("slanted ") }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("strong") }
        }
        assertEquals(listOf(Run("slanted "), Run("strong", bold = true)), annotatedToRuns(annotated))
    }

    @Test
    fun emptyParagraphHasNoRuns() {
        assertEquals(emptyList(), annotatedToRuns(runsToAnnotated(emptyList())))
    }

    @Test
    fun listWithAnEmptyItem() {
        val items = listOf(listOf(Run("Water")), emptyList())
        assertEquals(items, items.map { annotatedToRuns(runsToAnnotated(it)) })
    }

    @Test
    fun pastedLineBreaksAndControlCharactersAreNormalised() {
        val annotated = buildAnnotatedString { append("one\r\ntwo\rthree\n\tfour\u0007") }
        assertEquals(listOf(Run("one\ntwo\nthree\n four")), annotatedToRuns(annotated))
    }

    @Test
    fun singleLineFieldsHaveNoLineBreaksAndAreCutInCodePoints() {
        assertEquals("The pumpkin is huge", singleLine("The pumpkin\nis huge", 200))
        assertEquals("ab", singleLine("abc", 2))
        assertEquals("🎃🎃", singleLine("🎃🎃🎃", 2))
    }
}
