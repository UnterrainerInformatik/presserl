## Context

- Newspaper settings are resolved in `EffectiveSettings.resolve` from `NewspaperConfig` (code default +
  environment, `@ConfigMapping(prefix = "presserl")`) and the JSON overrides of the newspaper row.
  Enumerated settings are enums implementing `SettingValue` with a `SettingValueConverter`, which also
  produces the startup error naming variable and allowed values. `WritableSettings` lists the keys
  `PUT /api/newspaper/settings` accepts (today only `reader.text-size`); `NewspaperResource` admits
  `Newsroom.mayConfigureNewspaper()` (publisher or editor-in-chief).
- The spell check lives in `spellcheck/`: `SpellCheckService.check(text)` calls LanguageTool and maps
  matches to `SpellMatchDto(offset, length, message, replacements)`. Its deployment config
  (`SpellCheckConfig`, prefix `presserl.spell-check`) holds `enabled`, `url`, `language`.
- Admin: `NewspaperSettingsModel`/`NewspaperScreen` show the text-size choice; `SpellSuggestionRow`
  renders message, suggestion buttons and *Ignore*. `Screen.LoggedIn` carries `me` and `spellCheck`.

## Goals / Non-Goals

**Goals:**
- One newspaper-wide level, resolved and overridden exactly like the other settings.
- The server is the only place that decides how much help a writer gets.
- The publisher-only rule is visible to the app through `allowedActions`, not through role names.

**Non-Goals:**
- Per-section or per-writer levels; per-newspaper on/off switch; message rewriting (see proposal).

## Decisions

### 1. Setting key `spell-check.help`, enum `SpellCheckHelp`
Enum `SpellCheckHelp { SUGGESTIONS("suggestions"), MESSAGES("messages"), MARKS("marks") }` in
`newspaper/`, implementing `SettingValue`, with a converter in `SettingValueConverter`. The key sits
next to the other spell-check variables so operators find `PRESSERL_SPELL_CHECK_HELP` beside
`PRESSERL_SPELL_CHECK_LANGUAGE`.

`NewspaperConfig` gains `SpellCheck spellCheck()` (`@WithName("spell-check")`) with only `help()`
(`@WithDefault("suggestions")`, converter). `SpellCheckConfig` keeps `enabled/url/language` under the
same prefix; two mappings sharing a prefix is already the case for `presserl.*` vs `presserl.media.*`
and Quarkus does not reject unmapped sibling keys. Alternative — put `help` into `SpellCheckConfig`:
rejected, because `EffectiveSettings` resolves all layered newspaper settings from `NewspaperConfig`.

`EffectiveSettings` gains `SpellCheckHelp spellCheckHelp`, resolved with the existing `override(...)`,
and `settingsMap()` adds `"spell-check.help"` after `media.max-size`.

### 2. Publisher-only key in `WritableSettings`
`WritableSettings` gets a per-key predicate on the `Newsroom`: `reader.text-size` →
`mayConfigureNewspaper()`, `spell-check.help` → new `Newsroom.mayConfigureSpellCheck()` (holds
`PUBLISHER`). `NewspaperResource.update` keeps its `mayConfigureNewspaper()` gate (`403` for
readers etc.), then parses the body (`400` for unknown keys/values as today), then answers `403`
when any key in the body is not writable for this newsroom — before anything is written, so the
update stays all or nothing. Order `400` before key-level `403` matches the existing validation-
first style; a body that is both invalid and forbidden gets `400`.

Alternative — `400 "is not a writable setting"` for the editor-in-chief: rejected; the key is
writable, the user just lacks the right, and `403` is what the app's refusal handling expects.

### 3. `CONFIGURE_SPELL_CHECK` in `allowedActions`
`NewspaperAction` gets `CONFIGURE_SPELL_CHECK` after `CONFIGURE_NEWSPAPER`;
`Newsroom.allowedActions()` adds it when `mayConfigureSpellCheck()`. The admin app decides
editable vs read-only from this action, following the rule that the server decides and the app
only reflects `allowedActions`. Existing clients ignore unknown actions (`MeDto` doc).

### 4. Enforcement in `SpellCheckService`
`SpellCheckService.check(text)` loads `NewspaperSettings.effective()` (one primary-key read, same as
`GET /api/newspaper`) together with the LanguageTool call and applies the level to the mapped
matches:

