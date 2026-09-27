# reader-theme Specification

## Purpose

Gives the reader its newspaper look — a default theme built on public design tokens, self-hosted
fonts, dark mode, a modular front page and a section bar — and lets a fork restyle it through a
theme directory without touching code.

## Requirements

### Requirement: Default theme on public design tokens
Every reader page SHALL load the built-in default theme, which defines on `:root` at least these
public tokens and uses them for all fonts, colours, text size, line length and front-page
columns: `--presserl-font-body`, `--presserl-font-headline`, `--presserl-color-paper`,
`--presserl-color-ink`, `--presserl-color-muted`, `--presserl-color-accent`,
`--presserl-color-rule`, `--presserl-text-size`, `--presserl-letter-spacing`,
`--presserl-measure`, `--presserl-grid-columns` and `--presserl-section-<key>` for each section
palette key (`red`, `orange`, `yellow`, `green`, `teal`, `blue`, `purple`, `pink`). Overriding a
token in a later stylesheet SHALL change every place that uses it.

The defaults SHALL follow the readability rules for children: body face Atkinson Hyperlegible,
headline face Playfair Display, body text 19 px (text size M) with line height 1.5 and letter
spacing `0.02em`, reading column at most `65ch`, left-aligned text without justification or
automatic hyphenation, emphasis in bold (no italics or all-caps body text), a lightly tinted
paper background instead of pure white, and a contrast of body text and muted text against the
paper of at least 4.5:1 (WCAG AA).

#### Scenario: Tokens are defined
- **WHEN** a client fetches the default reader stylesheet
- **THEN** it defines every public token listed above on `:root`

#### Scenario: Fork overrides the accent colour
- **WHEN** `custom.css` sets `:root { --presserl-color-accent: #1f4e79; }`
- **THEN** every element of the reader that uses the accent colour shows `#1f4e79`

#### Scenario: Quotes are not italic
- **WHEN** an article body contains a quote
- **THEN** the quote is set upright, distinguished by a rule and weight rather than italics

### Requirement: Self-hosted fonts
The fonts of the default theme — Atkinson Hyperlegible (regular, bold), Playfair Display (bold)
and Andika (regular, bold) as an alternative body face for beginning readers — SHALL be served by
the reader from its own origin as WOFF2 together with their SIL Open Font License texts. Reader
pages and stylesheets SHALL reference no font, stylesheet or other resource of another origin. A
browser SHALL only download a font face that the page actually uses.

#### Scenario: Font is served from the same origin
- **WHEN** a client requests the Atkinson Hyperlegible regular font file referenced by the default theme
- **THEN** the response is `200` with content type `font/woff2`

#### Scenario: No external origin
- **WHEN** the default theme and every reader page are inspected
- **THEN** they contain no absolute URL of another origin and the `Content-Security-Policy` is unchanged (`'self'` only)

### Requirement: Dark mode follows the operating system
When the browser reports `prefers-color-scheme: dark`, the default theme SHALL switch to a dark
colour scheme by redefining the colour tokens only (paper, ink, muted, accent, rule, section
colours), keeping a contrast of at least 4.5:1 for body and muted text. Without that preference
the light scheme SHALL apply. A fork overriding colour tokens in `custom.css` SHALL be able to
override the dark values in its own `prefers-color-scheme: dark` media query.

#### Scenario: Dark system setting
- **WHEN** a browser with `prefers-color-scheme: dark` opens the front page
- **THEN** the page is shown with a dark paper colour and light ink

#### Scenario: Light system setting
- **WHEN** a browser with `prefers-color-scheme: light` opens the front page
- **THEN** the page is shown with the tinted light paper colour and dark ink

### Requirement: Fork theme directory served at /theme/
The reader SHALL serve the files of the configured theme directory (deployment setting
`presserl.theme.dir`, default `/deployments/theme`) read-only under `/theme/<path>`, without
login and also for a private newspaper. Only files with the extensions `css`, `woff2`, `woff`,
`ttf`, `otf`, `png`, `jpg`, `jpeg`, `gif`, `webp`, `svg` and `ico` SHALL be served, with the
matching content type; every other path, a missing file, a directory, and any path that resolves
outside the theme directory SHALL be answered with `404`. Responses SHALL require revalidation
(`Cache-Control: no-cache`) so that a changed theme applies on the next page load. A missing
theme directory SHALL NOT prevent startup.

