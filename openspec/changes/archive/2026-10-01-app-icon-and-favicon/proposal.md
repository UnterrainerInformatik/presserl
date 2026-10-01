## Why

Presserl has no face of its own: the reader and the admin web app show the browser's blank tab
icon, the Android launcher shows a purple Material-baseline newspaper that matches nothing else,
and the Google Play listing (android-play-publishing) needs a 512×512 icon and a 1024×500 feature
graphic. A single brand icon in the reader's own colours and type, kept in one place and built
into every surface from there, fixes all of it before the app goes to the Play closed test.

## What Changes

- New top-level `icons/` directory as the single source of the brand artwork: a tilted newspaper
  front page with a Playfair Display "P" masthead, double rule, the eight section colours as
  section bar and a child's drawing as lead picture, paper `#fbf8f1` on accent red `#a8321d`.
  It holds the generator script, the SVG sources, every rendered file (Play icon 512, feature
  graphics de/en 1024×500, favicon SVG/ICO/PNGs) and a README on regenerating and distributing
  them; a sync script copies the files into their consumers and can check that the copies are
  current.
- Android: the adaptive launcher icon (foreground, background, monochrome) shows the new motif;
  the purple `#6750A4` background goes.
- Reader: every page links a favicon (SVG with ICO fallback) and an Apple touch icon; the files
  are served by the reader without login, also for a private newspaper; `/favicon.ico` at the
  root answers too.
- Forks: a `favicon.svg`, `favicon.ico` or `apple-touch-icon.png` in the theme directory replaces
  the default icon of that kind in the reader.
- Admin web app: `index.html` links the same favicon.
- Play Store: icon and feature graphics are copied into the store listing folder that
  android-play-publishing defines; that change's task 3.2 and decision D7 are pointed at
  `icons/` as source.

## Non-goals

- A wordmark or logo for the reader masthead — the masthead stays the newspaper's name in type.
- A web app manifest / installable PWA.
- iOS app icon (no iOS target yet).
- Per-fork Android or Play icons — the app in the store is upstream's app for every newspaper.
- Changing the admin app's Material colour scheme.

## Capabilities

### New Capabilities
- `brand-icons`: the brand artwork's single source in `icons/`, its generated variants and the
  rule that every surface uses copies of these files.

### Modified Capabilities
- `reader-shell`: reader pages link the favicon and Apple touch icon, which are served without login.
- `reader-theme`: icon files in the fork's theme directory replace the default icons.
- `admin-shell`: the admin web app shows the favicon.
- `admin-android`: the launcher icon shows the brand motif, including a monochrome variant.

## Impact

- **icons/** (new): `gen.py`, SVG sources, rendered PNG/ICO files, `sync.sh`, `README.md`.
- **reader / backend**: icon files under the reader's static resources, `layout/reader.html`
  head links, root `/favicon.ico`, fork override lookup next to `ThemeFiles`; JUnit/`@QuarkusTest`
  tests. No REST contract change; `ai/primer/endpoints.md` unaffected.
- **admin**: `admin/composeApp/src/wasmJsMain/resources/` (favicon files, `index.html`),
  `admin/androidApp/src/main/res/` (launcher drawables, colours),
  `admin/androidApp/play/graphics/` (store graphics).
- **deploy**: `deploy/theme/README.md` and the `custom.css` starter comment name the icon override files.
- **docs**: `docs/design-guidelines.md` (brand icon section).
- **openspec**: android-play-publishing task 3.2 and D7 updated.
- **ai/**: memory build/test note for regenerating and syncing `icons/`; `ai/open-proposals.md` unchanged (the store icon belongs to android-play-publishing).
- **Deployment repos**: none required; forks may add their own icons to their theme later.
