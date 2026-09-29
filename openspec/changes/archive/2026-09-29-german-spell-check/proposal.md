## Why

The reporters are children, and their articles are full of spelling mistakes — capitalised nouns,
*das/dass*, commas. Today every mistake either goes online or costs a rejection round trip. The
admin app is Compose/Wasm and draws its text on a canvas, so the browser's built-in spell check
never sees the text; Presserl has to bring its own.

## What Changes

- New service **LanguageTool** (open source, German spelling and grammar rules) in the reference
  compose deployment and in both deployment repositories (`../presserl-deployment`,
  `../alexpresse`), reachable only by `presserl` on the compose network — no text leaves the
  installation.
- New endpoint `POST /api/spell-check` for writers: takes a text and returns the findings (position,
  explanation, up to five suggestions). The backend forwards to LanguageTool; when LanguageTool is
  unreachable it answers `503`, and editing is never affected.
- `GET /api/client-config` gains `spellCheck` (on/off), so the admin app knows whether to check.
- New settings `presserl.spell-check.enabled` (default on), `presserl.spell-check.url` (default the
  compose service) and `presserl.spell-check.language` (default `de-DE`; both deployment
  repositories use `de-AT`).
- Admin app: every prose field — kicker, headline, subheadline, lead, caption, every body block
  (paragraph, subhead, quote, list item), the rejection note and the section name — is checked
  shortly after the user stops typing. Findings are marked in the text; with the cursor on a
  finding, the explanation and the suggestions appear below the field. Choosing a suggestion is a
  normal edit (undo, autosave); *Ignore* hides that word's finding for the rest of the session.

## Non-goals

- No blocking: findings never prevent saving, submitting or publishing.
- No checking in the reader, of names (usernames, first names), dates or pass-phrases.
- No style advice (word repetition, sentence length) — only spelling, grammar and punctuation.
- No server-side personal dictionary and no automatic correction while typing.
- No other languages than German and no LanguageTool n-gram data (several GB).

## Capabilities

### New Capabilities
- `spell-check`: the backend's spell-check endpoint, its configuration, its behaviour when the
  checker is off or unreachable, and the `spellCheck` flag in the client configuration.
- `admin-spell-check`: which admin fields are checked, how findings are marked and explained, and
  how suggestions and *Ignore* work.

### Modified Capabilities
- `deployment`: the reference compose deployment gains a fourth service, `languagetool`, without a
  published port; `presserl` does not wait for it.

## Impact

- **Backend**: new `spellcheck` package (resource, LanguageTool client, config), `client-config`
  response, `%dev` LanguageTool via Compose Dev Services, a stub LanguageTool for `@QuarkusTest`.
- **Admin**: spell-check client and state, marking in plain and rich-text fields, suggestion row,
  German and English texts, DTOs and API client.
- **Reader**: not affected.
- **Deploy**: `deploy/compose.yaml`, `deploy/INSTALL.md` (memory needs, switching off);
  `../presserl-deployment` and `../alexpresse` (`deploy/compose.yaml`, `site.env` with `de-AT`).
- **Contract/Docs**: `ai/primer/endpoints.md`, `http/spell-check.http`, `docs/architecture.md`.
- **Operations**: LanguageTool image `erikvl87/languagetool:6.8` (~450 MB), around 500 MB–1 GB RAM
  on the host.
