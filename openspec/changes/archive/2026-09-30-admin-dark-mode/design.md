## Context

- `ui/App.kt` wraps the whole app in `MaterialTheme { Surface(Modifier.fillMaxSize()) { … } }`
  without a `colorScheme`, so Material 3's default light scheme applies. Almost all screens
  already take colours from `MaterialTheme.colorScheme` (banners, spell marks, icons via
  `LocalContentColor`), so a scheme switch reaches them automatically.
- Hard-coded colours in `commonMain`:
  - `ui/section/SectionPalette.kt`: eight Material-700 tones plus `Color.Gray` fallback.
  - `ui/account/AccountScreens.kt` `QrImage`: black on white on purpose (scanners).
  - `ui/media/MediaScreens.kt`: white/black strokes and a black dim over the photo in the image
    editor; they sit on image content, not on the app background.
  - `ui/Icons.kt`: `Color.Black` path fill, tinted at draw time with `LocalContentColor`.
- `wasmJsMain/resources/styles.css`: `#presserl-startup-notice` and `#presserl-startup-loading`
  are `#fff` / `#1c1b1f`, spinner `#e0dfe3` / `#1c1b1f`; the `@media print` slip is black on white.
- Compose Multiplatform 1.12.1, Material 3 1.9.0. On the web target `isSystemInDarkTheme()` reads
  `prefers-color-scheme`.
- The reader's dark section tokens (`reader.css`) are a tested set of brighter tones on a dark
  paper background.

## Goals / Non-Goals

**Goals:**
- One place that decides light vs dark and provides the schemes.
- Palette colours resolved per scheme through a single function, testable without UI.

**Non-Goals:**
- A custom brand colour set; the Material 3 baseline light/dark schemes are used as they are.
- Touching the image-editor overlays, the QR code or the print CSS.

## Decisions

1. **`PresserlTheme` composable in `ui/Theme.kt`.** It calls
   `MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme())` with
   `dark = isSystemInDarkTheme()` as default parameter; `App.kt` replaces `MaterialTheme` with it.
   A parameter keeps it testable and gives a later manual toggle a hook without changing callers.
   Alternative: switching schemes inline in `App.kt` — rejected, the palette also needs to know
   the scheme.
2. **Dark detection is exposed via a `CompositionLocal` (`LocalDarkScheme`)** provided by
   `PresserlTheme`. Checking `colorScheme.background.luminance()` would also work but is implicit;
   an explicit boolean is clearer for `sectionColor`.
3. **`sectionColor(key, dark: Boolean)`** as a pure function with the current tones for light and
   the reader's dark tokens for dark (`#ef6b6b`, `#f39a4a`, `#e3c24a`, `#6cc070`, `#4db6ac`,
   `#6ea8f0`, `#c08be0`, `#f06ba0`); the fallback becomes `Color.Gray` in both. `ColorMarker`
   reads `LocalDarkScheme.current`. Reusing the reader's dark set keeps the two apps visually
   consistent and avoids inventing a second palette.
4. **Live switching.** Rely on `isSystemInDarkTheme()` recomposing on a `prefers-color-scheme`
   change. If the manual check shows that 1.12.1 does not observe the change on Wasm, add a small
   `wasmJsMain` `actual` that holds a `mutableStateOf` updated by a
   `matchMedia("(prefers-color-scheme: dark)")` `change` listener (via `kotlinx-browser`), behind an
   `expect fun rememberSystemDark(): Boolean`. Only then — no `expect/actual` up front.
   Screen state lives in models above the theme, so a recomposition with a new scheme keeps input.
5. **Start-up screens via CSS custom properties.** `styles.css` gets
   `--presserl-startup-bg/-fg/-track` on `:root`, a `@media (prefers-color-scheme: dark)` block
   (`#1c1b1f` background, `#e6e1e5` text, `#49454f` track — Material 3 baseline dark neutrals),
   and `color-scheme: light dark`. `index.html` gets `<meta name="color-scheme" content="light dark">`
   so the browser paints the page background dark before any CSS/Wasm loads. No inline styles, so
   the admin CSP is untouched. The `@media print` block keeps its explicit black on white.

## Risks / Trade-offs

- [A screen uses a light-only colour not found by grep (e.g. `Color.Unspecified` backgrounds,
  `copy(alpha)` on a light tone)] → manual Playwright pass through every screen in dark
  (tasks), fix what shows up.
- [`isSystemInDarkTheme()` not reactive on Wasm] → decision 4 fallback.
- [Section tones differ between admin light (Material 700) and reader light] → unchanged
  behaviour, out of scope.

## Migration Plan

Admin-only frontend change, ships with the next image. Rollback = previous image. No data or
configuration change.
