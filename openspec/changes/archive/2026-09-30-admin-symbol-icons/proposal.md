## Why

Every admin page logs a CSP violation: Compose Multiplatform's Wasm runtime ships a fallback font
downloader that fetches Noto fonts from `https://fonts.gstatic.com/s/` whenever a glyph is missing
from the loaded fonts. Our buttons use arrow and symbol characters as text (`← Back` on almost every
screen, `↑`/`↓`, `✕`, `↶`/`↷`, `↗`, `▶`), so the runtime tries to download Noto Sans Symbols. The
admin CSP (`connect-src 'self' <issuer>`) correctly blocks this, leaving a console error and
symbols that render blank or as boxes. Allowing Google's font host is not an option: the policy is
`'self'`-only by design, and every admin visit would send the user's IP address to Google.

## What Changes

- The symbols in admin buttons and markers are drawn as vector icons bundled with the app instead of
  being typed as text characters: back (`←`), move up/down (`↑`/`↓`), remove (`✕`), undo/redo
  (`↶`/`↷`), open externally (`↗`) and the field-help pointer (`▶`).
- Any other character in the app's own UI texts that the runtime cannot render from its loaded
  fonts (checked for `–`, `…`, `„`, `“`, `”`, `•`) is replaced the same way, or by a plain
  equivalent, if the check shows it triggers a download.
- A build check rejects symbol characters outside an allowlist in UI string literals and string
  resources, so the problem does not come back with the next button.
- The CSP stays unchanged.

## Non-goals

- User-entered content (article text, captions, names) containing emoji or rare scripts: the runtime
  may still try to download a fallback font for those. That is a separate topic.
- Changing the admin typography or bundling a text font.
- The reader: it is server-rendered HTML with browser fonts and is not affected.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `admin-shell`: new requirement that the admin UI's own texts and symbols render without
  downloading fonts from another origin.

## Impact

- **admin**: `ui/Common.kt`, `ui/editor/EditorScreen.kt`, `ui/editor/FieldHelp.kt`,
  `ui/issue/IssueScreens.kt`, `ui/section/SectionScreens.kt`; a new small file of icon definitions;
  a Gradle verification task wired into `check`.
- **backend, reader, deploy**: none. CSP in `SecurityHeaders.java` unchanged.
- **docs**: none (`docs/architecture.md` already states the `'self'`-only policy).
- No new dependencies (icons are defined in-project, not via `material-icons-extended`).
