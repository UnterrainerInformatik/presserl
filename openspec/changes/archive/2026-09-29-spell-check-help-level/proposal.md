## Why

The spell check (german-spell-check) always offers ready-made corrections. For children learning to
write, a newspaper may want them to find the correct spelling themselves: see that something is
wrong, maybe read why, but not get the answer handed to them. How much help the writers get is a
pedagogical decision of the newspaper, so the publisher should choose it.

## What Changes

- New newspaper setting `spell-check.help` with three levels:
  - `suggestions` (code default, today's behavior): mark, message and replacement buttons.
  - `messages`: mark and message, no replacements.
  - `marks`: mark only, no message and no replacements; the row below the field offers *Ignore* only.
- Layered like `reader.text-size`: code default → `PRESSERL_SPELL_CHECK_HELP` (an invalid value stops
  the startup) → newspaper override via `PUT /api/newspaper/settings`. `GET /api/newspaper` shows the
  effective level and whether it is overridden.
- Only the publisher may change it: `PUT /api/newspaper/settings` with `spell-check.help` from an
  editor-in-chief answers `403` and stores nothing (all or nothing, also for other keys in the same
  body). `GET /api/me` announces the right as the new action `CONFIGURE_SPELL_CHECK`.
- `POST /api/spell-check` enforces the level on the server: `messages` answers every finding with
  empty `replacements`, `marks` additionally with an empty `message`, so a direct API call gets no
  more help than the app shows.
- Admin "Newspaper" screen: a new "Spell-check help" choice ("installation default" and the three
  levels) that publishers change and editors-in-chief see read-only. German and English texts.
- Admin editor: a finding without message shows only *Ignore* in its row.
- Endpoints primer, `.http` files, `INSTALL.md` and `.env.example` (optional, commented out).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `newspaper-settings`: the resolved settings, the startup check for enumerated values and the
  writable keys gain `spell-check.help`; writing it is limited to publishers.
- `spell-check`: the endpoint strips replacements and messages according to the effective level.
- `admin-newspaper`: the settings screen gains the spell-check help choice, editable for publishers
  only.
- `admin-spell-check`: the explanation row copes with findings without message.
- `api-authentication`: `allowedActions` gains `CONFIGURE_SPELL_CHECK` (publisher only).

## Non-goals

- A level per section, per writer or per age group; the setting is newspaper-wide.
- Switching the spell check off per newspaper (stays deployment-only via
  `PRESSERL_SPELL_CHECK_ENABLED`).
- Changing which findings are reported, or rewording LanguageTool's messages so they never reveal
  the correction (with `messages`, a message may still name it; `marks` is the level for that).
- Re-checking open editors immediately when the level changes; the new level applies from the next
  check of a field.

## Impact

- **backend:** newspaper settings (config mapping, effective settings, writable settings, publisher-
  only key), `Newsroom`/`NewspaperAction` (`CONFIGURE_SPELL_CHECK`), spell-check service reads the
  effective level. Tests: JUnit 5 + AssertJ, `@QuarkusTest`.
- **admin:** newspaper settings model and screen, spell suggestion row, texts (de/en). Kotlin tests.
- **REST contract:** `GET /api/newspaper` (new key), `PUT /api/newspaper/settings` (new writable key,
  new `403` case), `GET /api/me` (new action), `POST /api/spell-check` (level-dependent content).
  `ai/primer/endpoints.md`, `http/newspaper.http`, `http/spell-check.http`.
- **deploy:** `INSTALL.md`, `.env.example` mention `PRESSERL_SPELL_CHECK_HELP`. No new service.
- **reader:** unaffected.
