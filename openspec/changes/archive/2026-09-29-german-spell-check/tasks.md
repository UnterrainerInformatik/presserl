## 1. Backend

- [x] 1.1 Add `quarkus-rest-client-jackson`; add `presserl.spell-check.enabled` (default `true`), `.url` (default `http://languagetool:8010`) and `.language` (default `de-DE`) as a `@ConfigMapping` in a new `spellcheck` package, `%dev` URL built from `presserl-dev.languagetool.port` (design D3).
- [x] 1.2 Add `languagetool` (`erikvl87/languagetool:6.8`, `Java_Xmx=768m`, healthcheck on `/v2/languages`, port label `presserl-dev.languagetool.port`) to `backend/compose-devservices.yml` under the compose profile `spell-check`, activated for `%dev` only; confirm `@QuarkusTest` runs do not start it and `quarkus:dev` does (fallback per design D4 if profiles do not work, recorded in `ai/memory/reference_build_and_test.md`).
- [x] 1.3 LanguageTool REST client for `POST /v2/check` (form-encoded `text`, `language`, `level=default`), 5 s connect and read timeout.
- [x] 1.4 `SpellCheckService`: blank text → no matches without a call; filter by issue type and category, map `offset`/`length`/`message`/first five replacements (design D2); disabled or any client failure → `SpellCheckUnavailableException`; log an outage once as a warning until the next success, never the text.
- [x] 1.5 `SpellCheckResource` `POST /api/spell-check`: `WRITE_ARTICLES` only (`403` empty body otherwise), `400` error body for missing/non-string `text` or more than 10,000 code points, `503` error body (field `null`) mapped from the unavailable exception.
- [x] 1.6 Add `"spellCheck"` (= `enabled`) to `GET /api/client-config`.
- [x] 1.7 Test resource: JDK `HttpServer` stub for `/v2/check` with canned LanguageTool JSON (misspelling, casing, a style match, an emoji text), a slow mode and an error mode; wire `presserl.spell-check.url` to it for `@QuarkusTest`.
- [x] 1.8 `@QuarkusTest`s: reporter gets filtered matches with ≤ 5 replacements and German messages; style match dropped; blank text answers without a stub call; `400` for missing, non-string and 10,001-code-point text (stub not called); reader `403`, no token `401`; stub slow (> 5 s) and stub error → `503`; `enabled=false` → `503` without a stub call; `/q/health/ready` `UP` while the stub is down; client-config `spellCheck` true/false.

## 2. Admin

- [x] 2.1 DTOs (`SpellCheckRequestDto`, `SpellCheckResponseDto`, `SpellMatchDto`, `spellCheck` in `ClientConfigDto` defaulting to `false` when missing) and `ApiClient.spellCheck(text)` distinguishing success from unavailable (`503`, network error, timeout).
- [x] 2.2 `SpellChecker` (commonMain, design D5): per-field keys, 1 s debounce, stale answers discarded, LRU cache, at most two requests in flight, ignored words per instance, availability state with 60 s retry per field and a one-time notice flag. Kotlin tests with a fake API and virtual time: debounce, stale discard, cache hit on undo, ignore across fields, retry spacing, no requests when `spellCheck` is false.
- [x] 2.3 Spell-check `VisualTransformation` for plain fields (red, underlined, identity offset mapping) and Kotlin tests that the transformed text equals the input and carries spans exactly on the finding ranges.
- [x] 2.4 Suggestion row composable (message, one button per replacement, *Ignore*, accessible labels, keyboard reachable) shown while the collapsed caret of the focused field lies inside a visible finding.
- [x] 2.5 German and English strings: notice ("Rechtschreibprüfung gerade nicht verfügbar" / "Spell check is currently unavailable"), "Ignorieren" / "Ignore", accessible labels for suggestion buttons.

## 3. Admin — plain fields

- [x] 3.1 Wire kicker, headline, subheadline, lead, caption and subhead fields in `EditorScreen.kt` to the editor's `SpellChecker`: transformation, suggestion row, check on open, applying a suggestion through the field's existing edit intent (one undo step, autosaved, caret after the replacement).
- [x] 3.2 Wire the rejection note (editor) and the section name (`SectionScreens.kt`) with their own `SpellChecker` instance per dialog/form; confirm account, issue-date and newspaper forms stay unchecked.
- [x] 3.3 Editor model tests: applying a suggestion produces one undo step whose undo restores the old word; marking alone creates no undo step and no save.

