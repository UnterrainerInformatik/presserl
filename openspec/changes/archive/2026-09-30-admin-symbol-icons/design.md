## Context

See proposal.md – Why. Compose Multiplatform 1.12's Wasm runtime contains
`androidx.compose.ui.platform.WebFallbackFontDownloader`; for any code point missing from the
loaded fonts it fetches a Noto font from `https://fonts.gstatic.com/s/`. The string is compiled
into the Wasm module, so it cannot be redirected by configuration we control.

Symbol characters in the admin's own UI today (string literals in `commonMain`):
`←` (`ui/Common.kt`, back button on most screens), `↑ ↓ ✕` (editor, issue, section screens),
`↶ ↷` (editor undo/redo), `↗` (open in reader / print), `▶` (field help), `•` (bullet lists in
`Common.kt`, `EditorScreen.kt`, `FieldHelp.kt`). Typographic punctuation `– … „ “ ”` is used in
string resources. The app already avoids fonts for symbols once: `PictureIcon` in
`ui/media/LeadImageViews.kt` draws a picture symbol on a `Canvas` because "emoji fonts are not
loaded on the canvas".

## Goals / Non-Goals

**Goals:**
- Zero font downloads caused by the app's own texts.
- A regression guard that runs with `./gradlew check`.

**Non-Goals:**
- Suppressing or re-hosting the runtime's fallback downloader (would still fire for user content,
  and there is no public switch we can rely on).
- Visual redesign of the buttons beyond swapping the symbol.

## Decisions

**1. Material icon paths as `ImageVector`s in the project.** A new file
`ui/Icons.kt` defines the needed icons (`ArrowBack`, `ArrowUpward`, `ArrowDownward`, `Close`,
`Undo`, `Redo`, `OpenInNew`, `PlayArrow`) with `ImageVector.Builder` and the path data from
Material Symbols (Apache 2.0), plus a `LabeledIcon(icon, label)` / button-content helper that puts
an `Icon` (18 dp, content colour, `contentDescription = null`) before or after the `Text`.
- *Alternative: `material-icons-extended` dependency.* Rejected: several MB of Wasm for eight icons,
  and the artifact is no longer maintained for new Compose versions.
- *Alternative: draw on `Canvas` like `PictureIcon`.* Works, but path data from Material is less
  code and consistent in stroke weight; `PictureIcon` stays as is.
- *Alternative: bundle a Noto Sans Symbols subset font.* Rejected: needs subsetting tooling and
  font-fallback registration, and every new symbol means rebuilding the subset.

**2. `contentDescription = null` on the icons.** The button's label already names the action
(spec: accessible names keep their labels); a description would be read twice.

**3. Empirical coverage check first.** Before replacing `•` and the typographic punctuation, the
built app is opened in Playwright with request logging. Only characters that actually trigger a
`fonts.gstatic.com` request are replaced (`•` → a small drawn dot or icon; punctuation → keep if
covered). The arrows/crosses are replaced regardless, since they are the reported trigger.
*Result (2026-09-29, production bundle, `de-DE` and `en-US`, all seven screens plus the open
field help):* the runtime's `FallbackFontDownloader` requested fonts for exactly U+2190 `←`,
U+2191 `↑`, U+2193 `↓`, U+2197 `↗`, U+21B6 `↶`, U+21B7 `↷`, U+25B6 `▶` and U+2715 `✕`
(Noto Sans Symbols, Symbols 2, Math, Color Emoji). `•` (list blocks, help sample), `–`, `…`, `„`,
`“`, `”` and `·` were on screen and triggered nothing, so they stay as text.

**4. Gradle guard task `checkUiGlyphs`.** Scans `src/commonMain/**/*.kt` string literals (lines
that are not comments) and `composeResources/values*/strings.xml` for code points outside an
allowlist: Basic Latin, Latin-1 Supplement, Latin Extended-A, plus the punctuation verified in
decision 3. Fails with file, line and code point. Wired into `check`. Kept as plain Gradle/Kotlin
in `composeApp/build.gradle.kts` (no plugin), since Kotlin/Wasm tests cannot read source files.

## Risks / Trade-offs

- [The runtime's default font lacks some punctuation we keep] → decision 3 verifies with real
  network logs before fixing the allowlist.
- [Guard misreads a comment or KDoc containing `→`] → only lines not starting with `*` or `//`
  are scanned; KDoc arrows in `Autosaver.kt`/`Pkce.kt` are comments and ignored.
- [User content still triggers downloads] → out of scope (proposal Non-goals); CSP keeps blocking.

## Migration Plan

Admin-only frontend change; ships with the next image. Rollback = revert the commit.
