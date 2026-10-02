## 1. Admin — shared groundwork

- [x] 1.1 Add `Icons.Menu`, `Icons.Calendar` and `Icons.Check` vectors to `ui/Icons.kt` and new de/en strings (menu, choose date); verify `./gradlew check` stays green (CSP style-hash and strings tests included)
- [x] 1.2 Add `LocalCompactLayout` provided by `LoggedIn` (< 720 dp, one constant replacing `NARROW_HEADER`/`NARROW_BAR`) and `expect fun keyboardVisible()` with Android (`WindowInsets.isImeVisible`) and wasmJs (`false`) actuals (design D1, D3); verify the app compiles for both targets (`./gradlew assembleDebug wasmJsBrowserDistribution`)

## 2. Admin — header

- [x] 2.1 Replace the narrow two-row header with the compact row (newspaper name + menu button) and a `DropdownMenu` holding nav entries (current marked), the wrapping user line → "My account", and "Log out"; every item closes the menu (design D2). Wide header stays unchanged; verify with the Playwright UI check in 4.1
- [x] 2.2 Hide header and divider while `compact && keyboardVisible()`; verify on the A54 in 4.2

## 3. Admin — editor, issues, images

- [x] 3.1 Extract `EditorNotices` (status row + notice banners) and render it inside the scrolling column when compact, keeping the conflict banner fixed (design D4); verify `EditorModelTest` and the rest of `./gradlew check` stay green and with 4.1
- [x] 3.2 Compact `BottomBar` while the keyboard is shown: icon-only undo/redo with "Undo"/"Redo" content descriptions + save state, article actions omitted (design D5); verify on the A54 in 4.2
- [x] 3.3 Add `isoToEpochMillis`/`epochMillisToIso` to `IssueDates.kt` with Kotlin tests (epoch, leap day 2024-02-29, 2026-10-12, year-end, round-trip over a range of days); verify `./gradlew check`
- [x] 3.4 Add the calendar button + `DatePickerDialog` to `DateField` (new-issue dialog and issue detail), preselected from the field or nothing selected when empty/invalid, confirm writes `YYYY-MM-DD` via `onChange`, cancel keeps the field (design D6); verify with 4.1 and check whether the picker's own texts appear in German
- [x] 3.5 Add `serverImageType(bytes)` and `jpegName(name)` to commonMain media with Kotlin tests (JPEG/PNG/WebP magic accepted, HEIC `ftypheic`, GIF and short/empty input refused; `IMG_1.heic`→`IMG_1.jpg`, `photo`→`photo.jpg`, `a.b.HEIF`→`a.b.jpg`); verify `./gradlew check`
- [x] 3.6 Convert non-server image types in `FilePicker.android.kt` (rename by MIME type, `ImageDecoder` ≥ API 28 / `BitmapFactory` below, JPEG q90 on IO, decode-size cap at the server limits, fallback to original bytes on failure) (design D7); verify on the A54 in 4.2

## 4. Verification

- [x] 4.1 Playwright UI check of the admin web build at 390 px and 1280 px against a local backend: compact header + menu (all six entries, current marked, entry closes menu, user line → My account, Log out), sectionless reporter menu, editor status row scrolls away, conflict banner stays, date picker in new-issue dialog and detail (pick, cancel, typing still works), wide layout unchanged; screenshots in the scratchpad
- [x] 4.2 Check on the A54 (debug build via adb): header hidden and one-line bottom bar while typing, both restored when the keyboard closes; a HEIC photo from the gallery uploads as `.jpg` upright; JPEG upload unchanged; camera photo still uploads (run on the emulator `presserl`, the A54 was not connected; the system photo picker names files by media id, so the dialog listed `22.jpg` for the HEIC)
- [x] 4.3 Stop every server, emulator and container started for 4.1/4.2 and confirm with `ps`/`ss`/`docker ps`

## 5. Contract/Docs

- [x] 5.1 Remove the "Admin app on phones — layout polish" entry from `ai/open-proposals.md` (done at propose time; confirm it is gone). If the date picker is not localized (3.4), add a backlog entry for it there; `ai/primer/endpoints.md` needs no change
