## Context

See proposal.md — Why. Constraints that shape the approach:

- The admin app renders on a canvas (Compose/Wasm); there is no DOM input, so the browser's spell
  check cannot help. Android/iOS targets will follow and must get the same behaviour.
- Plain fields are Material 3 `OutlinedTextField`s with a `String` value (kicker, headline,
  subheadline, lead, caption, subhead, rejection note, section name). Paragraph, quote and list
  items use `OutlinedRichTextEditor` from `com.mohamedrejeb.richeditor` 1.2.0 with a
  `RichTextState`; `RichTextStates.kt` converts the state to runs using only the bold mark, so any
  other span style is invisible to saving.
- The reference deployment already runs a helper container that only `presserl` reaches (`rustfs`),
  with the backend defaulting to its compose service name; dev and tests start it through
  `backend/compose-devservices.yml`.
- Writers are recognised by `WRITE_ARTICLES` (`Newsroom#isWriter`).

## Goals / Non-Goals

**Goals:**
- Server-side checking usable unchanged by later mobile clients.
- Zero influence on editing, saving and approval when the checker is slow, down or switched off.
- Texts stay inside the installation.

**Non-Goals:**
- Tuning LanguageTool rules per newspaper, user dictionaries, n-gram data (see proposal Non-goals).

## Decisions

### D1 — LanguageTool as its own container (`erikvl87/languagetool:6.8`)
LanguageTool catches casing, *das/dass* and comma mistakes that word-list checkers (Hunspell)
miss. It runs as a separate compose service rather than as a library inside the backend.

- *Alternative: embed `org.languagetool:language-de` in the backend.* No extra service, but it
  adds ~100 MB to the image and several hundred MB of heap to the backend JVM, slows start-up, and
  a checker problem would take the whole newspaper down. The separate container keeps memory and
  failure isolated and can be switched off without rebuilding.
- *Image:* LanguageTool publishes no official image; `erikvl87/languagetool` is the widely used
  one (~6 M pulls, tracks LanguageTool releases). Pinned to `6.8`, heap limited with
  `Java_Xms=256m`, `Java_Xmx=768m`. It opens no outbound connections.
- No `depends_on` from `presserl`: LanguageTool needs ~20 s to start and is optional.
- A healthcheck on `GET /v2/languages` gives `docker compose ps` a meaningful status.

### D2 — Backend proxies, the admin app never talks to LanguageTool
`POST /api/spell-check` keeps authentication, the size limit, language choice and result filtering
in one place, and LanguageTool publishes no port.

Request / response:

```http
POST /api/spell-check
Authorization: Bearer <token>
Content-Type: application/json

{ "text": "Der Hund ist gros." }
```

```json
{
  "matches": [
    { "offset": 13, "length": 4,
      "message": "Möglicher Tippfehler gefunden.",
      "replacements": ["groß", "Gros", "grob"] }
  ]
}
```

- `400` `{"errors": [{"field": "text", "message": "text must be a string of at most 10000 code points"}]}`
- `401` empty, `403` empty (no `WRITE_ARTICLES`)
- `503` `{"errors": [{"field": null, "message": "the spell check is currently unavailable"}]}`

`GET /api/client-config` gains a top-level `"spellCheck": true|false`.

The backend calls LanguageTool's `POST /v2/check` (form-encoded `text`, `language`,
`level=default`) with a Quarkus REST client (`quarkus-rest-client-jackson`), connect and read
timeout 5 s. Mapping:
- Keep a match when `rule.issueType` is `misspelling`, `grammar`, `typographical` or
  `uncategorized`, or `rule.category.id` is one of `TYPOS`, `CASING`, `GRAMMAR`, `PUNCTUATION`,
  `CONFUSED_WORDS`, `COMPOUNDING`; drop everything else (`style`, `REDUNDANCY`, `COLLOQUIALISMS`, …)
  so a child is not flooded with advice.
- `message` from LanguageTool's `message`; `replacements` = first five `replacements[].value`.
- LanguageTool offsets are Java `char` indices, i.e. UTF-16 code units — the same unit as Kotlin
  strings. Verified against the real container with an emoji (task 6.3); if LanguageTool ever reports code points,
  the mapping converts them.

The text is never logged; an outage is logged once as a warning until the next success.

### D3 — Configuration
`presserl.spell-check.enabled` / `.url` / `.language` in `application.properties` with the
defaults from the spec, `%dev` pointing at the Compose Dev Services port. `de-DE` is the generic
default; both deployment repositories set `PRESSERL_SPELL_CHECK_LANGUAGE=de-AT` in `site.env`
because Austrian children write *Jänner* and *heuer*. These are deployment settings, not newspaper
settings — the text language does not change at runtime.

### D4 — Dev and tests
- **Dev:** `languagetool` joins `backend/compose-devservices.yml` under the compose profile
  `spell-check`, activated for `%dev` only (`quarkus.compose.devservices.profiles`), with its mapped
  port in `presserl-dev.languagetool.port`.
