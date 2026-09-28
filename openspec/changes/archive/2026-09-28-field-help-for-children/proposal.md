## Why

The editor's field names — *Ressort*, *Dachzeile*, *Schlagzeile*, *Unterzeile*, *Vorspann*,
*Titelbild*, *Bildunterschrift* and the block types *Absatz*, *Zwischentitel*, *Zitat*,
*Aufzählung* — are newsroom jargon that means nothing to a ten-year-old reporter. Children guess,
fill the wrong field or ask an adult every time. The lead image additionally carries a real
responsibility children do not know about: the right to one's own image (*Recht am eigenen Bild*)
and whose photo it is.

## What Changes

- Every editor field and every body block type gets a small question-mark button next to its
  label. Hovering it (pointer), focusing it (keyboard) or tapping/clicking it (touch) opens a short,
  child-friendly explanation (reading level about age 10, one or two sentences).
- Every explanation shows the same sample article (section, kicker, headline, subheadline, lead
  image with caption, lead and a short body with one block of each type), with the part being
  explained highlighted, so the child sees each part's role in context.
- The lead-image explanation additionally explains, in children's words, what to watch out for:
  ask every person who can be recognised in the photo (for children: also their parents) before
  using it; if they say no or cannot be asked, choose another photo or make the faces
  unrecognisable (pixelate); only use photos you took yourself or may use.
- All texts in German and English via the admin resources.
- Accessible: the button has a screen-reader label naming the field, Escape and clicking outside
  close the explanation, focus returns to the button.
- Found while verifying the help on real screens, fixed here because the help depends on them:
  keyboard use stays alive after tabbing out of a text field (Compose web dropped browser focus
  to the page body, so no key reached the app any more), and the header no longer collapses at
  phone width (two rows below about 720 dp, navigation scrolls horizontally), nor does the
  editor's bottom bar.
- A short section in `docs/design-guidelines.md` describing the field-help pattern and its tone.
- The backlog entry in `ai/open-proposals.md` is removed.

## Non-goals

- No help for fields outside the article editor (accounts, sections, issues, newspaper settings).
- No in-app pixelation or cropping tool — that is the separate "Media view with crop and blur"
  proposal; the lead-image help only tells the child to make faces unrecognisable and, until the
  tool exists, to ask an adult for help with it.
- No legal advice or complete statement of image or copyright law; the texts are a child-level
  reminder, not a consent workflow. No consent tracking, no upload blocking.
- No per-newspaper customisable help texts; no age-level-dependent variants.
- No changes to the REST contract, backend or reader.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `admin-shell`: new requirements "Keyboard use survives leaving a text field" and "Header and
  editor bar fit narrow screens".

- `admin-articles`: new requirement "Field explanations for children" — question-mark help with
  child-friendly text and the highlighted sample article for every editor field and block type,
  including the image-rights hint on the lead image.

## Impact

- **admin**: `ui/editor/EditorScreen.kt` (help button next to every field and block label),
  a new help composable and sample-article model under `ui/editor/`, German and English strings in
  `composeResources/values*/strings.xml`, Kotlin tests.
- **admin shell**: `ui/App.kt` (narrow header), `ui/editor/EditorScreen.kt` (narrow bottom bar), `wasmJsMain/.../Main.kt` (browser focus guard),
  a browser test for the guard.
- **docs**: `docs/design-guidelines.md` gets a "Field help" section.
- **backend, reader, deploy**: not affected. No REST contract change, so `ai/primer/endpoints.md`
  and `http/` stay unchanged.
