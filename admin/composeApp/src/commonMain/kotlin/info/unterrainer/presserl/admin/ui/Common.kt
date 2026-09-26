package info.unterrainer.presserl.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.article.runsToAnnotated
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.back
import info.unterrainer.presserl.admin.resources.no_headline
import info.unterrainer.presserl.admin.resources.reload
import info.unterrainer.presserl.admin.resources.something_went_wrong
import info.unterrainer.presserl.admin.ui.editor.Draft
import info.unterrainer.presserl.admin.ui.editor.EditorBlock
import org.jetbrains.compose.resources.stringResource

@Composable
fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text("← " + stringResource(Res.string.back)) }
}

/** A message across the content width, e.g. a conflict or a server message. */
@Composable
fun Banner(text: String, color: Color = MaterialTheme.colorScheme.errorContainer, action: @Composable (() -> Unit)? = null) {
    Surface(color = color, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, Modifier.weight(1f))
            action?.invoke()
        }
    }
}

@Composable
fun LoadFailed(message: String, onReload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.something_went_wrong, message))
        Button(onClick = onReload) { Text(stringResource(Res.string.reload)) }
    }
}

/** Read-only view of article content in the newspaper's hierarchy (revisions, articles without `EDIT`). */
@Composable
fun ArticleView(draft: Draft) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.kicker.isNotEmpty()) Text(draft.kicker, style = MaterialTheme.typography.labelLarge)
        Text(draft.headline.ifEmpty { stringResource(Res.string.no_headline) }, style = MaterialTheme.typography.headlineMedium)
        if (draft.subheadline.isNotEmpty()) Text(draft.subheadline, style = MaterialTheme.typography.titleMedium)
        if (draft.lead.isNotEmpty()) Text(draft.lead, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        draft.blocks.forEach { block ->
            when (block) {
                is EditorBlock.Paragraph -> Text(runsToAnnotated(block.runs), style = MaterialTheme.typography.bodyLarge)
                is EditorBlock.Subhead -> Text(block.text, style = MaterialTheme.typography.titleMedium)
                is EditorBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    Text(runsToAnnotated(block.runs), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
                is EditorBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row {
                            Text("•", Modifier.width(20.dp), style = MaterialTheme.typography.bodyLarge)
                            Text(runsToAnnotated(item.runs), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
