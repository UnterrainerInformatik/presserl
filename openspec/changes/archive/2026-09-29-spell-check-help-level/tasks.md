## 1. Backend

- [x] 1.1 Enum `SpellCheckHelp` (`suggestions`, `messages`, `marks`) implementing `SettingValue`, converter in `SettingValueConverter`; `NewspaperConfig.spellCheck().help()` (`presserl.spell-check.help`, default `suggestions`) (design D1).
- [x] 1.2 `EffectiveSettings`: resolve `spell-check.help` with the existing override logic, add it to `settingsMap()` after `media.max-size`.
- [x] 1.3 `NewspaperAction.CONFIGURE_SPELL_CHECK` after `CONFIGURE_NEWSPAPER`; `Newsroom.mayConfigureSpellCheck()` (`PUBLISHER`) and its entry in `allowedActions()` (design D3).
- [x] 1.4 `WritableSettings`: add `spell-check.help` with a per-key permission (`reader.text-size` → `mayConfigureNewspaper`, `spell-check.help` → `mayConfigureSpellCheck`); `NewspaperResource.update` answers `403` (empty body) when a key in a valid body is not writable for the user, before anything is written (design D2).
- [x] 1.5 `SpellCheckService`: read the effective `spell-check.help` per non-blank check and shape the matches (`messages` → no replacements, `marks` → `""` message and no replacements) (design D4).
- [x] 1.6 Tests `NewspaperConfigTest`: default `suggestions`, `PRESSERL_SPELL_CHECK_HELP=messages` resolves, `hints` fails startup naming the variable and the three values; database override wins.
- [x] 1.7 Tests `NewspaperResourceTest`: fresh `GET` contains `spell-check.help: "suggestions"`; publisher sets `marks` and clears it with `null`; `hints` → `400` naming the field; editor-in-chief with `spell-check.help` alone and together with `reader.text-size` → `403`, nothing stored; editor-in-chief still sets `reader.text-size`.
- [x] 1.8 Tests `/api/me`: publisher lists `CONFIGURE_SPELL_CHECK`, editor-in-chief and section roles do not.
- [x] 1.9 Tests `SpellCheckResourceTest`: same stub text on the three levels gives identical offsets/lengths, messages and replacements only where the level allows; level change applies to the next request; blank text still answers without the stub.

## 2. Admin

- [x] 2.1 `NewspaperSettingsModel`: generic per-key choose/save with state for `spell-check.help` (override and effective) next to the text size; a save sends only its own key; refusal restores that key's previous choice and shows the server message.
- [x] 2.2 `NewspaperScreen`: "Spell-check help" section (installation default + three levels with one-line descriptions, effective level), shown only when `spellCheck`; disabled with the publisher-only note without `CONFIGURE_SPELL_CHECK`; `App.kt` passes `spellCheck` and the action.
- [x] 2.3 `SpellSuggestionRow`: no message text when the message is blank.
- [x] 2.4 German and English strings for the section title, the three levels with descriptions, "installation default", the effective-level line and the publisher-only note.
- [x] 2.5 Tests: `NewspaperSettingsModelTest` (load both keys, save `marks` sends only `spell-check.help`, `null` for default, `403` restores the previous level and keeps the text size untouched); `SpellViewsTest` (blank message → only *Ignore*).

## 3. Deploy

- [x] 3.1 `deploy/.env.example`: commented-out `PRESSERL_SPELL_CHECK_HELP` with the three values; `deploy/INSTALL.md`: installation default and that the publisher can override it in the app.

## 4. Contract / Docs

- [x] 4.1 `ai/primer/endpoints.md`: `spell-check.help` in `GET /api/newspaper`, writable key and publisher-only `403` in `PUT /api/newspaper/settings`, `CONFIGURE_SPELL_CHECK` in `GET /api/me`, level-dependent `message`/`replacements` in `POST /api/spell-check`.
- [x] 4.2 `http/newspaper.http`: set/clear `spell-check.help` as publisher, `400` for an invalid value, `403` as editor-in-chief; `http/spell-check.http`: one request per level (after setting it); run them against `quarkus:dev` with the real LanguageTool.

## 5. Verification

- [x] 5.1 Run the backend tests for `newspaper`, `spellcheck` and `/api/me`, and `./gradlew check` in `admin/`.
- [x] 5.2 Playwright against dev: publisher sets "marks only" → editor shows the mark and only *Ignore*; "marks and explanation" → message without suggestion buttons; editor-in-chief sees the choice disabled with the note and sends no request.
