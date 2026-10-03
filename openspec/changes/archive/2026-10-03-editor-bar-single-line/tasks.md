## 1. Admin

- [x] 1.1 `ui/Layout.kt` (or a new `ui/FitWidth.kt`): pure `fitScale(contentWidth, maxWidth)` and the `FitWidth` layout that scales its content uniformly to fit (design D2)
- [x] 1.2 `EditorScreen.kt`: extract the undo/redo icon pair from the keyboard-open branch into one composable and use it in both the keyboard-open and the compact branch (design D1)
- [x] 1.3 `EditorScreen.kt`: replace the compact branch's `FlowRow` with a single `Row` (undo/redo icons, save state, article actions, primary action last) wrapped in `FitWidth`; adjust the comment that explains the old wrapping
- [x] 1.4 Keyboard-open and wide branches unchanged — check by diff

## 2. Tests

- [x] 2.1 `commonTest`: unit tests for `fitScale` (fits → 1, too wide → ratio, zero/negative widths guarded)
- [x] 2.2 `cd admin && ./gradlew check` passes

## 3. Verification

- [x] 3.1 Web at 390 px and 360 px (Playwright, German and English): published article as publisher — bar is one line, ends with "Publish"/"Veröffentlichen", nothing cut off; tapping "Take offline" opens its confirmation (tap lands on the scaled button)
- [x] 3.2 A54 (debug build against staging or dev): same check by screenshot, record the scale reached; keyboard open still shows the icon line, closing the keyboard brings the single-line bar back
- [x] 3.3 Web at 1280 px: wide bar unchanged
- [x] 3.4 `openspec validate editor-bar-single-line --strict`
