package info.unterrainer.presserl.admin.article

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)

/** Weights from semi-bold up count as bold, so pasted "strong" text keeps its emphasis. */
private fun SpanStyle.isBold(): Boolean = (fontWeight?.weight ?: FontWeight.Normal.weight) >= FontWeight.SemiBold.weight

/** Editor text of [runs]: one [FontWeight.Bold] span per bold run. */
fun runsToAnnotated(runs: List<Run>): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        if (run.bold) withStyle(BOLD) { append(run.text) } else append(run.text)
    }
}

/**
 * Runs of editor text: split at bold boundaries, adjacent runs with the same mark merged, no empty
 * runs. Line breaks become `\n`, tabs a space, other control characters are dropped (the server
 * allows no control character but line feed); every other formatting is ignored.
 */
fun annotatedToRuns(annotated: AnnotatedString): List<Run> {
    val text = annotated.text
    val bold = BooleanArray(text.length)
    annotated.spanStyles.filter { it.item.isBold() }.forEach { span ->
        for (i in span.start until minOf(span.end, text.length)) bold[i] = true
    }
    val runs = mutableListOf<Run>()
    val current = StringBuilder()
    var currentBold = false
    fun append(c: Char, isBold: Boolean) {
        if (current.isNotEmpty() && isBold != currentBold) {
            runs += Run(current.toString(), currentBold)
            current.clear()
        }
        currentBold = isBold
        current.append(c)
    }
    for (i in text.indices) {
        when (val c = text[i]) {
            '\r' -> if (text.getOrNull(i + 1) != '\n') append('\n', bold[i])
            '\n' -> append('\n', bold[i])
            '\t' -> append(' ', bold[i])
            else -> if (!c.isControlCharacter()) append(c, bold[i])
        }
    }
    if (current.isNotEmpty()) runs += Run(current.toString(), currentBold)
    return runs
}

/**
 * Text of a single-line field: line breaks and tabs become spaces, other control characters are
 * dropped, and the result is cut to [maxLength] code points (the server's unit).
 */
fun singleLine(text: String, maxLength: Int): String {
    val cleaned = buildString {
        for (c in text) {
            when {
                c == '\r' || c == '\n' || c == '\t' -> append(' ')
                !c.isControlCharacter() -> append(c)
            }
        }
    }
    return cleaned.takeCodePoints(maxLength)
}

private fun Char.isControlCharacter(): Boolean = this < ' ' || this in '\u007f'..'\u009f'

private fun String.takeCodePoints(max: Int): String {
    var count = 0
    var i = 0
    while (i < length) {
        if (count == max) return substring(0, i)
        i += if (this[i].isHighSurrogate() && getOrNull(i + 1)?.isLowSurrogate() == true) 2 else 1
        count++
    }
    return this
}

/**
 * [runs] with the characters [start] until [end] replaced by [text]. The new text takes the mark of the first
 * replaced character (of the character before it for an empty range), so a corrected bold word stays bold.
 */
fun replaceInRuns(runs: List<Run>, start: Int, end: Int, text: String): List<Run> {
    val all = runs.joinToString("") { it.text }
    val bold = BooleanArray(all.length)
    var at = 0
    runs.forEach { run ->
        for (i in run.text.indices) bold[at + i] = run.bold
        at += run.text.length
    }
    val newBold = bold.getOrNull(start) ?: bold.getOrNull(start - 1) ?: false
    val annotated = buildAnnotatedString {
        append(all.substring(0, start))
        append(text)
        append(all.substring(end))
        for (i in 0 until start) if (bold[i]) addStyle(BOLD, i, i + 1)
        if (newBold && text.isNotEmpty()) addStyle(BOLD, start, start + text.length)
        for (i in end until all.length) if (bold[i]) addStyle(BOLD, i - end + start + text.length, i - end + start + text.length + 1)
    }
    return annotatedToRuns(annotated)
}
