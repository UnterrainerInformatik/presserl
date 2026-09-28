## Context

See proposal.md — Why. The editable editor (`ui/editor/EditorScreen.kt`) renders the section
chooser, then the header fields from `HEADER_LABELS` (`HeaderField` → label resource), the
lead-image field with its caption before the lead, and one `BlockCard` per body block whose label
comes from `BLOCK_LABELS` (`BlockType` → label resource). The read-only view (`ArticleView`) shows
the article without field labels and is not touched. The app has no icon library; buttons use
text glyphs (undo `↶`, redo `↷`). UI strings live in `composeResources/values/strings.xml`
(German, default) and `values-en/strings.xml`. Specs: `specs/admin-articles/spec.md`.

## Goals / Non-Goals

**Goals:**
- One reusable help composable used for all eleven parts, so behaviour and look are uniform.
- The sample article is data, not layout per part: one model, one renderer, the highlight is a
  parameter.
- Pure, testable mapping from editor parts to help content.

**Non-Goals:**
- No generic help framework for other screens (can be extracted later if needed).
- No real photo asset in the sample; the image is a drawn placeholder.

## Decisions

### 1. `HelpPart` enum as the single key
`HelpPart` = `SECTION, KICKER, HEADLINE, SUBHEADLINE, LEAD_IMAGE, CAPTION, LEAD, PARAGRAPH,
SUBHEAD, QUOTE, LIST`, with `HeaderField.helpPart` and `BlockType.helpPart` mappings. Each part
maps to three resources: explanation (`help_<part>`), accessible label (`help_label_<part>`) and
its sample text (`sample_<part>`); `LEAD_IMAGE` additionally has `help_image_rights`. The order of
the enum is the reader order, which the sample renderer uses.
*Alternative:* keying by label resource — rejected, block labels and field labels are separate
maps and the sample needs a stable order.

### 2. Own popup instead of Material3 `TooltipBox`
`FieldHelp(part)` is a small circular `?` button (≥ 44 dp touch target, per design guidelines)
that opens a `Popup` anchored below it. Opening sources: pointer hover (`hoverable` interaction),
keyboard focus (`onFocusChanged`) and click/tap (toggle). A popup opened by hover closes when the
pointer leaves both button and popup; one opened by click or focus stays until click again,
Escape (`onKeyEvent` on the button and popup) or an outside click (`onDismissRequest`), then
focus returns to the button. A single `mutableStateOf<HelpPart?>` held by the editor screen
guarantees at most one open popup. The popup is `focusable = false` so it never steals the
cursor from a text field, and it touches neither the model nor the autosaver.
*Alternative:* `TooltipBox` + `RichTooltip` — rejected: the content (explanation plus a whole
sample article) is larger than a tooltip, and focus-to-open plus hover-vs-pinned behaviour are not
controllable enough across Wasm and later touch targets.

### 3. Sample article renderer
`SampleArticle(highlight: HelpPart)` renders, in reader order, section marker + name, kicker,
headline (title style), subheadline, a grey placeholder box with a camera glyph and the caption
below it, lead (bold, as the reader does), then paragraph, subhead, quote (indented with a bar)
and a three-item bullet list. The highlighted part gets the `primaryContainer` background, a
2 dp `primary` border and a `▶` marker at the start; others are rendered in `onSurfaceVariant`
so the highlight stands out without colour alone. Popup width max 360 dp, max height ~70 % of the
window with vertical scroll, so it works on phones.

### 4. Placement
- Header fields: the `?` sits as `trailingIcon` of the `OutlinedTextField` (keeps the label in
  place, no extra row). Section chooser: the dropdown already uses `trailingIcon`, so the `?`
  goes into a `Row` right of the chooser.
- Lead image: next to the "Titelbild" label text; caption: `trailingIcon` of its field.
- Blocks: next to the block type label in the `BlockCard` header row.

### 5. Texts (draft; final wording in the resources)
Tone per design guidelines: short, concrete, "du". German:

