# Design guidelines

Sections 1, 2, 4 and 5 apply to the **reader** (server-rendered HTML, Qute). Section 3 applies to the **administration app** (Compose Multiplatform). Section 6 covers the printable account slip.

## 1. Newspaper page (front page / overviews)

- **Masthead** at the top: newspaper name (large, serif or display face), subtitle, date, issue number — most of the "newspaper feel" comes from it.
- **Section bar** right below (one colour per section as a small marker, not as a background).
- **Clear hierarchy**: exactly one **lead story** with a dominant image and the largest headline, spanning several columns; smaller stories below.
- **Modular grid**: stories are rectangles (no L-shapes). 12-column CSS grid on desktop, 6 on tablet, 1 on phone.
- **Headline hierarchy** (also used in the editor; German UI labels in parentheses): kicker (*Dachzeile*) → headline (*Schlagzeile*) → subheadline (*Unterzeile*) → lead (*Vorspann*) → body with subheads (*Zwischentitel*) → caption (*Bildunterschrift*) → byline (*Autorenzeile*).
- **Pull quotes and info boxes** break up text blocks.
- Thin rules between columns, plenty of white space, restrained colours — the children's pictures are the colour.
- **No advertising**, no placeholders for it.
- Online body text is **single-column**; multiple columns only for front-page cards and print views.

## 2. Readability for ages 6–16

| Rule | Value | Rationale |
|---|---|---|
| Body size | default 19 px; reader switch S/M/L/XL (17/19/22/26 px) on every page, without JavaScript (a form posts to `/text-size`, the choice lives in a cookie for a year); the newspaper's default is set in the admin app | BDA: 16–19 px. Younger readers benefit from larger type and shorter lines; for 10–11-year-olds the effect reverses → switchable instead of fixed |
| Line length | max. 60–70 characters (`max-width: 65ch`) | BDA |
| Line height | 1.5 | BDA, children's reading research |
| Letter spacing | slightly increased (`0.02em`, `0.04em` at XL) | children rate increased letter spacing as easiest to read |
| Body face | sans-serif with unambiguous shapes (I/l/1, a/ɑ): **Atkinson Hyperlegible** (default) or **Andika** (beginning readers) — both OFL, shipped as WOFF2 under `/reader/fonts/` with their licence texts; a face is only downloaded when used | BDA recommends sans-serif; no external font CDNs |
| Headline face | serif / display allowed (large enough) | newspaper character |
| Alignment | left-aligned, no justification, no automatic hyphenation | justification creates rivers; hyphenation slows beginning readers |
| Emphasis | bold, not italics; no ALL CAPS in body text | BDA |
| Contrast | WCAG AA (≥ 4.5:1); lightly tinted paper background instead of pure white | BDA, glare |
| Paragraphs | short; the editor gently flags very long paragraphs | |
| Reading aid | optional reading time and difficulty (Wiener Sachtextformel) shown to author and reader | German-language readability formula |
| Dark mode | yes, via `prefers-color-scheme` | |

## 3. Editor for ages 6–16 (administration app)

