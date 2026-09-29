package info.unterrainer.presserl.admin.ui.spell

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Marks [findings] in a rich-text editor by drawing over it (design D6): the glyphs of each finding are recoloured
 * with [color] and underlined, while the editor's state (spans, history, selection) stays untouched. [layout] is the
 * editor's last text layout, whose text starts at [originX]/[originY] inside the modified element; nothing is drawn
 * while the laid-out text differs from [text], the text the findings belong to (see [showsText]).
 */
fun Modifier.spellMarksOverlay(
    findings: List<SpellFinding>,
    text: String,
    layout: () -> TextLayoutResult?,
    color: Color,
    originX: Dp,
    originY: Dp,
): Modifier =
    if (findings.isEmpty()) {
        this
    } else {
        // Offscreen, so the recolouring only reaches pixels the editor itself has drawn
        graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            val laidOut = layout() ?: return@drawWithContent
            if (!showsText(laidOut.layoutInput.text.text, text)) return@drawWithContent
            val stroke = 1.dp.toPx()
            translate(originX.toPx(), originY.toPx()) {
                findings.filter { it.end <= text.length }.forEach { finding ->
                    drawPath(laidOut.getPathForRange(finding.start, finding.end), color, blendMode = BlendMode.SrcAtop)
                    val firstLine = laidOut.getLineForOffset(finding.start)
                    val lastLine = laidOut.getLineForOffset(maxOf(finding.start, finding.end - 1))
                    for (line in firstLine..lastLine) {
                        val start = maxOf(finding.start, laidOut.getLineStart(line))
                        val end = minOf(finding.end, laidOut.getLineEnd(line, visibleEnd = true))
                        if (start >= end) continue
                        val y = laidOut.getLineBottom(line) - stroke
                        drawLine(
                            color,
                            Offset(laidOut.getBoundingBox(start).left, y),
                            Offset(laidOut.getBoundingBox(end - 1).right, y),
                            strokeWidth = stroke,
                        )
                    }
                }
            }
        }
    }

/**
 * Whether an editor laying out [laidOut] shows [text] character for character. compose-rich-editor separates its
 * paragraphs with a space where the runs have a line break; the offsets are the same.
 */
fun showsText(laidOut: String, text: String): Boolean =
    laidOut.length == text.length && laidOut.indices.all { laidOut[it] == text[it] || (text[it] == '\n' && laidOut[it] == ' ') }
