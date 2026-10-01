## 1. Artwork source (`icons/`)

- [x] 1.1 Copy the approved drafts from the scratchpad (design.md Context) into `icons/`: `gen.py`, feature graphic HTML template, rendered files; rebuild per D1 if the scratchpad is gone
- [x] 1.2 Clean up `gen.py`: font paths relative to the repo (reader WOFF2s), all outputs into `icons/`, feature graphic rendering (Chrome screenshot, crop to 1024×500, alpha removed) inside the script
- [x] 1.3 Extend `gen.py` with the Android vector drawables `android/ic_launcher_foreground.xml` and `android/ic_launcher_monochrome.xml` (D4)
- [x] 1.4 `icons/build.sh` (venv with fonttools + brotli on demand, runs `gen.py`) and `icons/sync.sh` with the D2 mapping and `--check`
- [x] 1.5 `icons/README.md`: motif, colours, tools and versions, regenerate/sync/check commands, where each file is used
- [x] 1.6 Run `build.sh` from a clean state; verify sizes and formats with `identify` (512×512, 1024×500 without alpha, ICO 16/32/48)

## 2. Reader / Backend

- [x] 2.1 `sync.sh`: icons into `META-INF/resources/reader/icons/`
- [x] 2.2 Icon URL resolution (theme file if present, else `/reader/icons/…`) next to `ThemeFiles.customCssPresent()`; new `ReaderPage` component(s) filled wherever `ReaderPage` is built
- [x] 2.3 `layout/reader.html`: SVG, ICO and Apple touch icon links (D3)
- [x] 2.4 Root route `/favicon.ico` (theme file or bundled ICO, `image/x-icon`, `no-cache`), registered on the Vert.x router like `/theme/*`
- [x] 2.5 Tests: front page and a print view link the three icons; `/reader/icons/*` and `/favicon.ico` answer `200` with the right type for a public and a private newspaper (anonymous); with `favicon.svg`/`favicon.ico` in the test theme directory the links and `/favicon.ico` switch to the theme's files while the touch icon stays default (extend `ThemeFilesTest`/`ReaderThemeTest`/`ReaderPrivateTest`)
- [x] 2.6 Run the reader/theme test classes (`./mvnw test -Dtest=…`)

## 3. Admin

- [x] 3.1 `sync.sh`: favicon files into `admin/composeApp/src/wasmJsMain/resources/`; `index.html` links `favicon.svg` (`type="image/svg+xml"`) and `favicon.ico` relatively
- [x] 3.2 Check that the admin bundle (`PrecompressedAdminBundle`) serves `/admin/favicon.svg` and `/admin/favicon.ico` with the right content types; add a backend test if the bundle test setup allows it
- [x] 3.3 `sync.sh`: launcher drawables into `admin/androidApp/src/main/res/drawable/`; `ic_launcher.xml` uses the monochrome drawable; `colors.xml` background `#A8321D` with an updated comment
- [x] 3.4 `sync.sh`: store graphics into `admin/androidApp/play/graphics/` (`icon-512.png`, `feature-1024x500-{de-DE,en-US}.png`)
- [x] 3.5 Build the Android debug app and the Wasm bundle (`reference_build_and_test.md`)

## 4. Deploy, Contract/Docs

- [x] 4.1 `deploy/theme/README.md` and the `deploy/theme/custom.css` starter comment: icon override files (`favicon.svg`, `favicon.ico`, `apple-touch-icon.png` 180×180), hard reload hint
- [x] 4.2 `docs/design-guidelines.md`: short "Brand icon" section (motif, colours, `icons/` as single source, sync)
- [x] 4.3 android-play-publishing: task 3.2 reworded to the copies from `icons/` and ticked; D7 icon/feature sentences name `icons/` and the per-language feature graphics (D5)
- [x] 4.4 Memory `reference_build_and_test.md`: regenerate (`icons/build.sh`), distribute (`icons/sync.sh`), check (`icons/sync.sh --check`)
- [x] 4.5 No REST contract change: confirm `ai/primer/endpoints.md` and `http/` need nothing

## 5. Verification

- [x] 5.1 `icons/sync.sh --check` passes; touching a copy makes it fail with that file named
- [x] 5.2 Reader in a headless browser (Playwright): tab icon requests go to `/reader/icons/favicon.svg`, no CSP violation; with a theme `favicon.svg` the request goes to `/theme/favicon.svg`
- [x] 5.3 Admin web app in a headless browser: favicon loads, no CSP violation
- [x] 5.4 Android on the A54: launcher icon fully visible on the red background; themed icons on show the monochrome "P" page; Android Studio adaptive-icon preview for circle/squircle/rounded square
- [x] 5.5 Stop every server, emulator or container started for the checks