- **Tests:** starting a 450 MB, 20 s-start container for every `@QuarkusTest` run is too costly.
  A `QuarkusTestResourceLifecycleManager` starts a JDK `HttpServer` stub that answers `/v2/check`
  with canned LanguageTool JSON (including a style match to prove filtering, and a slow mode to
  prove the timeout). One manual verification against the real container covers the real rules
  and the offset unit.
- If compose profiles in Dev Services do not work as described, fall back to a separate
  `backend/compose-languagetool.yml` started by hand, documented in memory.

### D5 — Admin: one `SpellChecker` per editor or form
A `SpellChecker` (commonMain) owns, per field key (`headline`, `block:<id>`, `item:<id>`,
`caption`, `rejectNote`, `sectionName`):
- the last text sent and its findings; a debounce of 1 s after the last change (same pattern as
  `Autosaver`); on answer, findings are kept only if the field's current text equals the text sent;
- a small LRU cache text → findings, so undo/redo and reopening do not re-request;
- the session's ignored words (the finding's text, case-sensitive); findings on ignored words are
  filtered out when displayed;
- the availability state: after a failure, a field is retried on the next change once 60 s have
  passed; the notice is shown once per checker instance.

On open, the editor enqueues all fields; requests run at most two at a time so a long article does
not fire dozens of parallel calls.

### D6 — Marking without touching content
- **Plain fields:** a `VisualTransformation` that returns the same text with
  `SpanStyle(color = error, textDecoration = Underline)` on each finding range and
  `OffsetMapping.Identity`. It changes rendering only; `value` and `onValueChange` are untouched.
- **Rich-text blocks:** the library has no decoration layer. Approach: after each check, apply the
  mark as a span style on the finding ranges through the `RichTextState` API and remove it before
  the next check; saving is unaffected because `runs()` reads only bold marks. Risks: the library
  may record these calls in its own history, change selection, or fire the change listener that
  drives autosave. The editor's change detection compares runs, not the state object, which
  should absorb that — verified by a spike (task 4.1) with tests for "no save", "no undo step",
  "bold kept".
  *Fallback if the spike fails:* draw the underline ourselves with `drawWithContent` over the
  editor using its `TextLayoutResult` (from the editor's `onTextLayout` or the state, whichever 1.2.0 exposes), which touches no state
  at all.

The undo/redo of the editor is our own (`EditorModel`), not the library's, so marks never enter
it as long as they do not produce an `EditorIntent`.

**Spike outcome (task 4.1): span-style marks rejected, overlay implemented.** In compose-rich-editor
1.2.0 `addSpanStyle(style, range)` and `removeSpanStyle(style, range)` run inside `recordHistory`,
so every mark becomes an entry of the library's own history, which the editor's Ctrl/Cmd+Z uses
(`onPreviewKeyEvent`); the switch that suppresses recording is private. Ctrl+Z after a check would
undo the mark instead of the typed word. The fallback is used instead: `spellMarksOverlay` draws
over `OutlinedRichTextEditor` from the layout its `onTextLayout` reports, at the text origin
(content padding, plus the 8 dp the outlined editor adds for a label). It recolours the glyphs of
each finding with the error colour (`BlendMode.SrcAtop` in an offscreen layer, so only pixels the
editor drew change) and draws an underline; the `RichTextState` is never touched. The library
separates paragraphs with a space where the runs have `\n`; lengths and offsets are equal, so the
overlay compares the texts with that one difference allowed (`showsText`). A chosen suggestion is
applied to the runs (`replaceInRuns`, the new text takes the mark of the first replaced character),
loaded into the state with the caret after it and dispatched as `EditRuns(ownStep = true)`.

### D7 — Suggestion row instead of a popup
The row below the focused field (message, suggestion buttons, *Ignore*) appears when the caret
(collapsed selection) lies within a finding. A row instead of a popup: it works identically for
touch, mouse and keyboard, needs no hit-testing of the canvas text, and avoids the Compose `Popup`
focus and semantics problems already seen in the field-help work. Applying a suggestion dispatches
the existing edit intent for that field (plain) or replaces the range in the `RichTextState`
keeping the span's bold state (rich), so undo and autosave work as for typing.

## Risks / Trade-offs

- [Rich-text marking pollutes library state] → spike first (task 4.1) with the overlay fallback in
  D6.
- [LanguageTool memory on small hosts] → heap capped at 768 MB; `INSTALL.md` documents
  `PRESSERL_SPELL_CHECK_ENABLED=false` and removing the service for hosts under 2 GB RAM.
- [Third-party image] → pinned tag, no published port, no outbound need; review the tag on
  upgrades like the other images.
- [False positives on names and children's words] → *Ignore* per session; findings never block.
- [Many requests while typing] → 1 s debounce, only changed fields, cache, two parallel requests.
- [Offsets drift when the user types during a request] → answers for stale text are discarded.

## Migration Plan

1. Merge; CI builds the image; staging redeploys automatically — but `../presserl-deployment`'s
   compose must gain the `languagetool` service in the same rollout, otherwise the feature just
   answers `503` (harmless).
2. Update `../presserl-deployment` (compose + `site.env`), push after Gerald's go; then merge
   upstream into `../alexpresse` and push after Gerald's go.
3. Rollback: set `PRESSERL_SPELL_CHECK_ENABLED=false` or drop the service; no data involved.
