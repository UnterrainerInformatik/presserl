package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.LeadImageDto
import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.article.Block
import info.unterrainer.presserl.admin.article.Body
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.ui.diff.BlockChange
import info.unterrainer.presserl.admin.ui.diff.ComparedField
import info.unterrainer.presserl.admin.ui.diff.DiffKind.ADDED
import info.unterrainer.presserl.admin.ui.diff.DiffKind.REMOVED
import info.unterrainer.presserl.admin.ui.diff.DiffKind.SAME
import info.unterrainer.presserl.admin.ui.diff.DiffPart
import info.unterrainer.presserl.admin.ui.diff.blockDiff
import info.unterrainer.presserl.admin.ui.diff.changed
import info.unterrainer.presserl.admin.ui.diff.compare
import info.unterrainer.presserl.admin.ui.diff.loadComparison
import info.unterrainer.presserl.admin.ui.diff.wordDiff
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordDiffTest {

    private fun paragraph(text: String) = Block.Paragraph(listOf(Run(text)))

    private fun revision(number: Int, headline: String, blocks: List<Block> = emptyList(), leadImage: LeadImageDto? = null) =
        RevisionDto(
            number = number,
            headline = headline,
            author = AuthorDto(if (number == 1) "reader" else "chief", if (number == 1) "Reader" else "Lena"),
            createdAt = "t",
            updatedAt = "t",
            live = false,
            kicker = "",
            subheadline = "",
            lead = "",
            body = Body(blocks = blocks).toJson(),
            leadImage = leadImage,
        )

    @Test
    fun headlineWordChange() {
        assertEquals(
            listOf(DiffPart(SAME, "Wir gewinnen "), DiffPart(REMOVED, "gros"), DiffPart(ADDED, "groß")),
            wordDiff("Wir gewinnen gros", "Wir gewinnen groß"),
        )
    }

    @Test
    fun wordsInTheMiddleAndSpacing() {
        assertEquals(
            listOf(DiffPart(SAME, "Der "), DiffPart(ADDED, "große "), DiffPart(SAME, "Hund bellt")),
            wordDiff("Der Hund bellt", "Der große Hund bellt"),
        )
        assertEquals(listOf(DiffPart(REMOVED, "alt")), wordDiff("alt", ""))
        assertEquals(listOf(DiffPart(ADDED, "neu")), wordDiff("", "neu"))
    }

    @Test
    fun identicalTexts() {
        assertEquals(listOf(DiffPart(SAME, "Gleich bleibt gleich.")), wordDiff("Gleich bleibt gleich.", "Gleich bleibt gleich."))
        assertEquals(emptyList(), wordDiff("", ""))
        assertFalse(wordDiff("a b", "a b").changed())
    }

    @Test
    fun addedParagraph() {
        val first = paragraph("Erster Absatz.")
        val second = paragraph("Zweiter Absatz.")
        val added = paragraph("Neuer Absatz.")

        assertEquals(
            listOf(BlockChange.Same(first), BlockChange.Added(added), BlockChange.Same(second)),
            blockDiff(listOf(first, second), listOf(first, added, second)),
        )
    }

    @Test
    fun removedBlock() {
        val first = paragraph("Erster Absatz.")
        val subhead = Block.Subhead("Zwischentitel")

        assertEquals(
            listOf(BlockChange.Same(first), BlockChange.Removed(subhead)),
            blockDiff(listOf(first, subhead), listOf(first)),
        )
    }

    @Test
    fun changedParagraphShowsItsWordChanges() {
        val before = paragraph("Der Hund ist gros.")
        val after = paragraph("Der Hund ist groß.")

        val change = blockDiff(listOf(before), listOf(after)).single()

        assertEquals(
            BlockChange.Changed(before, after, listOf(DiffPart(SAME, "Der Hund ist "), DiffPart(REMOVED, "gros."), DiffPart(ADDED, "groß.")),
            ),
            change,
        )
    }

    @Test
    fun replacedImage() {
        val before = Block.Image(17, "Unsere Klasse")
        val after = Block.Image(18, "Unsere Klasse")

        val change = blockDiff(listOf(before), listOf(after)).single() as BlockChange.Changed

        assertTrue(change.imageReplaced)
        assertFalse(change.parts.changed())
    }

    @Test
    fun changedCaption() {
        val before = Block.Image(17, "Unser Klasenfoto")
        val after = Block.Image(17, "Unser Klassenfoto")

        val change = blockDiff(listOf(before), listOf(after)).single() as BlockChange.Changed

        assertFalse(change.imageReplaced)
        assertEquals(listOf(DiffPart(SAME, "Unser "), DiffPart(REMOVED, "Klasenfoto"), DiffPart(ADDED, "Klassenfoto")), change.parts)
    }

    @Test
    fun blocksOfAnotherTypeAreNotPaired() {
        val before = paragraph("Text")
        val after = Block.Quote(listOf(Run("Text")))

        assertEquals(listOf(BlockChange.Removed(before), BlockChange.Added(after)), blockDiff(listOf(before), listOf(after)))
    }

    @Test
    fun comparisonOfTwoRevisions() {
        val older = revision(1, "Wir gewinnen gros", listOf(paragraph("Tor!")), LeadImageDto(17, "Jubel", 10, 10))
        val newer = revision(2, "Wir gewinnen groß", listOf(paragraph("Tor!")), LeadImageDto(18, "Jubel", 10, 10))

        val comparison = compare(older, newer)

        assertTrue(comparison.fields.getValue(ComparedField.HEADLINE).changed())
        assertFalse(comparison.fields.getValue(ComparedField.LEAD).changed())
        assertFalse(comparison.fields.getValue(ComparedField.LEAD_IMAGE_CAPTION).changed())
        assertTrue(comparison.leadImageReplaced)
        assertEquals(listOf(BlockChange.Same(paragraph("Tor!"))), comparison.blocks)
        assertEquals("Lena", comparison.newer.author?.displayName)
    }

    @Test
    fun loadingARevisionAndItsPredecessor() = runTest {
        val loaded = mutableListOf<Int>()
        val comparison = loadComparison(2) { number ->
            loaded += number
            revision(number, "H$number")
        }

        assertEquals(listOf(2, 1), loaded)
        assertEquals(1, comparison.older.number)
        assertEquals(2, comparison.newer.number)
        assertFailsWith<IllegalArgumentException> { loadComparison(1) { revision(it, "H") } }
    }
}
