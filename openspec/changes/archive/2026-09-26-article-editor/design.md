## Context

- Admin app: Compose Multiplatform 1.12 / Kotlin 2.4, Wasm target only, one screen
  (`ui/App.kt`) with a `Screen` sealed interface; `ApiClient` already covers every article
  endpoint; `ArticleDto.body` is a raw `JsonObject`.
- Body format v1 (spec `articles`): blocks `paragraph{content}`, `subhead{text}`,
  `quote{content}`, `list{items}`; run `{text, bold?}` with non-empty text; only line feed allowed
  in runs; server rejects unknown fields.
- `docs/design-guidelines.md` §3: level `standard`, autosave, undo always visible, one primary
  button bottom right, friendly messages, i18n resources, touch targets ≥ 44 dp.
- CSP of `/admin/` allows no inline script and only hashed framework styles; Compose renders to
  canvas, so libraries that draw with Compose do not touch the CSP.
- No REST changes.

## Goals / Non-Goals

**Goals:** usable editor for the solo publisher (M1), lossless round trip of every v1 body,
autosave that never silently overwrites someone else's save, testable logic outside composables.

**Non-Goals:** levels `starter`/`profi`, images, approval, draft preview, admin deep links,
spell check.

## Decisions

### D1 Block-based editor, rich text only inside a block
One card per block with a type-specific field. Paragraph, quote and each list item use a
`RichTextState` from `com.mohamedrejeb.richeditor:richeditor-compose` (supports `wasmJs`); the
toolbar offers only **Bold**. Subhead and header fields use plain `BasicTextField`/
`OutlinedTextField` with `singleLine = true` and `maxLength` filtering.
*Why:* the block list maps 1:1 to format v1 (no parsing of a free document into
quote/subhead/list), and it is the documented fallback that also fits `starter` later.
*Alternative:* one continuous rich-text document mapped to blocks — rejected, block boundaries
(quote vs paragraph, list items) would have to be inferred.
*Alternative:* pure Compose without library — rejected by decision: bold inside a paragraph
needs span editing that `BasicTextField` does not provide.
*Verification at apply time:* pin the newest library version that resolves against Compose
1.12.x / Kotlin 2.4.x; if none does, stop and report (no silent downgrade of Compose).

### D2 Typed body model with explicit mapping
`article/Body.kt`: `@Serializable` sealed `Block` (`Paragraph(content)`, `Subhead(text)`,
`Quote(content)`, `BulletList(items)`) with `@SerialName` type discriminator `type`, `Run(text,
bold = false)` encoded without default `bold` (`encodeDefaults = false` for this model), and
`Body(version = 1, blocks)`. `ArticleDto.body`/`ArticleContent.body` stay `JsonObject` on the
wire; `Body.fromJson`/`toJson` convert.
Runs ↔ editor: `runsToAnnotated(runs)` builds an `AnnotatedString` with `FontWeight.Bold`
spans; `annotatedToRuns(annotated)` splits at bold boundaries, merges adjacent runs with the same
mark, drops empty runs, and turns `\r\n`/`\r` into `\n`. Both are pure functions and tested,
including the round trip of `ArticleExamples.ARTICLE`.

### D2a Empty blocks
`ArticleBodyValidator` accepts empty blocks: a paragraph/quote with `content: []`, list items
`[]` (a list needs at least one item) and a subhead `""`; only runs must have non-empty text.
The editor therefore saves empty blocks as they are, and a new list block starts with one empty
item. Removing the last item of a list removes the list block.

### D3 Editor state and view model outside composables
`ui/editor/EditorModel` holds an immutable `Draft(kicker, headline, subheadline, lead,
blocks: List<EditorBlock>)` where each `EditorBlock` has a stable local id; composables render
it and send intents (`EditField`, `AddBlock(after, type)`, `MoveBlock`, `RemoveBlock`,
`ToggleBold`, `Undo`, `Redo`). `RichTextState` instances are kept per block id in the UI layer
and synchronised through D2's mapping on change.

### D4 Undo/redo as snapshot history
The model keeps a bounded stack (100) of `Draft` snapshots. Typing is coalesced: consecutive
text edits in the same field within 1 s form one step; structural changes (add/move/remove
block, bold toggle) are always their own step. Undo/redo replace the draft and mark it dirty, so
autosave picks them up. No per-field native undo is relied upon.

### D5 Autosave controller
`ui/editor/Autosaver` (coroutine-based, clock injectable for tests):
- Debounce 1.5 s after the last change; at most one request in flight; changes during a request
  trigger another save afterwards with the newly received `version`.
- `flush()` saves immediately and suspends until saved (used before Publish and on leaving).
- States: `Saved`, `Pending`, `Saving`, `Failed(retryInSeconds)`, `Invalid(errors)`,
  `Conflict`.
- `400` → `Invalid`: map `FieldErrorDto.field` to header fields or block indices
  (`body.blocks[i]…`) for inline messages; retry after the next change only.
- `409` → `Conflict`: stop saving; UI offers "Load current version" (reload, discard local
  changes). Nothing else is written.
- Network error / `5xx` → `Failed`, retry with backoff 2, 4, 8 … max 30 s, content kept.
- `401` → treated like the rest of the app (token refresh by `AuthClient`; if that fails, the
  shell's error screen).

### D6 Navigation
In-memory screen stack in `App`: `ArticleList(tab)`, `Editor(id)`, `Revisions(id)`,
`Revision(id, n)`. Back buttons in the UI; leaving the editor calls `flush()`. The browser URL
stays `/admin/` (deep links are a non-goal).

### D7 Actions and read-only mode
Buttons are derived by a pure function `actionsFor(allowedActions)`; `EDIT` absent ⇒ all
fields disabled and autosave off. Publish: `flush()` then `POST …/publish`; a `400` naming
`headline` is shown at the field. Delete: Compose `AlertDialog` (never a browser `confirm()`).
The response `ArticleDto` replaces the local article (version, status, `allowedActions`).

### D8 Reader link
"View in reader" is shown when `status == PUBLISHED` and opens
`"/articles/{id}"` of the current origin in a new tab via Compose's `LocalUriHandler`. The route is delivered by
`reader-articles`; until then the link answers `404` — accepted, the change order is
`article-editor` then `reader-articles`, both in M1.

### D9 i18n with Compose Resources
Add `org.jetbrains.compose.components:components-resources`; strings in
`composeResources/values/strings.xml` (German, default) and `values-en/strings.xml`. Compose
picks the locale from the browser. A common test asserts both files define the same keys.
Status and role labels (`roleLabel`, new `statusLabel`) become string resources.

### D10 Layout
Single column, max width ~ 900 dp, header fields in the hierarchy order kicker → headline →
subheadline → lead (German labels Dachzeile, Schlagzeile, Unterzeile, Vorspann; block
Zwischentitel, Zitat, Aufzählung). Bottom bar: save state and undo/redo left, Publish right.
Touch targets ≥ 44 dp.

## Risks / Trade-offs

- [compose-rich-editor lags behind Compose releases] → version check at apply (D1); the library
  is used only behind the D2 mapping, so replacing it touches one composable.
- [Wasm text input quirks (IME, paste with formatting)] → paste is reduced to plain text + bold
  by `annotatedToRuns`; manual browser test in Firefox and Chrome is part of verification.
- [Bundle size grows] → acceptable; `.wasm` is cached long-term after `admin-cache-headers`.
- [Conflict loses local edits on reload] → acceptable in M1 (single author per article); the
  message says so before reloading.
- [Reader link 404 until `reader-articles`] → documented, short-lived.