#### Scenario: Custom stylesheet is served
- **WHEN** the theme directory contains `custom.css` and a client requests `GET /theme/custom.css`
- **THEN** the response is `200` with content type `text/css`, the file's content and `Cache-Control: no-cache`

#### Scenario: Fork font
- **WHEN** the theme directory contains `fonts/comic.woff2` and a client requests `GET /theme/fonts/comic.woff2`
- **THEN** the response is `200` with content type `font/woff2`

#### Scenario: Path escape
- **WHEN** a client requests `/theme/../application.properties` or `/theme/%2e%2e/%2e%2e/etc/passwd`
- **THEN** the response is `404` and reveals no file outside the theme directory

#### Scenario: Type not allowed
- **WHEN** the theme directory contains `notes.txt` and a client requests `GET /theme/notes.txt`
- **THEN** the response is `404`

### Requirement: Fork stylesheet is loaded after the default theme
When the theme directory contains a file `custom.css`, every reader page SHALL link
`/theme/custom.css` after the default theme's stylesheet, so its rules and tokens win. When the
file does not exist, reader pages SHALL NOT link it. Adding or removing the file SHALL take
effect on the next page load without a restart.

#### Scenario: Fork theme present
- **WHEN** the theme directory contains `custom.css` and a visitor requests `GET /`
- **THEN** the page links the default theme stylesheet and then `/theme/custom.css`

#### Scenario: No fork theme
- **WHEN** the theme directory has no `custom.css` and a visitor requests `GET /`
- **THEN** the page links only the default theme stylesheet

### Requirement: Stable styling API for forks
The reader SHALL expose a public styling API consisting of the public tokens, the `data-view`
attribute on `<main>` of every reader view (`frontpage`, `article`, `not-found`) and these
documented classes, which SHALL stay stable across releases: `.masthead`, `.masthead__name`,
`.masthead__subtitle`, `.section-bar`, `.section-tag`, `.text-size-switch`, `.lead-article`,
`.article-card`, `.kicker`, `.headline`, `.subheadline`, `.lead`, `.byline`, `.article`,
`.article__body`, `.note`. Each section tag SHALL carry `data-section-color="<key>"`. Other markup
details are internal and MAY change.

#### Scenario: Front page carries the documented hooks
- **WHEN** a visitor requests `GET /` of a newspaper with published articles
- **THEN** `<main>` has `data-view="frontpage"`, the masthead has class `masthead`, the lead story has class `lead-article` and the other stories have class `article-card`

#### Scenario: View-specific override
- **WHEN** `custom.css` contains `[data-view="frontpage"] .masthead { text-align: left; }`
- **THEN** the front page masthead is left-aligned while the article page masthead keeps the default alignment

### Requirement: Modular front-page grid
The front page SHALL lay out stories on a grid of `--presserl-grid-columns` columns (12 by
default) on wide screens, 6 columns on tablet widths and a single column on phone widths. The lead
story SHALL span the full width with the largest headline; the other stories SHALL be rectangular
cards of equal width in a row, separated by thin rules. Online body text on the article page
SHALL stay single-column.

#### Scenario: Desktop
- **WHEN** the front page with one lead story and six other stories is shown 1280 px wide
- **THEN** the lead story spans all columns and the other stories are shown three per row

#### Scenario: Phone
- **WHEN** the same front page is shown 375 px wide
- **THEN** all stories are stacked in a single column without horizontal scrolling

### Requirement: Section bar below the masthead
Below the masthead every reader page SHALL show a section bar listing all sections in their
position order, each with its name and a small marker in its section colour (not as a background
of the bar). The section names SHALL NOT be links yet. The section bar SHALL NOT be shown when
the page lists no content because the newspaper is private and the visitor is anonymous or not
entitled, and SHALL NOT be shown when no section exists.

#### Scenario: Public newspaper with sections
- **WHEN** the sections `General` (red) and `Sport` (blue) exist in that order and a visitor requests `GET /`
- **THEN** below the masthead a section bar shows `General` with a red marker followed by `Sport` with a blue marker

#### Scenario: Private newspaper, anonymous visitor
- **WHEN** the visibility is `private` and an anonymous visitor requests `GET /`
- **THEN** the page shows no section bar and no section name