```java
static SpellMatchDto shaped(SpellMatchDto m, SpellCheckHelp help) = switch (help) {
    case SUGGESTIONS -> m;
    case MESSAGES -> new SpellMatchDto(m.offset(), m.length(), m.message(), List.of());
    case MARKS -> new SpellMatchDto(m.offset(), m.length(), "", List.of());
};
```

The settings read is not cached, so a change applies to the next check (spec scenario). If reading
the settings fails, the request fails like any other database error (`500`), it does not fall back
to more help. Blank text still answers without contacting LanguageTool or the database.

Response shapes (`POST /api/spell-check`, body `{"text": "Der Hund ist gros."}`):

```json
// suggestions
{ "matches": [ { "offset": 13, "length": 4, "message": "Möglicher Tippfehler gefunden.", "replacements": ["groß", "gros", "Gros"] } ] }
// messages
{ "matches": [ { "offset": 13, "length": 4, "message": "Möglicher Tippfehler gefunden.", "replacements": [] } ] }
// marks
{ "matches": [ { "offset": 13, "length": 4, "message": "", "replacements": [] } ] }
```

`message` stays a string (empty) rather than `null`, so the admin DTO (`message: String`) and any
older client keep deserialising.

### 5. REST contract of the settings endpoints

```json
// GET /api/newspaper (fresh installation)
{ "name": "My Newspaper", "subtitle": "", "visibility": "public",
  "settings": { "retract.author-can-retract": true, "section.default": "General",
                "editor.level": "standard", "reader.text-size": "m", "media.max-size": "10M",
                "spell-check.help": "suggestions" },
  "overrides": {} }

// PUT /api/newspaper/settings  (publisher)
{ "spell-check.help": "marks" }      → 200, settings/overrides contain "spell-check.help": "marks"
{ "spell-check.help": null }         → 200, override removed
{ "spell-check.help": "hints" }      → 400 {"errors":[{"field":"spell-check.help","message":"must be one of suggestions, messages, marks"}]}
// editor-in-chief
{ "spell-check.help": "marks" }      → 403, empty body, nothing stored
{ "reader.text-size": "l", "spell-check.help": "marks" } → 403, nothing stored

// GET /api/me (publisher)
"allowedActions": ["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES",
                   "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER", "CONFIGURE_SPELL_CHECK"]
```

### 6. Admin app
- `NewspaperSettingsModel` generalises the text-size save into `choose(key, value)` with per-key
  state (`override`, `effective`); state gains `spellCheckHelp` / `effectiveSpellCheckHelp`. Saving
  one key never sends the other.
- `NewspaperScreen(api, spellCheck, mayConfigureSpellCheck)`: second section "Spell-check help" with
  the same radio rows (default + three levels, each with a one-line description), rendered only when
  `spellCheck`; rows disabled plus a note when `CONFIGURE_SPELL_CHECK` is missing. `App.kt` passes
  both from `Screen.LoggedIn`.
- `SpellSuggestionRow`: renders the message `Text` only when `message.isNotBlank()`.
- Texts (de/en), e.g. de: "Rechtschreibhilfe", "Markieren, erklären und Korrekturen vorschlagen",
  "Markieren und erklären", "Nur markieren", "Nur die Herausgeberin oder der Herausgeber kann das
  ändern."; en: "Spell-check help", "Mark, explain and suggest corrections", "Mark and explain",
  "Mark only", "Only the publisher can change this."

### 7. Deploy and docs
`.env.example`: commented `# PRESSERL_SPELL_CHECK_HELP=suggestions` with the three values;
`INSTALL.md`: one paragraph in the spell-check section (installation default; the publisher can
override it in the app). Endpoints primer: `GET /api/newspaper`, `PUT /api/newspaper/settings`,
`GET /api/me`, `POST /api/spell-check` sections.

## Risks / Trade-offs

- [Messages can reveal the correction on level `messages`, e.g. "Meinten Sie „das“?"] → Documented
  in the level's description ("the explanation may name the correct word"); `marks` exists for
  newspapers that want no hint.
- [One extra database read per spell-check request] → Primary-key read of a single row, next to a
  LanguageTool call of tens of milliseconds; negligible.
- [Open editors keep old findings until the field is checked again] → Accepted (non-goal); the next
  pause in typing re-checks the field.
- [A publisher-only key inside a publisher-or-editor-in-chief endpoint adds a second permission
  layer] → Kept in `WritableSettings` next to the parsers, so the rule for each key is in one place
  and covered by resource tests.
