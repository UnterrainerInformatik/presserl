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
| Body size | default 19 px; reader switch S/M/L/XL (17/19/22/26 px) | BDA: 16–19 px. Younger readers benefit from larger type and shorter lines; for 10–11-year-olds the effect reverses → switchable instead of fixed |
| Line length | max. 60–70 characters (`max-width: 65ch`) | BDA |
| Line height | 1.5 | BDA, children's reading research |
| Letter spacing | slightly increased (`0.02em`, `0.04em` at XL) | children rate increased letter spacing as easiest to read |
| Body face | sans-serif with unambiguous shapes (I/l/1, a/ɑ): **Atkinson Hyperlegible** (default) or **Andika** (beginning readers) — both OFL, self-hosted | BDA recommends sans-serif; no external font CDNs |
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

## 4. Print views

- Dedicated routes (`/print/article/:id`, `/print/issue/:id`) with `@media print` + `@page` (A4 portrait, margins, page numbers).
- **Article**: single column, large, with masthead header — for pinning on the wall.
- **Issue**: front page with masthead and lead story, then 2–3 columns; images never split across pages (`break-inside: avoid`); section headers.
- Browser "Print → Save as PDF" is sufficient; no server-side PDF in the MVP.
- Print colours: black on white; section colours only as rules.

## 5. Theming via CSS in `deploy/` (reader)

- The default theme is built entirely on **CSS custom properties** (design tokens):

  ```css
  :root {
    --presserl-font-body: "Atkinson Hyperlegible", sans-serif;
    --presserl-font-headline: "Playfair Display", serif;
    --presserl-color-paper: #fbf8f1;
    --presserl-color-ink: #1d1d1b;
    --presserl-color-accent: #b3261e;
    --presserl-text-size: 19px;
    --presserl-measure: 65ch;
    --presserl-grid-columns: 12;
  }
  ```

- `deploy/theme/` is mounted into the `presserl` container and served by the reader at `/theme/`; `custom.css` is loaded **after** the default theme and may override tokens or whole views:

  ```css
  :root { --presserl-color-accent: #1f4e79; }
  [data-view="frontpage"] .masthead { text-align: left; }
  @media print { [data-view="print-issue"] { --presserl-grid-columns: 3; } }
  ```

- Public, stable styling API: tokens, `data-view` attributes and documented classes (`.masthead`, `.lead-article`, `.article-card`, `.byline`, `.kicker`, `.section-bar`, …). Everything else is internal and may change.
- Custom fonts and images go into `deploy/theme/fonts/` and `deploy/theme/` — same origin, CSP stays `self`.
- Upstream ships 2–3 example themes (*Classic*, *Colourful* for younger kids, *Night*) as templates to copy.

## 6. Account slip (printable)

New accounts are handed over on paper — there is no e-mail.

- One slip per account, printed from the administration app right after creating the account or resetting its password; A4 with several slips to cut, or a single slip.
- Content: newspaper name (masthead style), web address, **username**, **password** — nothing else, no role, no real name beyond the username.
- Password in a large monospace face, words separated by dashes and easy to read aloud (`tiger-wolke-apfel-leiter`); no ambiguous characters because the word list has no umlauts or ß.
- A short friendly line for the child ("Log in with these details." — UI text via i18n) and a hint to keep the slip safe.
- Black on white, no images needed; a dashed cut line around the slip.
- Room reserved for a QR code (M8), which replaces typing the address and password.

## Sources

- British Dyslexia Association: [Dyslexia Style Guide 2023](https://cdn.bdadyslexia.org.uk/uploads/documents/Advice/style-guide/BDA-Style-Guide-2023.pdf?v=1680514568)
- Katzir et al.: [The Effect of Font Size on Reading Comprehension on Second and Fifth Grade Children: Bigger Is Not Always Better](https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0074061) (PLOS ONE)
- Wilkins et al.: [Typography for children may be inappropriately designed](https://www.researchgate.net/publication/227732089_Typography_for_children_may_be_inappropriately_designed)
- [Effects of Increased Letter Spacing on Digital Text Reading … in Young Readers](https://www.researchgate.net/publication/396131874_Effects_of_Increased_Letter_Spacing_on_Digital_Text_Reading_Comprehension_Calibration_and_Preferences_in_Young_Readers)
- [Legible Typography — Overview of research](https://legible-typography.com/en/6-overview-of-research-typography)
- Nielsen Norman Group: [UX Design for Children (Ages 3–12)](https://www.nngroup.com/reports/children-on-the-web/), [Design for Kids Based on Their Stage of Physical Development](https://www.nngroup.com/articles/children-ux-physical-development/)
- Newspaper layout: [Front Page Design](https://fiveable.me/advanced-editorial-design/unit-8/front-page-design/study-guide/dSvIC2IxtDxtSYw9), [Newspaper Grid Structures](https://fiveable.me/advanced-editorial-design/unit-8/newspaper-grid-structures/study-guide/6Rc2XECLdNLLHdmx)
