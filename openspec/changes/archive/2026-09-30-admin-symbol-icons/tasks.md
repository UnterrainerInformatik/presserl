## 1. Admin – coverage check

- [x] 1.1 Build the production bundle (`./gradlew wasmJsBrowserDistribution`), open article list, editor, issue, section, media, accounts and newspaper settings in Playwright with request logging, and record which characters cause a `fonts.gstatic.com` request (verify: list of triggering code points noted in design.md decision 3)

## 2. Admin – icons

- [x] 2.1 Add `ui/Icons.kt` with `ImageVector` definitions (back, up, down, close, undo, redo, open-in-new, play-arrow) and a label-with-icon helper; verify it compiles with `./gradlew compileKotlinWasmJs`
- [x] 2.2 Replace `←` in `ui/Common.kt` back button with the icon; verify the button still shows the localized label
- [x] 2.3 Replace `↑ ↓ ✕` in `ui/section/SectionScreens.kt`, `ui/issue/IssueScreens.kt` and `ui/editor/EditorScreen.kt` (incl. the bare `✕` chip remove button, which gets the localized `remove_item` string ("Punkt entfernen" / "Remove item") as content description since it has no label)
- [x] 2.4 Replace `↶ ↷` (editor undo/redo) and `↗` (view in reader, open issue in reader/print) with icons
- [x] 2.5 Replace `▶` in `ui/editor/FieldHelp.kt` and any character from 1.1 (e.g. `•`) with a drawn graphic or covered equivalent; verify existing `FieldHelpTest` still passes

## 3. Admin – regression guard

- [x] 3.1 Add Gradle task `checkUiGlyphs` in `composeApp/build.gradle.kts` scanning non-comment lines of `src/commonMain/**/*.kt` and `composeResources/values*/strings.xml` against the allowlist from design.md decision 4 (plus punctuation verified in 1.1); wire into `check`; verify it fails on a temporarily inserted `"→"` and passes on the cleaned sources

## 4. Verification

- [x] 4.1 Run `cd admin && ./gradlew check`; all tests and `checkUiGlyphs` pass
- [x] 4.2 Repeat the Playwright run from 1.1 against the new bundle: zero requests to font hosts and zero CSP violations on all listed screens; screenshots show visible arrows/crosses (spec scenarios "No font request on any admin screen" and "Back button shows an arrow graphic")
- [x] 4.3 Check the Playwright `ariaSnapshot()` of editor and issue screens: button names are the localized labels without symbol characters (spec scenario "Accessible names keep their labels")
