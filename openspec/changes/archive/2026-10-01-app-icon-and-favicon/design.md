## Context

See proposal.md for the motivation. Current state:

- Drafts approved by Gerald on 2026-10-01 exist in the session scratchpad
  `/tmp/claude-1000/-mnt-data-source-JAVA-presserl/921420f1-f24e-4d8d-9141-1b4d29858d49/scratchpad/`
  (`gen.py`, `feature-{de,en}.html`, `out/*`). `gen.py` holds the "P" as an SVG path extracted
  with fontTools from `reader/fonts/playfair-display-latin-700-normal.woff2`; the feature graphic
  is an HTML page (Playfair + Atkinson Hyperlegible via `@font-face` on the reader WOFF2s)
  screenshotted by headless Chrome and flattened with ImageMagick. If `/tmp` was wiped before
  apply, the artwork is rebuilt from the description in D1 — the scratchpad is a convenience,
  not a dependency.
- Reader: `layout/reader.html` has no icon links; static files live under
  `META-INF/resources/reader/` (fonts, `reader.css`, `print.js`) and are public. `ThemeFiles`
  serves the fork's theme directory at `/theme/*` and already allows `svg`, `ico` and `png`.
  `ReaderPage` carries per-request flags such as `customCss` and `legalNotice`, computed from
  `ThemeFiles` on every request.
- Admin web: `admin/composeApp/src/wasmJsMain/resources/index.html`, served under `/admin/` by
  `PrecompressedAdminBundle`, CSP `default-src 'self'` (img-src falls back to it).
- Android: minSdk 26, so the adaptive icon in `mipmap-anydpi-v26` is the only launcher icon
  needed; foreground and monochrome currently share one purple-on-white vector drawable,
  background is `@color/ic_launcher_background` `#6750A4`.
- android-play-publishing (open, not applied) plans the store graphics in
  `admin/androidApp/play/graphics/` (D7, task 3.2) "rendered from the launcher icon source".

## Goals / Non-Goals

**Goals:**
- One generator run produces every artwork variant, including the Android vector drawables.
- Consumers hold plain copies, so no build (Gradle, Maven, Docker) needs Python, fonts or Chrome.

**Non-Goals:**
- Running the generator or the sync check in CI.
- Pixel-hinted hand-drawn 16 px variant; the 16 px rendering of the simplified favicon is enough.

## Decisions

### D1 — Artwork and generator in `icons/`
`icons/gen.py` (from the scratchpad, cleaned up) writes:
- `play-icon.svg`, `play-icon-512.png` — tilted (−6°) cream front page on full-bleed `#a8321d`:
  "P" masthead, double rule, eight section-colour bars, lead picture (sky `#6ea8f0`, sun, two
  green hills), six text lines, soft drop shadow.
- `favicon.svg`, `favicon.ico` (16/32/48), `favicon-{16,32,48,180,192,512}.png` — simplified:
  upright sheet with "P" and one rule on a rounded red square, legible at 16 px.