## 4. Admin — rich-text blocks

- [x] 4.1 Spike (design D6): mark finding ranges in a `RichTextState` via span styles and check that the editor sends no save, records no undo step, keeps bold runs and keeps the selection; if any fails, implement the `drawWithContent` overlay fallback instead. Record the outcome in design.md.
- [x] 4.2 Wire paragraph, quote and list-item editors: marks, check on open, suggestion row, replacing a range while keeping its bold state, as an undoable, autosaved edit.
- [x] 4.3 Tests: marked bold word stays bold; `runs()` identical with and without marks; replacing inside a bold run keeps it bold; undo restores the original word.

## 5. Deploy

- [x] 5.1 `deploy/compose.yaml`: service `languagetool` (`erikvl87/languagetool:6.8`, `restart: unless-stopped`, `Java_Xms=256m`, `Java_Xmx=768m`, healthcheck, no published port), no `depends_on` from `presserl`; header comment updated.
- [x] 5.2 `deploy/INSTALL.md`: what the spell checker is, its memory need, `PRESSERL_SPELL_CHECK_LANGUAGE` (e.g. `de-AT`), switching it off with `PRESSERL_SPELL_CHECK_ENABLED=false` and removing the service; `.env.example` mentions both only as commented-out optional examples.
- [x] 5.3 `../presserl-deployment`: add the `languagetool` service to `deploy/compose.yaml`, `PRESSERL_SPELL_CHECK_LANGUAGE=de-AT` to `deploy/site.env`; commit; push only after Gerald's go.
- [ ] 5.4 `../alexpresse`: merge `upstream/master` after 5.3 is pushed, check `site.env` carries `de-AT`; commit; push only after Gerald's go.

## 6. Contract / Docs

- [x] 6.1 `ai/primer/endpoints.md`: new `POST /api/spell-check` section with request, response, errors and side effects; `spellCheck` in `GET /api/client-config`.
- [x] 6.2 `http/spell-check.http`: misspelling, correct text, blank text, too long (`400`), reader (`403`); `spellCheck` assertion in the client-config request file; run them against `quarkus:dev` with the real LanguageTool.
- [x] 6.3 Against the real LanguageTool in dev: confirm offsets of a text starting with an emoji are UTF-16 code units, `gros` → `groß`, `hund` → `Hund`, and that `de-AT` accepts `Jänner`; adjust the mapping if not.
- [x] 6.4 `docs/architecture.md`: the spell checker in the component overview and deployment diagram (PlantUML + rendered SVG).

## 7. Verification

- [x] 7.1 Run backend tests for the `spellcheck` package and `client-config`, and `./gradlew check` in `admin/`.
- [x] 7.2 Playwright against dev (backend + LanguageTool + admin bundle): headline mark and suggestion accepted and saved; bold paragraph marked without a save request; *Ignore* on a name clears both occurrences; read-only article and new-account form send no spell-check request; stopping the `languagetool` container shows the notice once while autosave continues, and marking resumes after it is back.
- [x] 7.3 Build the image, run `deploy/compose.yaml` locally with LanguageTool: `languagetool` has no published port, `presserl` becomes healthy before `languagetool`, spell check answers once it is up; tear everything down again.
- [x] 7.5 Make `FocusGuardTest.focusFallingToTheBodyGoesBackToTheCanvas` deterministic: with this change's tests in the bundle it failed in 3 of 4 `./gradlew check` runs (green on HEAD). Cause (measured): the headless test window then has no system focus (`document.hasFocus()` false, also in Karma's parent page), and Chrome sends no `focusout` for the removed input. The test dispatches that event itself when the document lacks focus and polls for the canvas focus up to a deadline instead of looking once after 20 ms.
- [ ] 7.4 After the staging rollout: check a spell-check request on `presserl.unterrainer.info` and the container's memory use; record both in this change.
