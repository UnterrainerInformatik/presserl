## Why

The reader still runs on a minimal placeholder stylesheet (Georgia on white, no tokens, no dark
mode) that ignores almost everything `docs/design-guidelines.md` asks for children aged 6–16:
a legible self-hosted body face, a switchable text size, tinted paper, dark mode, a newspaper grid
and section colours. A fork also has no way yet to restyle its newspaper, although forkability
("name, `.env` and theme — without touching code") is a core principle. M4 — *Look* — closes this
before images (M5) and print (M6) build on the same tokens.

## What Changes

- **Default theme on design tokens.** `reader.css` is rebuilt on `--presserl-*` custom properties
  (fonts, paper/ink/accent/rule colours, text size, letter spacing, measure, grid columns,
  `--presserl-section-<key>` for the eight section palette keys). Body text follows the
  readability rules (19 px default, line height 1.5, `65ch`, letter spacing, left-aligned, no
  hyphenation, bold not italics, WCAG AA contrast on a lightly tinted paper).
- **Self-hosted fonts.** Atkinson Hyperlegible (body), Playfair Display (headlines) and Andika
  (alternative for beginning readers) ship as `woff2` with their OFL licence under
  `/reader/fonts/`; no external origin, CSP stays `'self'`.
- **Dark mode** via `prefers-color-scheme: dark`, by redefining colour tokens only.
- **Fork theme.** A theme directory (`presserl.theme.dir`, default `/deployments/theme`) is served
  read-only at `/theme/*` (allow-listed static file types, no path escapes). When
  `custom.css` exists there, every reader page links it **after** the default theme.
- **Stable styling API.** `data-view` on `<main>` of every reader view, and documented classes
  (`.masthead`, `.section-bar`, `.lead-article`, `.article-card`, `.kicker`, `.byline`, …) that
  forks may target; the class names of the reader templates are aligned with this list.
- **Modular front page.** 12-column grid on desktop, 6 on tablet, 1 on phone; the lead story
  spans the full width, cards are rectangles with thin rules.
- **Section bar** below the masthead: the sections in their order, each with its colour as a
  small marker (display only — section pages are not part of this change). Front-page stories
  and the article page show the article's section with its colour marker.
- **Reader text-size switch** S/M/L/XL (17/19/22/26 px) on every reader page, without
  JavaScript: a small form posts to `POST /text-size`, which stores the choice in a cookie and
  redirects back. The server renders the effective size into `<html data-text-size>`; without a
  cookie the newspaper's `reader.text-size` applies.
- **Newspaper layer for `reader.text-size`.** New endpoint `PUT /api/newspaper/settings` sets or
  clears newspaper overrides — in this change only `reader.text-size`. `GET /api/newspaper`
  additionally returns `overrides`. New allowed action `CONFIGURE_NEWSPAPER` (publisher and
  editor-in-chief).
- **Admin app:** new header entry "Newspaper" (for `CONFIGURE_NEWSPAPER`) with a settings screen
  that sets the default reader text size or resets it to the installation default.
- **Deploy:** `deploy/theme/` with a do-nothing `custom.css` starter, a README and the example
  themes *Classic*, *Colourful* and *Night*; the compose file mounts it read-only.
  `../presserl-deployment` gets its own `deploy/theme/` and the mount.
- **Docs:** `docs/architecture.md` (config key `presserl.theme.dir` replaces
  `presserl.theme.css`, routes, `CONFIGURE_NEWSPAPER`), `docs/design-guidelines.md` (text-size
  switch without JS, class list), `deploy/INSTALL.md` (theming), primer and `.http` files.
- `ai/open-proposals.md`: the M4 entry is removed.

## Non-goals

- Section pages (`/sections/{slug}`); the section bar is not linked yet.
- Images, lead images, captions (M5) and print views (M6) — the tokens are prepared for them.
- A manual dark/light toggle; dark mode follows the operating system only.
- Writable newspaper settings other than `reader.text-size` (name, subtitle, visibility, editor
  level, …) and a per-user text size stored server-side.
- Editing the theme from the admin app, uploading theme files, or theme hot-reload beyond
  normal HTTP revalidation.
- Theming the admin app with the reader tokens or `custom.css`.
- Reading time and difficulty indicators.

## Capabilities

### New Capabilities
- `reader-theme`: default theme on design tokens, self-hosted fonts, dark mode, front-page grid,
  section bar, fork theme directory served at `/theme/*` and linked `custom.css`, stable styling
  API (`data-view`, documented classes).
- `reader-text-size`: reader text-size switch, `POST /text-size`, cookie, rendering of the
  effective size with the newspaper default as fallback.
- `admin-newspaper`: admin screen for the newspaper settings (default reader text size).

### Modified Capabilities
- `newspaper-settings`: `GET /api/newspaper` returns `overrides`; new `PUT /api/newspaper/settings`
  to set/clear the `reader.text-size` override.
- `api-authentication`: `allowedActions` gains `CONFIGURE_NEWSPAPER`.
- `admin-shell`: header entry "Newspaper" for `CONFIGURE_NEWSPAPER`.
- `reader-articles`: front-page stories and the article page show the article's section.
- `deployment`: compose mounts `deploy/theme/`; the deployment ships a theme starter and example
  themes.

## Impact

- **Backend / reader:** `reader.css` rewritten, fonts under `META-INF/resources/reader/fonts/`,
  reader templates (layout, masthead, story, article, not-found) and `ReaderResource`, new theme
  file route and text-size resource, `ReaderArticle` carries the section, `NewspaperResource`
  write endpoint, `NewspaperAction`/`MeDto`, config `presserl.theme.dir`, auth permissions.
- **Admin:** `NavEntry`, `NewspaperAction`, `NewspaperDto` (+`overrides`), `ApiClient`, new
  newspaper settings screen and labels (de/en).
- **Deploy:** `deploy/theme/**`, `deploy/compose.yaml`, `deploy/INSTALL.md`;
  `../presserl-deployment/deploy/theme/`, its `compose.yaml`.
- **Docs / contract:** `docs/architecture.md`, `docs/design-guidelines.md`,
  `ai/primer/endpoints.md`, `http/newspaper.http`, `http/reader.http`, `http/me.http`,
  `ai/open-proposals.md`.
- **Dependencies:** no new libraries; font files (OFL) are vendored.
- **REST contract:** additive — new field `overrides`, new endpoint, new `allowedActions` value
  (clients ignore unknown values).
