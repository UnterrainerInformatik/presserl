@file:OptIn(ExperimentalRichTextApi::class)

package info.unterrainer.presserl.admin.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.document.RichTextBlock
import com.mohamedrejeb.richeditor.document.RichTextBlockType
import com.mohamedrejeb.richeditor.document.RichTextDocument
import com.mohamedrejeb.richeditor.document.RichTextSpanMark
import com.mohamedrejeb.richeditor.model.RichTextState
import info.unterrainer.presserl.admin.article.Run
import info.unterrainer.presserl.admin.article.annotatedToRuns
import info.unterrainer.presserl.admin.article.runsToAnnotated

/*
 * compose-rich-editor sits only behind these functions (design D2): a run text's lines are the
 * editor's paragraphs, bold is its only mark.
 */

val BOLD = SpanStyle(fontWeight = FontWeight.Bold)

/** Replaces the content with [runs]; the caret goes to [caret] or the end. */
fun RichTextState.load(runs: List<Run>, caret: Int? = null) {
    val annotated = runsToAnnotated(runs)
    var start = 0
    val blocks = annotated.text.split('\n').map { line ->
        val end = start + line.length
        val marks = annotated.spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .mapNotNull { span ->
                val from = maxOf(span.start, start)
                val to = minOf(span.end, end)
                if (from < to) RichTextSpanMark.Bold((from - start) until (to - start)) else null
            }
        start = end + 1
        RichTextBlock(text = line, spans = marks)
    }
    setRichTextDocument(RichTextDocument(blocks), caret?.let { TextRange(it) })
}

/** The content as runs; list paragraphs (see [revertLists]) count with their prefix. */
fun RichTextState.runs(): List<Run> = annotatedToRuns(toAnnotated(toRichTextDocument()))

/**
 * Turns list paragraphs back into plain text with their prefix. The library starts a list when
 * "- ", "* " or "1. " is typed at the start of a line; format v1 knows no lists inside a paragraph.
 */
fun RichTextState.revertLists() {
    val document = toRichTextDocument()
    val first = document.blocks.indexOfFirst { it.type is RichTextBlockType.ListItem }
    if (first < 0) return
    val prefixes = prefixes(document.blocks)
    val caret = (0 until first).sumOf { prefixes[it].length + document.blocks[it].text.length + 1 } + prefixes[first].length
    load(annotatedToRuns(toAnnotated(document)), caret)
}

/**
 * Line breaks carry no style in the editor; one between two bold characters counts as bold, so bold
 * text across lines stays one run.
 */
private fun toAnnotated(document: RichTextDocument): AnnotatedString {
    val prefixes = prefixes(document.blocks)
    val text = StringBuilder()
    val bold = mutableListOf<Boolean>()
    document.blocks.forEachIndexed { index, block ->
        if (index > 0) {
            text.append('\n')
            bold += false
        }
        prefixes[index].forEach { text.append(it); bold += false }
        val offset = bold.size
        text.append(block.text)
        repeat(block.text.length) { bold += false }
        block.spans.filter { it.isBold() }.forEach { mark -> mark.range.forEach { bold[offset + it] = true } }
    }
    text.indices.filter { text[it] == '\n' && bold.getOrNull(it - 1) == true && bold.getOrNull(it + 1) == true }
        .forEach { bold[it] = true }
    return buildAnnotatedString {
        append(text.toString())
        bold.indices.filter { bold[it] }.forEach { addStyle(BOLD, it, it + 1) }
    }
}

private fun RichTextSpanMark.isBold() =
    this is RichTextSpanMark.Bold || (this is RichTextSpanMark.FontWeight && weight >= FontWeight.SemiBold.weight)

/** "- " for bullet items, "n. " for numbered items (counted per run of consecutive items). */
private fun prefixes(blocks: List<RichTextBlock>): List<String> {
    var number = 0
    return blocks.map { block ->
        val type = block.type
        if (type is RichTextBlockType.ListItem && type.ordered) {
            number = type.startNumber ?: (number + 1)
            "$number. "
        } else {
            number = 0
            if (type is RichTextBlockType.ListItem) "- " else ""
        }
    }
}
