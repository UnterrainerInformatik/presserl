package info.unterrainer.presserl.admin.ui.spell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.spell_ignore
import info.unterrainer.presserl.admin.resources.spell_ignore_label
import info.unterrainer.presserl.admin.resources.spell_replace_label
import info.unterrainer.presserl.admin.resources.spell_unavailable
import org.jetbrains.compose.resources.stringResource

/** The look of a finding: [color] (the theme's error colour) and underlined. */
fun spellMarkStyle(color: Color) = SpanStyle(color = color, textDecoration = TextDecoration.Underline)

/**
 * Marks [findings] with [style] without changing the text: the transformed text equals the input and offsets map
 * one to one, so value, caret and editing stay untouched.
 */
data class SpellMarks(val findings: List<SpellFinding>, val style: SpanStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        buildAnnotatedString {
            append(text)
            findings.filter { it.end <= text.length }.forEach { addStyle(style, it.start, it.end) }
        },
        OffsetMapping.Identity,
    )
}

/** The finding the collapsed caret [selection] lies in (its end included), if any. */
fun findingAt(findings: List<SpellFinding>, selection: TextRange): SpellFinding? =
    if (!selection.collapsed) null else findings.firstOrNull { selection.start in it.start..it.end }

/** [text] with [finding] replaced by [replacement]. */
fun replaceFinding(text: String, finding: SpellFinding, replacement: String): String =
    text.replaceRange(finding.start, finding.end, replacement)

/** The message the row below a field shows for [finding]; `null` when the server withheld it (spell-check help `marks`). */
fun rowMessage(finding: SpellFinding): String? = finding.message.takeIf { it.isNotBlank() }

/**
 * The explanation below a field: the finding's message (none when the newspaper's spell-check help withholds it), one
 * button per suggestion and *Ignore*. The buttons are ordinary focusable buttons with labels naming the word, so they
 * are reachable with Tab and screen readers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpellSuggestionRow(finding: SpellFinding, onReplace: (String) -> Unit, onIgnore: () -> Unit) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rowMessage(finding)?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            finding.replacements.forEach { replacement ->
                val label = stringResource(Res.string.spell_replace_label, finding.word, replacement)
                OutlinedButton(onClick = { onReplace(replacement) }, modifier = Modifier.semantics { contentDescription = label }) {
                    Text(replacement)
                }
            }
            val label = stringResource(Res.string.spell_ignore_label, finding.word)
            TextButton(onClick = onIgnore, modifier = Modifier.semantics { contentDescription = label }) {
                Text(stringResource(Res.string.spell_ignore))
            }
        }
    }
}

/** The one-time notice of an editor or form whose spell check is unavailable. */
@Composable
fun SpellNotice(checker: SpellChecker?) {
    if (checker?.notice == true) {
        Text(
            stringResource(Res.string.spell_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Checks the field [key] of [checker]: at once on first composition, afterwards after each change of [text].
 */
@Composable
fun CheckField(checker: SpellChecker?, key: String, text: String) {
    if (checker == null || !checker.enabled) return
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (opened) checker.changed(key, text) else checker.checkNow(key, text)
        opened = true
    }
}

/**
 * An [OutlinedTextField] whose findings are marked and explained below it while the caret is inside one. [onChange]
 * receives typed text, [onReplace] the text after a suggestion was chosen (an edit of its own). Without a [checker]
 * it is a plain text field.
 */
@Composable
fun SpellCheckedTextField(
    value: String,
    onChange: (String) -> Unit,
    checker: SpellChecker?,
    key: String,
    modifier: Modifier = Modifier,
    onReplace: (String) -> Unit = onChange,
    label: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    // Same bookkeeping as the String overload of OutlinedTextField, but the caret stays visible to us
    var state by remember { mutableStateOf(TextFieldValue(value)) }
    val field = state.copy(text = value)
    SideEffect {
        if (field.selection != state.selection || field.composition != state.composition) state = field
    }
    var lastText by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    CheckField(checker, key, value)
    val findings = if (checker != null && enabled) checker.findings(key, value) else emptyList()
    val style = spellMarkStyle(MaterialTheme.colorScheme.error)
    Column(modifier.onFocusChanged { focused = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = field,
            onValueChange = { changed ->
                state = changed
                val textChanged = lastText != changed.text
                lastText = changed.text
                if (textChanged) onChange(changed.text)
            },
            label = label,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle,
            visualTransformation = if (findings.isEmpty()) VisualTransformation.None else SpellMarks(findings, style),
            modifier = Modifier.fillMaxWidth(),
        )
        val finding = if (focused) findingAt(findings, field.selection) else null
        if (finding != null && checker != null) {
            SpellSuggestionRow(
                finding,
                onReplace = { replacement ->
                    val replaced = replaceFinding(value, finding, replacement)
                    state = TextFieldValue(replaced, TextRange(finding.start + replacement.length))
                    lastText = replaced
                    onReplace(replaced)
                },
                onIgnore = { checker.ignore(finding.word) },
            )
        }
    }
}
