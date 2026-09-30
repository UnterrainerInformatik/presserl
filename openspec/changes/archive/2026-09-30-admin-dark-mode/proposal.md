## Why

The reader follows the system light/dark preference, but the admin app is always light: `App.kt`
wraps everything in a bare `MaterialTheme { … }`, which falls back to Material 3's light scheme.
Editors working in a dark environment get a glaring white screen, and the two apps of one
newspaper behave differently.

## What Changes

- The admin app picks a light or a dark Material 3 colour scheme from the system preference
  (`isSystemInDarkTheme()`), and switches when the preference changes while the app is open.
- The section palette markers get a dark variant (brighter tones, matching the reader's dark
  section tokens) so they stay visible on a dark surface.
- The start-up loading indicator and the start-up failure notice (plain HTML/CSS shown before
  Compose draws) follow `prefers-color-scheme` as well, so a dark system does not flash white.
- Deliberately unchanged: the account slip's QR code stays black on white (scanners), the image
  editor's crop/focus overlays stay white/black over the photo, and the printed account slip
  stays black on white.
- No manual toggle; the system preference is the only input.

## Non-goals

- A light/dark/auto switch in the admin UI or a stored per-user choice.
- Theming the admin app per newspaper (fork theme colours stay reader-only).
- Changes to the reader's dark mode.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `admin-shell`: new requirement that the admin app follows the system colour scheme, including
  the start-up loading indicator and failure notice.
- `admin-sections`: the section colour markers use a dark-scheme variant of the palette.

## Impact

- Platforms: **admin** only. Backend, reader, deploy and docs are unaffected; no REST change,
  so `ai/primer/endpoints.md` stays as is.
- Code: `admin/composeApp/src/commonMain/.../ui/App.kt` (theme wrapper), a new theme file with
  light/dark schemes, `ui/section/SectionPalette.kt` (scheme-aware colours),
  `admin/composeApp/src/wasmJsMain/resources/styles.css` (start-up screens), `index.html`
  (`color-scheme` meta).
- Tests: Kotlin unit tests for scheme selection and the palette's dark variant.
