# Presserl brand icon

The single source of Presserl's brand artwork. Every surface that shows the icon — reader, admin web
app, Android launcher, Google Play listing — uses a copy of a file in this directory; nothing is drawn
anywhere else.

## Motif

A newspaper front page, tilted by −6°, on the reader's accent red:

- a Playfair Display Bold "P" as masthead, below it a double rule,
- the eight section colours as section bar,
- a child's drawing as lead picture (sky, sun, two green hills) next to six text lines,
- a soft drop shadow (not in the Android layers, launchers add their own).

The favicon is simplified so it reads at 16 px: an upright sheet with the "P" and one rule on a
rounded red square.

Colours are the default theme's (`reader.css`): paper `#fbf8f1`, ink `#1d1b18`, muted `#5b5650`,
accent `#a8321d`, rule `#d9d2c3`, sections `#c62828 #bf5a00 #9a7400 #2e7d32 #00786b #1565c0 #7b1fa2
#c2185b`; picture sky `#6ea8f0`, sun `#e3c24a`, hills `#2e7d32`/`#6cc070`. The feature graphic adds
Atkinson Hyperlegible for the claim. Both faces are read from the reader's bundled WOFF2 files in
`backend/src/main/resources/META-INF/resources/reader/fonts/`; the SVGs contain the "P" as an
outline and render without any font installed.

## Files

| File | What | Used as |
|---|---|---|
| `gen.py` | Generator for everything below | — |
| `play-icon.svg`, `play-icon-512.png` | Store icon, full bleed (Play applies the mask) | `admin/androidApp/play/graphics/icon-512.png` |
| `feature-graphic-de.png`, `feature-graphic-en.png` | 1024×500 feature graphics, no alpha | `admin/androidApp/play/graphics/feature-1024x500-{de-DE,en-US}.png` |
| `favicon.svg` | Favicon | reader `/reader/icons/favicon.svg`, admin `/admin/favicon.svg` |
| `favicon.ico` | 16, 32 and 48 px | reader `/reader/icons/favicon.ico` and `/favicon.ico`, admin `/admin/favicon.ico` |
| `favicon-180.png` | Apple touch icon | reader `/reader/icons/apple-touch-icon.png` |
| `favicon-{16,32,48,192,512}.png` | Raster favicons for other uses | — |
| `android/ic_launcher_foreground.xml` | Adaptive icon foreground, 108 dp, sheet inside the 66 dp safe zone | `admin/androidApp/src/main/res/drawable/` |
| `android/ic_launcher_monochrome.xml` | Themed icon layer: the sheet with "P", rules, picture and text lines cut out | `admin/androidApp/src/main/res/drawable/` |

The launcher background is the colour `ic_launcher_background` (`#A8321D`) in
`admin/androidApp/src/main/res/values/colors.xml`. Forks replace the reader's favicons through their
theme directory (`deploy/theme/README.md`), not here.

## Regenerate, distribute, check

```sh
icons/build.sh          # regenerate every file in icons/
icons/sync.sh           # copy them into reader, admin web, Android and Play folders
icons/sync.sh --check   # list copies that are missing or differ; exit 1 if any
```

Run all three whenever the artwork changes and commit the sources, the generated files and the
copies together. Builds (Maven, Gradle, Docker) only use the copies and need none of the tools below.

`build.sh` creates a Python venv in `icons/.venv` (git-ignored) on first use. Tools used for the
current files:

- Python 3.14 with fontTools 4.66.1 and brotli 1.2.0 (WOFF2 glyph extraction)
- rsvg-convert 2.62.3 (SVG → PNG)
- ImageMagick 7.1.2 (`magick`: ICO, feature graphic crop and alpha removal)
- Google Chrome 147, `--headless=new` (feature graphic: HTML page with the reader fonts, screenshot)
