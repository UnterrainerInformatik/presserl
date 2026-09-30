package info.unterrainer.presserl.admin.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.help_image_rights
import info.unterrainer.presserl.admin.ui.Icons
import info.unterrainer.presserl.admin.ui.media.PictureIcon
import info.unterrainer.presserl.admin.ui.section.ColorMarker
import info.unterrainer.presserl.admin.ui.SymbolIcon
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

/**
 * Which field explanation is open, if any; at most one is. One opened by pointing closes when the pointer leaves,
 * one opened by focus when focus leaves, one opened by clicking only on another click, Escape or a click outside.
 * It touches neither the article nor the autosaver. [clock] gives milliseconds for telling a click on the own button
 * from a click elsewhere.
 */
class FieldHelpState(private val clock: () -> Long = monotonicMillis()) {
    private enum class Source { HOVER, FOCUS, CLICK }

    /** An explanation closed by a press outside it; the click that press belongs to may still follow. */
    private class Dismissal(val part: HelpPart, val source: Source, val at: Long)

    private var source: Source? = null
    private var dismissal: Dismissal? = null

    /** Closed by a click while the pointer is on its button: pointing reopens it only after leaving. */
    private var hoverBlocked: HelpPart? = null

    var open by mutableStateOf<HelpPart?>(null)
        private set

    /** Pointing opens the part unless another explanation is pinned by focus or click. */
    fun hover(part: HelpPart) {
        if (part == hoverBlocked) return
        if (open == null || source == Source.HOVER) show(part, Source.HOVER)
    }

    fun leave(part: HelpPart) {
        if (part == hoverBlocked) hoverBlocked = null
        if (open == part && source == Source.HOVER) close()
    }

    fun focus(part: HelpPart) {
        if (open != part || source == Source.HOVER) show(part, Source.FOCUS)
    }

    fun blur(part: HelpPart) {
        if (open == part && source == Source.FOCUS) close()
    }

    /**
     * Clicking pins the part's explanation; clicking a pinned one closes it. The press of a click on the own button
     * already dismissed the explanation, so what was open then decides.
     */
    fun click(part: HelpPart) {
        val dismissed = dismissal?.takeIf { it.part == part && clock() - it.at < DISMISS_CLICK_WINDOW_MS }
        dismissal = null
        val pinned = if (dismissed != null) dismissed.source == Source.CLICK else open == part && source == Source.CLICK
        if (pinned) {
            close()
            hoverBlocked = part
        } else {
            show(part, Source.CLICK)
        }
    }

    /** A press outside the open explanation, which may be on its own button. */
    fun dismiss() {
        val part = open ?: return
        dismissal = Dismissal(part, source ?: Source.CLICK, clock())
        close()
    }

    fun close() {
        open = null
        source = null
    }

    private fun show(part: HelpPart, from: Source) {
        open = part
        source = from
    }
}

/** Longest time from the press that dismissed an explanation to the click on its button that press started. */
private const val DISMISS_CLICK_WINDOW_MS = 500L

private fun monotonicMillis(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}

/** How long the pointer may be off both button and explanation before a pointed-at explanation closes. */
private const val HOVER_CLOSE_DELAY_MS = 150L

/**
 * The question-mark button of [part] (44 dp touch target, labelled "What is …?") and, while open, its explanation
 * below it (above it when there is no room): the child-level text, for the lead image the image-rights note, and
 * the [SampleArticle] with [part] highlighted. The explanation never takes focus, so typing goes on.
 */
