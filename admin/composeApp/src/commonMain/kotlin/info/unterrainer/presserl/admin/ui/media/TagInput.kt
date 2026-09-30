package info.unterrainer.presserl.admin.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.api.MediaTagDto
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.media_tag_control
import info.unterrainer.presserl.admin.resources.media_tag_remove
import info.unterrainer.presserl.admin.resources.media_tag_too_long
import info.unterrainer.presserl.admin.resources.media_tag_too_many
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Longest tag the server accepts, in code points. */
const val MAX_TAG_LENGTH = 40

/** Most tags a media may carry. */
const val MAX_MEDIA_TAGS = 20

/** Most tags the media list filters by. */
const val MAX_FILTER_TAGS = 10

/** Why a typed tag was not added. */
enum class TagProblem { TOO_LONG, CONTROL, TOO_MANY }

/** Outer whitespace removed and every inner run of whitespace replaced by one space, as the server stores tags. */
fun normalizeTag(tag: String): String = tag.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

/** Why a normalised, non-empty tag would be refused by the server; `null` when it is fine (commas are split before). */
fun tagProblem(tag: String): TagProblem? = when {
    tag.count { !it.isLowSurrogate() } > MAX_TAG_LENGTH -> TagProblem.TOO_LONG
    tag.any { it.isISOControl() } -> TagProblem.CONTROL
    else -> null
}

/**
 * The chips of a tag input: [tags] in the order added, free of case-insensitive duplicates, at most [maxTags]; [text]
 * is what is typed but not added yet.
 */
class TagChips(initial: List<String> = emptyList(), private val maxTags: Int = MAX_MEDIA_TAGS) {
    var tags by mutableStateOf(initial)
        private set
    var text by mutableStateOf("")
    var problem by mutableStateOf<TagProblem?>(null)
        private set

    /**
     * Adds [raw] (the typed text by default; commas separate several tags) as chips and clears the text; a tag already
     * present in any case is not added again. Nothing is added when one of them is invalid or there would be too many.
     *
     * @return whether the text was taken
     */
    fun add(raw: String = text): Boolean {
        val parts = raw.split(',').map(::normalizeTag).filter { it.isNotEmpty() }
        parts.firstNotNullOfOrNull(::tagProblem)?.let {
            problem = it
            return false
        }
        val added = parts.fold(tags) { all, tag -> if (all.any { it.equals(tag, ignoreCase = true) }) all else all + tag }
        if (added.size > maxTags) {
            problem = TagProblem.TOO_MANY
            return false
        }
        tags = added
        text = ""
        problem = null
        return true
    }

    fun remove(tag: String) {
        tags = tags - tag
        problem = null
    }

    /** Replaces all chips and clears the typed text. */
    fun set(tags: List<String>) {
        this.tags = tags
        text = ""
        problem = null
    }
}

/**
 * A tag input: the chips (selecting one removes it), a text field that adds its text on Enter, and suggestions from
 * [suggest] while it is focused: the most used tags when empty, else those starting with the text (chosen ones left
 * out). [onChange] receives the tags after every change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInput(
    chips: TagChips,
    label: String,
    suggest: suspend (prefix: String) -> List<MediaTagDto>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (List<String>) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    // stays open a moment after the field loses focus, so a click on a suggestion still lands
    var open by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<MediaTagDto>>(emptyList()) }
    LaunchedEffect(focused) {
        if (focused) open = true else {
            delay(300)
            open = false
        }
    }
    LaunchedEffect(open, chips.text) {
        if (!open) return@LaunchedEffect
        delay(200)
        suggestions = try {
            suggest(normalizeTag(chips.text))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
    }
    val add = { if (chips.add()) onChange(chips.tags) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (chips.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val remove = stringResource(Res.string.media_tag_remove)
                chips.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        enabled = enabled,
                        onClick = {
                            chips.remove(tag)
                            onChange(chips.tags)
                        },
                        label = { Text(tag) },
                        trailingIcon = { Text("×", style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.semantics { contentDescription = "$remove: $tag" },
                    )
                }
            }
        }
        OutlinedTextField(
            value = chips.text,
            onValueChange = { chips.text = it },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = chips.problem != null,
            supportingText = chips.problem?.let { { Text(tagProblemText(it)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                        add()
                        true
                    } else {
                        false
                    }
                },
        )
        val offered = suggestions.filter { s -> chips.tags.none { it.equals(s.name, ignoreCase = true) } }
        if (open && enabled && offered.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                offered.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { if (chips.add(suggestion.name)) onChange(chips.tags) },
                        label = { Text("${suggestion.name} (${suggestion.count})") },
                    )
                }
            }
        }
    }
}

@Composable
fun tagProblemText(problem: TagProblem): String = when (problem) {
    TagProblem.TOO_LONG -> stringResource(Res.string.media_tag_too_long, MAX_TAG_LENGTH)
    TagProblem.CONTROL -> stringResource(Res.string.media_tag_control)
    TagProblem.TOO_MANY -> stringResource(Res.string.media_tag_too_many)
}
