## MODIFIED Requirements

### Requirement: Article bodies render to fixed, escaped HTML
The reader SHALL render a body in format version 1 by mapping each block to fixed HTML only:
`paragraph` to a paragraph, `subhead` to a subheading below the headline level, `quote` to a
block quotation, `list` to a bullet list with one item per entry, and `image` to a figure with
class `article__figure` containing an `<img>` whose `src` is `/media/{id}/web?v={version}`, whose
`srcset` offers the `thumbnail` and `web` renditions with their widths, whose `width` and `height`
attributes are those of the `web` rendition, whose `alt` is the caption and which loads lazily,
followed by `<figcaption class="article__caption">` when the caption is not empty. An image block
whose media no longer exists or whose `web` and `thumbnail` renditions are not produced yet SHALL be
left out. Within runs, `bold: true` SHALL render as strong emphasis and a line feed as a line break.
All text — in the body, in captions and in every other article field — SHALL be HTML-escaped so
that markup characters appear literally and are never interpreted.

#### Scenario: All block types
- **WHEN** the live body contains a paragraph with a bold run, a subhead, a quote, a two-item list and an image block
- **THEN** the article page contains, in that order, a paragraph with a `<strong>` run, an `<h2>`, a `<blockquote>`, a `<ul>` with two `<li>` and a `figure.article__figure`

#### Scenario: Image block with caption
- **WHEN** the live body contains the image block of media 18 at version 0 with caption `The finish line` between two paragraphs
- **THEN** the page shows, between the two paragraphs, a `figure.article__figure` with an image from `/media/18/web?v=0`, `/media/18/thumbnail?v=0` in its `srcset`, and the figcaption `The finish line`

#### Scenario: Image block without caption
- **WHEN** the image block has no caption
- **THEN** the figure contains the image and no `figcaption`

#### Scenario: Caption with markup
- **WHEN** an image block's caption is `<b>Finish</b>`
- **THEN** the page shows the caption as text and contains no `<b>` element from it

#### Scenario: Line break inside a paragraph
- **WHEN** a paragraph run's text is `one\ntwo`
- **THEN** the paragraph renders `one`, a `<br>` and `two`

#### Scenario: Markup is shown as text
- **WHEN** the live headline is `<b>Hi</b>` and a paragraph run is `<script>alert(1)</script>`
- **THEN** the page contains `&lt;b&gt;Hi&lt;/b&gt;` and `&lt;script&gt;alert(1)&lt;/script&gt;` and no `<script>` element from the article

#### Scenario: Unpublished body image
- **WHEN** the live body has no image block and a newer working revision adds one with media 18
- **THEN** the article page never references `/media/18/`