| Part | Explanation | Sample |
|---|---|---|
| Ressort | Das Ressort ist die Abteilung der Zeitung, in die dein Artikel gehört – zum Beispiel Sport, Tiere oder Schule. So finden alle deinen Artikel leichter. | Tiere |
| Dachzeile | Die Dachzeile steht klein über der Schlagzeile. Sie sagt in wenigen Wörtern, wo oder worum es geht – wie ein Schild über der Tür. | Tierheim Linz |
| Schlagzeile | Die Schlagzeile ist die große Überschrift. Sie soll neugierig machen, damit alle deinen Artikel lesen wollen. | Minka hat ein neues Zuhause |
| Unterzeile | Die Unterzeile steht direkt unter der Schlagzeile und verrät ein bisschen mehr. | Warum Katzen am Anfang viel Geduld brauchen |
| Titelbild | Das Titelbild ist das große Foto zu deinem Artikel. Es zeigt auf einen Blick, worum es geht. | (placeholder: Katze auf einem Sofa) |
| Bildunterschrift | Die Bildunterschrift steht unter dem Foto. Sie sagt, was oder wer darauf zu sehen ist. | Minka erkundet ihr neues Sofa. |
| Vorspann | Der Vorspann ist der kurze erste Absatz. Er erzählt in zwei, drei Sätzen das Wichtigste: wer, was, wann, wo. | Drei Monate hat die Katze Minka im Tierheim gewartet. Seit Montag wohnt sie bei Familie Berger. |
| Absatz | Ein Absatz ist ein Stück von deinem Text. Beginnt ein neuer Gedanke, fängst du einen neuen Absatz an. | Am ersten Tag hat sich Minka unter dem Bett versteckt. |
| Zwischentitel | Ein Zwischentitel ist eine kleine Überschrift mitten im Text. Er teilt einen langen Artikel in Abschnitte. | Die ersten Tage |
| Zitat | Ein Zitat sind die genauen Worte, die jemand gesagt hat. Schreib dazu, wer es gesagt hat. | „Sie braucht einfach Zeit“, sagt die Tierpflegerin. |
| Aufzählung | In einer Aufzählung stehen mehrere Dinge untereinander, jedes mit einem Punkt davor – wie auf einem Einkaufszettel. | Futter · Kratzbaum · Katzenklo |

Image rights (`help_image_rights`), shown as a separate highlighted note under the lead-image
explanation: „Wichtig bei Fotos: Frag jede Person, die man auf dem Foto erkennt, ob es in die
Zeitung darf – bei Kindern auch die Eltern. Sagt jemand Nein oder kannst du nicht fragen, nimm ein
anderes Foto oder lass die Gesichter unkenntlich machen (verpixeln) – ein Erwachsener hilft dir
dabei. Nimm nur Fotos, die du selbst gemacht hast oder verwenden darfst.“

Accessible labels: „Was ist das Ressort?“, „Was ist die Dachzeile?“ … English: "What is the
section?", "What is the kicker?" …; English explanations and sample are direct translations
(sample: "Animals", "Linz animal shelter", "Minka has a new home", …, "Food · Scratching post ·
Litter box").

The sample itself shows no person, so it models the rule it explains.

### 6. Browser focus guard (found during verification)
Compose web edits text through a hidden `INPUT` inside its shadow root. Tabbing from a text field
to a button removes that `INPUT` while it holds browser focus; the browser then focuses `BODY`,
and no key reaches Compose any more (not even Tab). `Main.kt` installs a guard after
`ComposeViewport`: on `focusout` inside the app's shadow root it checks on the next task whether
`document.activeElement` is `BODY` and, if so, focuses the canvas with `preventScroll`. Focus
that leaves for another page element or window is left alone. The guard is a small JS function so
a browser test can build the same shadow-root structure and check it.
*Alternative:* upgrading Compose Multiplatform in the hope of an upstream fix — unknown, larger
change; the guard can be removed when upstream behaves.

### 7. Narrow header (found during verification)
`Header` measures its width (`BoxWithConstraints`). From 720 dp it stays one row. Below, it is a
column: first row newspaper name + user line (`weight(1f)`) and "Log out", second row the
navigation entries in a `horizontalScroll` row. The editor's `BottomBar` does the same below
720 dp: its two `FlowRow`s (undo/redo/save state left, actions right) become one wrapping
`FlowRow`, because the right group took its full intrinsic width and squeezed undo/redo into
broken words. No other screen changes.

## Risks / Trade-offs

- [Hover popups can flicker when the pointer moves from button to popup] → keep the popup open
  while either is hovered, close with a short delay (~150 ms).
- [Popup covers the field the child is typing in] → anchor below the button with
  `PopupPositionProvider` that flips above when there is no room; popup is non-focusable, so
  typing continues.
- [Texts drift from what the fields really do, e.g. once `starter`/`profi` levels add fields] →
  the design guidelines section says every new editor field gets a help entry; the `HelpPart`
  mapping test fails when a `HeaderField` or `BlockType` has no part.
- [The image-rights text is a simplification of the law] → worded as a reminder, not legal
  advice (proposal Non-goals); the newspaper's adults stay responsible via the approval chain.
- [Missing English string silently falls back to German] → verification step diffs the
  `help_*`/`sample_*` keys of both resource files.

## Migration Plan

Admin-only UI addition, shipped with the next admin bundle; no data, no contract change.
Rollback = revert the commit.
