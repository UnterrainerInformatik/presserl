## 1. Admin — dependencies and i18n

- [ ] 1.1 Add `components-resources` and `compose-rich-editor` to `gradle/libs.versions.toml` and `composeApp/build.gradle.kts`; pin the newest `richeditor-compose` version that resolves against Compose 1.12.x / Kotlin 2.4.x for `wasmJs` (D1) — stop and report if none does
- [ ] 1.2 Create `composeResources/values/strings.xml` (German) and `values-en/strings.xml` (English) for all existing and new UI texts; move login/logout/error texts and `roleLabel` to resources, add `statusLabel` (D9)
- [ ] 1.3 Test: both string files define the same keys; `roleLabel`/`statusLabel` cover every known value
- [ ] 1.4 Rebuild and confirm `CspStyleHashTest` still passes (update `csp-style-hashes.txt` only via the documented procedure if Compose's injected style changed)

## 2. Admin — body model and mapping

- [ ] 2.1 `article/Body.kt`: `@Serializable` `Body`, sealed `Block` (`Paragraph`, `Subhead`, `Quote`, `BulletList`), `Run`; `fromJson(JsonObject)`/`toJson()` without emitting `bold: false` (D2)
- [ ] 2.2 Pure mapping `runsToAnnotated` / `annotatedToRuns` (bold spans, merge adjacent runs, drop empty runs, normalise `\r\n`/`\r` to `\n`) (D2)
- [ ] 2.3 Tests: round trip of `ArticleExamples.ARTICLE` body unchanged; mapping edge cases (bold at start/end, adjacent bold runs, empty paragraph, list with empty item, pasted CRLF); empty blocks serialise as specified in D2a

## 3. Admin — editor model, undo, autosave

- [ ] 3.1 `ui/editor/EditorModel`: `Draft` with stable block ids, intents (edit field, add/move/remove block, toggle bold, undo, redo), length/single-line limits for header fields (D3)
- [ ] 3.2 Undo/redo snapshot history, bounded to 100, typing coalesced per field within 1 s, structural changes as own steps (D4)
- [ ] 3.3 `ui/editor/Autosaver` with injectable clock: debounce 1.5 s, one request in flight, `flush()`, states `Saved/Pending/Saving/Failed/Invalid/Conflict`, backoff 2→30 s, `400` field mapping to header fields and block indices, `409` stops saving (D5)
- [ ] 3.4 Tests with Ktor `MockEngine` and `kotlinx-coroutines-test` virtual time: single save after a burst of edits, version carried to the next save, edits during an in-flight save, `flush()` before publish, `400` mapping, `409` stops further PUTs, network error retried and succeeds
- [ ] 3.5 Tests for undo/redo: remove block + undo restores position and bold runs; coalescing; redo cleared by a new edit
- [ ] 3.6 Pure `actionsFor(allowedActions)` (publish/offline/delete/editable) with tests (D7)

## 4. Admin — screens

- [ ] 4.1 Navigation stack in `App` (`ArticleList(tab)`, `Editor(id)`, `Revisions(id)`, `Revision(id, n)`); header with newspaper name, display name, roles and logout (D6, admin-shell)
- [ ] 4.2 Article list screen: tabs "My articles"/"All articles", entries with headline or placeholder, status, author, last change; "New article" creates and opens the editor
- [ ] 4.3 Editor screen: header fields (kicker, headline, subheadline, lead), block cards with add-after/move/remove, rich-text fields with a Bold toggle for paragraph/quote/list items, plain subhead field, inline field errors, read-only mode without `EDIT` (D1, D3, D10)
- [ ] 4.4 Bottom bar: save state, undo/redo, Take offline, Delete (Compose `AlertDialog`), primary Publish bottom right; publish flushes first and shows a headline error at the field; conflict banner with "Load current version" (D5, D7)
- [ ] 4.5 Revision history screen and read-only revision view (live marker)
- [ ] 4.6 "View in reader" for `PUBLISHED` articles opening `/articles/{id}` in a new tab via `LocalUriHandler` (D8)
- [ ] 4.7 Touch targets ≥ 44 dp, max content width, German labels per `docs/design-guidelines.md` §1

## 5. Docs

- [ ] 5.1 `docs/design-guidelines.md` §3: record the chosen technique (block-based editor with `compose-rich-editor` for bold inside blocks) and that only `standard` is implemented so far
- [x] 5.2 `ai/open-proposals.md`: remove the `article-editor` entry (done at proposal time)

## 6. Verification

- [ ] 6.1 `./gradlew --console=plain check` in `admin/` (incl. Karma tests) and `./mvnw verify` in `backend/` pass locally
- [ ] 6.2 Manual run against `quarkus dev` + admin dev server in Firefox and Chrome: create, write every block type with bold, reload and compare, undo/redo, publish, take offline, delete draft, second-tab conflict, network off/on, German and English browser language; no CSP violation from the app in the console