@Composable
fun FieldHelp(part: HelpPart, help: FieldHelpState) {
    val buttonInteraction = remember { MutableInteractionSource() }
    val popupInteraction = remember { MutableInteractionSource() }
    val buttonHovered by buttonInteraction.collectIsHoveredAsState()
    val popupHovered by popupInteraction.collectIsHoveredAsState()
    val hovered = buttonHovered || popupHovered
    LaunchedEffect(hovered) {
        if (hovered) {
            help.hover(part)
        } else {
            delay(HOVER_CLOSE_DELAY_MS)
            help.leave(part)
        }
    }
    val focusRequester = remember { FocusRequester() }
    // Where the button is in the window, so the explanation gets at most the room beside it and never covers it
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val open = help.open == part
    val label = stringResource(part.label)
    // While the explanation is open its layer receives the keys, so Escape is handled there
    val escape = { event: KeyEvent ->
        if (event.key == Key.Escape && event.type == KeyEventType.KeyDown && open) {
            help.close()
            true
        } else {
            false
        }
    }
    Box(
        Modifier.size(44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) help.focus(part) else help.blur(part) }
            .onKeyEvent(escape)
            .onGloballyPositioned { anchor = it.boundsInWindow() }
            .hoverable(buttonInteraction)
            .clickable(interactionSource = buttonInteraction, indication = null) {
                focusRequester.requestFocus()
                help.click(part)
            }
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier.size(26.dp)
                .background(if (open) colors.primary else colors.surface, CircleShape)
                .border(1.5.dp, colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "?",
                color = if (open) colors.onPrimary else colors.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        if (open) {
            val density = LocalDensity.current
            val gap = with(density) { 4.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { BelowOrAbove(gap) },
                onDismissRequest = help::dismiss,
                properties = PopupProperties(focusable = false),
                onKeyEvent = escape,
            ) {
                BoxWithConstraints {
                    val room = with(density) { maxOf(constraints.maxHeight - anchor.bottom, anchor.top).toDp() } - 8.dp
                    Surface(
                        modifier = Modifier
                            .widthIn(max = min(360.dp, maxWidth - 16.dp))
                            .heightIn(max = min(maxHeight * 0.7f, room))
                            .hoverable(popupInteraction),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 8.dp,
                    ) {
                        HelpContent(part)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpContent(part: HelpPart) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(part.label), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(part.explanation), style = MaterialTheme.typography.bodyMedium)
        if (part == HelpPart.LEAD_IMAGE) {
            Text(
                stringResource(Res.string.help_image_rights),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
        }
        HorizontalDivider()
        SampleArticle(part)
    }
}

/**
 * Places the explanation below its button, or above it when there is no room below (on the roomier side when neither
 * fits), kept inside the window.
 */
private class BelowOrAbove(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.right - popupContentSize.width
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val fitsBelow = below + popupContentSize.height <= windowSize.height
        val y = when {
            fitsBelow -> below
            above >= 0 -> above
            windowSize.height - anchorBounds.bottom >= anchorBounds.top -> below
            else -> above
        }
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/**
 * The same small article for every explanation, in the reader's order, with [highlight] marked by background,
 * border and a `▶`; the other parts are dimmed. It shows no person, as the image-rights note asks.
 */
@Composable
fun SampleArticle(highlight: HelpPart) {
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SamplePart(HelpPart.SECTION, highlight) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorMarker("green", size = 10.dp)
                Text(stringResource(HelpPart.SECTION.sample), Modifier.padding(start = 6.dp), style = typography.labelMedium)
            }
        }
        SamplePart(HelpPart.KICKER, highlight) { Text(stringResource(HelpPart.KICKER.sample), style = typography.labelMedium) }
        SamplePart(HelpPart.HEADLINE, highlight) { Text(stringResource(HelpPart.HEADLINE.sample), style = typography.titleLarge) }
        SamplePart(HelpPart.SUBHEADLINE, highlight) { Text(stringResource(HelpPart.SUBHEADLINE.sample), style = typography.titleSmall) }
        SamplePart(HelpPart.LEAD_IMAGE, highlight) {
            Column(
                Modifier.fillMaxWidth().height(96.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PictureIcon(32.dp)
                Text(stringResource(HelpPart.LEAD_IMAGE.sample), style = typography.bodySmall)
            }
        }
        SamplePart(HelpPart.CAPTION, highlight) { Text(stringResource(HelpPart.CAPTION.sample), style = typography.bodySmall) }
        SamplePart(HelpPart.LEAD, highlight) {
            Text(stringResource(HelpPart.LEAD.sample), style = typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        SamplePart(HelpPart.PARAGRAPH, highlight) { Text(stringResource(HelpPart.PARAGRAPH.sample), style = typography.bodyMedium) }
        SamplePart(HelpPart.SUBHEAD, highlight) { Text(stringResource(HelpPart.SUBHEAD.sample), style = typography.titleSmall) }
        SamplePart(HelpPart.QUOTE, highlight) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(LocalContentColor.current))
                Text(stringResource(HelpPart.QUOTE.sample), Modifier.padding(start = 8.dp), style = typography.bodyMedium)
            }
        }
        SamplePart(HelpPart.LIST, highlight) {
            Column {
                stringResource(HelpPart.LIST.sample).split(" · ").forEach { item ->
                    Row {
                        Text("•", Modifier.width(16.dp), style = typography.bodyMedium)
                        Text(item, style = typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplePart(part: HelpPart, highlight: HelpPart, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (part == highlight) {
        Row(
            Modifier.fillMaxWidth()
                .background(colors.primaryContainer, RoundedCornerShape(6.dp))
                .border(2.dp, colors.primary, RoundedCornerShape(6.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(Icons.PlayArrow, Modifier.padding(end = 6.dp), size = 14.dp, tint = colors.primary)
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalContentColor provides colors.onPrimaryContainer) { content() }
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) { content() }
        }
    }
}
