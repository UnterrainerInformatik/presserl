package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ArticleCountsDto
import info.unterrainer.presserl.admin.api.IssueCountDto
import info.unterrainer.presserl.admin.ui.section.countLines
import kotlin.test.Test
import kotlin.test.assertEquals

/** The count lines of the section list with the German and English templates of the string resources. */
class SectionCountsTest {

    private val counts = ArticleCountsDto(2, 4, listOf(IssueCountDto(12, 2, 2), IssueCountDto(11, 1, 1)))

    @Test
    fun german() {
        assertEquals(
            listOf("2 online · 4 gesamt", "Ausgabe 2: 2 · Ausgabe 1: 1"),
            countLines(counts, { live, total -> "$live online · $total gesamt" }, { number, count -> "Ausgabe $number: $count" }),
        )
    }

    @Test
    fun english() {
        assertEquals(
            listOf("2 online · 4 in total", "Issue 2: 2 · Issue 1: 1"),
            countLines(counts, { live, total -> "$live online · $total in total" }, { number, count -> "Issue $number: $count" }),
        )
    }

    @Test
    fun emptySectionHasNoIssueLine() {
        assertEquals(
            listOf("0 online · 0 gesamt"),
            countLines(ArticleCountsDto(0, 0), { live, total -> "$live online · $total gesamt" }, { number, count -> "Ausgabe $number: $count" }),
        )
    }
}
