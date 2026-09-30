## 1. Theme

- [x] 1.1 Add `ui/Theme.kt` with `LocalDarkScheme` and `PresserlTheme(dark: Boolean = isSystemInDarkTheme(), content)` providing `lightColorScheme()` / `darkColorScheme()`
- [x] 1.2 Replace `MaterialTheme { … }` in `ui/App.kt` with `PresserlTheme { … }`

## 2. Section palette

- [x] 2.1 Change `sectionColor(key)` to `sectionColor(key, dark)` with the reader's dark tokens for `dark = true`; `ColorMarker` reads `LocalDarkScheme.current`; update all callers
- [x] 2.2 Unit test: every palette key yields different light and dark colours, both sets have eight distinct colours, unknown key falls back to gray

## 3. Start-up screens

- [x] 3.1 `styles.css`: introduce `--presserl-startup-bg/-fg/-track`, a `prefers-color-scheme: dark` block and `color-scheme: light dark`; use the variables in `#presserl-startup-notice`, `#presserl-startup-loading` and `.presserl-startup-spinner`; leave `@media print` as is
- [x] 3.2 `index.html`: add `<meta name="color-scheme" content="light dark">`

## 4. Verification

- [x] 4.1 Run the admin unit tests
- [x] 4.2 Playwright with `colorScheme: 'dark'`: loading indicator, a start-up notice (WebGL disabled), login, article list, editor (incl. spell marks), sections (markers, swatches), accounts (slip with QR stays black on white), media, newspaper settings; screenshot each and fix unreadable spots
- [x] 4.3 Playwright: switch emulated scheme light→dark while the editor holds unsaved text; confirm the app switches without reload and keeps the text. If it does not switch, implement the `matchMedia` fallback from design decision 4 and re-check
- [x] 4.4 Playwright with `colorScheme: 'light'`: spot-check that the light look is unchanged
- [x] 4.5 Print the account slip under dark emulation (print media) and confirm black on white