- `feature-graphic-{de,en}.png` — red band with the front page on the left; right on paper:
  "Presserl" (Playfair), double rule, claim ("Schreib für deine eigene Zeitung." / "Write for your
  own newspaper."), subline ("Für Kinder von 6 bis 16 – ohne Werbung, ohne Tracking." / "For kids
  aged 6 to 16 – no ads, no tracking."), section-colour bars.
- `android/ic_launcher_foreground.xml`, `android/ic_launcher_monochrome.xml` (D4).

Tools: Python venv with `fonttools` + `brotli` (glyph extraction from WOFF2), `rsvg-convert`,
ImageMagick 7 (`magick`), `google-chrome-stable --headless=new`. Fonts are read from the reader's
resources, never copied into `icons/`. The README lists the commands; a `Makefile`-less shell
entry `icons/build.sh` creates the venv on demand and runs everything.

*Alternative:* render everything in the browser or keep only PNGs. Rejected: SVG/vector sources
stay editable, and the Android drawables must be vectors anyway.

### D2 — Distribution by `icons/sync.sh`, verified by `--check`
`sync.sh` copies each generated file to its consumer according to one mapping table:

| Source in `icons/` | Copy |
|---|---|
| `favicon.svg`, `favicon.ico`, `favicon-180.png` | `backend/src/main/resources/META-INF/resources/reader/icons/{favicon.svg,favicon.ico,apple-touch-icon.png}` |
| `favicon.svg`, `favicon.ico` | `admin/composeApp/src/wasmJsMain/resources/{favicon.svg,favicon.ico}` |
| `android/ic_launcher_foreground.xml`, `android/ic_launcher_monochrome.xml` | `admin/androidApp/src/main/res/drawable/` |
| `play-icon-512.png` | `admin/androidApp/play/graphics/icon-512.png` |
| `feature-graphic-{de,en}.png` | `admin/androidApp/play/graphics/feature-1024x500-{de-DE,en-US}.png` |

`sync.sh --check` compares with `cmp` and exits non-zero listing differing or missing copies.
Copies over symlinks or a Gradle/Maven copy task: the Docker build context and the Android/Wasm
resource pipelines then need nothing outside their module.

### D3 — Reader icon links and fork override
New record component(s) on `ReaderPage`: the three icon URLs, resolved per request by a small
method next to `customCssPresent()` (e.g. `ThemeFiles.icon(name)` returning `/theme/<name>` when
the theme has the file, else `/reader/icons/<name>`). The layout head gets:

```html
<link rel="icon" href="{page.icons.svg}" type="image/svg+xml">
<link rel="icon" href="{page.icons.ico}" sizes="48x48">
<link rel="apple-touch-icon" href="{page.icons.touch}">
```

The ICO link carries `sizes` so browsers prefer the SVG. Print views use the same layout, so
they get the links too. Theme lookups are file-existence checks per request, as for `custom.css`.

`/favicon.ico` at the root: a Vert.x route registered like `/theme/*` that sends the theme's
`favicon.ico` if present, otherwise the bundled one, `image/x-icon`, `Cache-Control: no-cache`.
It bypasses the reader OIDC tenant, so private newspapers answer it too. `/reader/icons/*` is
covered by the existing public static-resource handling (as `/reader/fonts/*`); a test confirms
it for a private newspaper.

*Alternative:* a static `META-INF/resources/favicon.ico`. Rejected: it could not be replaced by
a fork's theme.

### D4 — Android adaptive icon from the generator
`gen.py` emits the front page as Android vector XML on the 108 dp canvas: the 512 design's sheet
group scaled so its rotated bounding box fits the 66 dp safe zone (centre 54/54, about 62 dp
across), `<group android:rotation="-6" android:pivotX="54" android:pivotY="54">`, the lead picture
via `<clip-path>`. The shadow is dropped (launchers add their own). Background becomes
`@color/ic_launcher_background` = `#A8321D`. The monochrome drawable is a single-colour version:
the sheet as a filled shape with the "P", the rules, the picture frame and the text lines cut out
(even-odd fill), so themed icons show a paper with a "P". `ic_launcher.xml` points `monochrome`
to the new drawable.

### D5 — Play graphics and android-play-publishing
Google Play accepts a feature graphic per listing language, so both languages get their own file
(D2 table). This change updates android-play-publishing's task 3.2 to "copy from `icons/` via
`sync.sh` (done in app-icon-and-favicon)" and ticks it, and changes D7's icon/feature sentences to
name `icons/` as the source and the per-language file names. Its `check.sh` (task 3.5) keeps
checking sizes.

## Risks / Trade-offs

- [Copies drift from `icons/`] → `sync.sh --check` in the tasks' verification and the README;
  memory note so it is run whenever artwork changes.
- [Generator depends on Chrome and ImageMagick on the developer machine] → only needed to
  regenerate; consumers never need it. README names the versions used.
- [Rotated sheet in the adaptive icon too small or clipped by some launcher] → check on the A54
  (One UI squircle) and with Android Studio's adaptive icon preview (circle, squircle, rounded
  square) before ticking.
- [Browsers cache favicons aggressively] → `no-cache` on `/favicon.ico`; a fork changing its icon
  may still need a hard reload — noted in `deploy/theme/README.md`.

## Migration Plan

Pure additions plus a launcher icon swap; rollback is reverting the commit. Staging picks the
reader icons up with the next image; Android users see the new icon with the next app build.
