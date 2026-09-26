## Why

The backend can store, revise, publish and take articles offline since `articles-core`, but the
admin app only shows who is logged in. Without an editor nobody can write the newspaper; this
change makes the admin app usable for milestone M1 ("solo newspaper") at editor level
`standard`.

## What Changes

- **Article lists** in the admin app: "My articles" (default) and "All articles", with status,
  headline, author and last change; create a new article from the list.
- **Article editor** at level `standard`: kicker, headline, subheadline, lead and a
  **block-based body** (one card per block: paragraph, subhead, quote, bullet list; add, move,
  remove blocks). Bold inside paragraph, quote and list items via the Compose rich-text
  library `compose-rich-editor` (Wasm-capable); only the mark `bold` is offered.
- **Typed body model** in the admin app for body format v1, with mapping between blocks/runs and
  the editor state; the app never produces anything the server's allowlist rejects in normal use.
- **Autosave** with the article `version` (debounced, flushed before publish and when leaving the
  editor), visible save state, friendly handling of `400` (field messages), `409` (changed
  elsewhere → reload) and network errors (retry).
- **Undo/redo** buttons, always visible in the editor.
- **Actions from `allowedActions`**: Publish (primary button, bottom right), Take offline,
  Delete (with in-app confirmation dialog); buttons not listed are not shown.
- **Revision history**: list of revisions and read-only view of one revision.
- **"View in reader"** link opening the reader article page `/articles/{id}` in a new tab for
  published articles.
- **i18n**: all admin UI texts in Compose Resources, German (default) and English, chosen from
  the browser language; existing login/logout texts move there too.

## Non-goals

- Editor levels `starter` and `profi` (every user gets `standard`; `presserl.editor.level` is
  not evaluated yet).
- Reader article page and draft preview — part of `reader-articles`; the link targets its route.
- Images, info boxes, links, sections, issues, approval (`SUBMITTED`), spell check (not
  available in the canvas-rendered Wasm UI), readability hints, browser history/deep links for
  admin screens.
- Any REST contract change.

## Capabilities

### New Capabilities

- `admin-articles`: article lists, editor at level `standard`, autosave, undo/redo, actions from
  `allowedActions`, revision history and reader link in the administration app.

### Modified Capabilities

- `admin-shell`: after login the app opens the article list (newspaper name, user and roles stay
  visible in the header); UI texts are localized (German default, English).

## Impact

- **admin**: new screens and view models under `ui/`, body model under `api/` or `article/`,
  Compose Resources (`strings.xml` de/en), new dependencies `compose-rich-editor` and
  `components-resources`; tests with `kotlin.test`, Ktor `MockEngine` and coroutines-test.
- **backend, reader, deploy**: unaffected. The admin bundle gets new `composeResources` files,
  which `admin-cache-headers` already serves with `no-cache`.
- **REST contract / `ai/primer/endpoints.md` / `http/`**: unchanged (client already implemented
  in `articles-core`).
- **docs**: `docs/design-guidelines.md` §3 records the chosen editor technique.