The editor is part of the Compose administration app (web first, Android/iOS later). It is **block-based**: one card per body block (paragraph, subhead, quote, bullet list) that can be added, moved and removed, which maps 1:1 to body format v1 and also suits `starter`. Bold inside paragraphs, quotes and list items uses the Compose library [`compose-rich-editor`](https://github.com/MohamedRejeb/compose-rich-editor) (Wasm-capable), which the app uses only behind its mapping between runs and editor text; subheads and header fields are plain text fields.

So far only `standard` is implemented; every user gets it and `presserl.editor.level` is not evaluated yet.

Levels (`presserl.editor.level`, overridable per user, default `standard`):

| Level | For | Tools |
|---|---|---|
| `starter` | approx. 6–9 | headline, text, image (+ caption), bold. Huge buttons with icon **and** word. One field at a time. |
| `standard` | approx. 10–13 | + kicker, subheadline, lead, subheads, quote, bullet list |
| `profi` | approx. 14–16 | + info box, image gallery, links, section/issue selection, keyboard shortcuts |

All levels:

- **Autosave** (no save button to forget); undo always visible.
- **Preview = the real newspaper view**: the preview opens the article in the reader with the deployment's theme.
- Spell check where the platform provides it (`lang` from the newspaper locale).
- One primary button bottom right: **Publish** (or **Submit** when an approval level applies — the server says which).
- Friendly, concrete messages ("Your picture is too big — I'll shrink it for you" instead of error codes). UI text lives in i18n resources.
- Touch targets ≥ 44 px; works on tablets and phones.
- The administration app uses the reader's colour and font tokens where it can, but is not themed by `custom.css`.

### Field help

Newsroom words (*Ressort*, *Dachzeile*, *Vorspann* …) mean nothing to a ten-year-old, so the editor explains them where they are used:

- A **`?` button** (≥ 44 px touch target, screen-reader label "What is the kicker?") sits next to every editor field and every block type label. Pointing at it, focusing it or tapping it opens the explanation; tapping again, Escape or tapping outside closes it. Only one explanation is open at a time; it never takes focus, changes the article or triggers a save.
- **Child-level text**: about age 10, one or two short sentences, "du" in German, saying what the part is *for* — no definitions from a style guide.
- **One shared sample article** (a cat that found a new home) shows every part in the reader's order; the explained part is highlighted by background, border and a `▶` marker, not by colour alone. The sample shows no person.
- **Images carry the image-rights hint**: ask every recognisable person (children: their parents too) first; otherwise choose another photo or have faces pixelated with an adult's help; use only own or permitted photos. It is a reminder, not legal advice.
- **Every new editor field or block type gets a help entry** (text, label and sample part, German and English); the `HelpPart` mapping test fails otherwise.

## 4. Print views

- Dedicated routes (`/print/article/:id`, `/print/issue/:id`) with `@media print` + `@page` (A4 portrait, margins, page numbers).
- **Article**: single column, large, with masthead header — for pinning on the wall.
- **Issue**: front page with masthead and lead story, then 2–3 columns; images never split across pages (`break-inside: avoid`); section headers.
- Browser "Print → Save as PDF" is sufficient; no server-side PDF in the MVP.
- Print colours: black on white; section colours only as rules.
- As built: `@page { size: A4 portrait; margin: 18mm 16mm 20mm }` with the page number in the
  `@bottom-center` margin box (`counter(page)`). Checked 2026-09-28: Chromium 140 prints the
  numbers; Firefox 141 drops the margin box (its CSSOM keeps `@page` without it). Where a browser lacks
  margin boxes, the page prints without a number; the browser's own header/footer option remains.
- Text in `pt` while printing (12pt, the issue's columns 10.5pt), independent of the reader's text
  size; colour tokens forced to black on white inside `@media print` and on the print sheet, so
  dark mode and fork colours never reach paper.
- The issue print view's first page (`.print-issue__front`: large masthead with issue line and the
  whole lead story) ends with a page break; the other articles follow in full inside
  `.print-columns`, each under a `.print-section-header` (section name in small caps, a top rule in
  the section colour). The number of columns is `--presserl-grid-columns` within
  `[data-view="print-issue"]`, default **2**; a fork sets 3 with
  `@media print { [data-view="print-issue"] { --presserl-grid-columns: 3; } }`.
- Figures and headline blocks (section header, kicker, headline, subheadline) never split
  (`break-inside: avoid`), headers stay with their text (`break-after: avoid`), `orphans`/`widows`
  3. Exact column balancing is out of scope.
- On screen a print view is a paper preview (white sheet, at most 210mm wide, shadow) with a
  screen-only toolbar: a "Drucken"/"Print" button (shown and bound by the same-origin
  `/reader/print.js`; without script the browser's print command still works) and a link back.
  Masthead tools, text-size switch, section bar, toolbar and `.print-link`s are never printed.

## 5. Theming via CSS in `deploy/` (reader)

- The default theme is built entirely on **CSS custom properties** (design tokens):

  ```css
  :root {
    --presserl-font-body: "Atkinson Hyperlegible", sans-serif;
    --presserl-font-headline: "Playfair Display", serif;
    --presserl-color-paper: #fbf8f1;
    --presserl-color-ink: #1d1b18;
    --presserl-color-muted: #5b5650;
    --presserl-color-accent: #a8321d;
    --presserl-color-rule: #d9d2c3;
    --presserl-text-size: 19px;
    --presserl-letter-spacing: 0.02em;
    --presserl-measure: 65ch;
    --presserl-grid-columns: 12;
    --presserl-section-red: #c62828;   /* … one per palette key: red, orange, yellow, green, teal, blue, purple, pink */
  }
  ```

  Dark mode redefines only the colour tokens inside `@media (prefers-color-scheme: dark)`. The server
  renders the effective text size as `<html data-text-size="s|m|l|xl">`; the theme maps it to
  `--presserl-text-size`, and all other sizes are relative to it.

- `deploy/theme/` is mounted into the `presserl` container and served by the reader at `/theme/`; `custom.css` is loaded **after** the default theme and may override tokens or whole views:

  ```css
  :root { --presserl-color-accent: #1f4e79; }
  [data-view="frontpage"] .masthead { text-align: left; }
  @media print { [data-view="print-issue"] { --presserl-grid-columns: 3; } }
  ```

- Public, stable styling API: tokens, `data-view` attributes (`frontpage`, `article`, `issues`, `issue`, `print-article`, `print-issue`, `not-found`) and these documented classes: `.masthead`, `.masthead__name`, `.masthead__subtitle`, `.masthead__issue` (issue label and date in the masthead), `.section-bar`, `.section-tag` (with `data-section-color="<key>"`), `.text-size-switch`, `.lead-article`, `.article-card`, `.kicker`, `.headline`, `.subheadline`, `.lead`, `.byline`, `.article`, `.article__body`, `.lead-image` (the lead image's `<figure>`), `.lead-image__caption` (its `<figcaption>`), `.note`, `.print-link` (links to a print view), `.print-toolbar` (screen-only print button and back link), `.print-columns` (the issue's articles after the first page), `.print-section-header` (section name above an article in a print view, with `data-section-color`). Everything else (other classes, tokens prefixed `--presserl-_`) is internal and may change.
- Front page: a grid of `--presserl-grid-columns` columns (12) on wide screens, 6 on tablets, 1 on phones; the lead story spans the full width, the other stories are equal cards separated by thin rules. A section bar below the masthead lists the sections with their colour markers; stories and articles show their section the same way.
- Custom fonts and images go into `deploy/theme/fonts/` and `deploy/theme/` — same origin, CSP stays `self`.
- Upstream ships the example themes *Classic*, *Colourful* (for younger kids) and *Night* in `deploy/theme/examples/` as templates to copy; `deploy/theme/custom.css` is a comment-only starter. Changes apply on the next page load (`Cache-Control: no-cache`).

## 6. Account slip (printable)

New accounts are handed over on paper — there is no e-mail.

- One slip per account, printed from the administration app right after creating the account or resetting its password; A4 with several slips to cut, or a single slip.
- Content: newspaper name (masthead style), web address, **username**, **password** and a **QR code** — nothing else, no role, no real name beyond the username.
- Password in a large monospace face, words separated by dashes and easy to read aloud (`tiger-wolke-apfel-leiter`); no ambiguous characters because the word list has no umlauts or ß.
- A short friendly line for the child ("Log in with these details." — UI text via i18n) and a hint to keep the slip safe.
- Black on white, no images besides the QR code; a dashed cut line around the slip.
- QR code: encodes `<web address>/qr?u=<username>#pw=<password>` (error correction level M). Scanned with the phone camera it opens the newspaper's login with the username filled in; the mobile app (M8) reads address, username and password from it. Printed about 35 mm wide, black on white with a quiet zone of four modules, to the right of the details, with a short line below ("Scan the code to open the login." — i18n). The code holds the password, so the slip stays as secret as before.

## Sources

- British Dyslexia Association: [Dyslexia Style Guide 2023](https://cdn.bdadyslexia.org.uk/uploads/documents/Advice/style-guide/BDA-Style-Guide-2023.pdf?v=1680514568)
- Katzir et al.: [The Effect of Font Size on Reading Comprehension on Second and Fifth Grade Children: Bigger Is Not Always Better](https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0074061) (PLOS ONE)
- Wilkins et al.: [Typography for children may be inappropriately designed](https://www.researchgate.net/publication/227732089_Typography_for_children_may_be_inappropriately_designed)
- [Effects of Increased Letter Spacing on Digital Text Reading … in Young Readers](https://www.researchgate.net/publication/396131874_Effects_of_Increased_Letter_Spacing_on_Digital_Text_Reading_Comprehension_Calibration_and_Preferences_in_Young_Readers)
- [Legible Typography — Overview of research](https://legible-typography.com/en/6-overview-of-research-typography)
- Nielsen Norman Group: [UX Design for Children (Ages 3–12)](https://www.nngroup.com/reports/children-on-the-web/), [Design for Kids Based on Their Stage of Physical Development](https://www.nngroup.com/articles/children-ux-physical-development/)
- Newspaper layout: [Front Page Design](https://fiveable.me/advanced-editorial-design/unit-8/front-page-design/study-guide/dSvIC2IxtDxtSYw9), [Newspaper Grid Structures](https://fiveable.me/advanced-editorial-design/unit-8/newspaper-grid-structures/study-guide/6Rc2XECLdNLLHdmx)
