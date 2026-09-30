# reader-articles Specification

## Purpose

Shows published articles to readers: the front-page article list and the article page, both
rendering only the live revision, with article bodies turned into safe HTML and no content
exposed while the newspaper is private.

## Requirements

### Requirement: Front page lists published articles
When the effective `visibility` is `public`, the front page (`GET /`) SHALL list, below the
masthead, the articles visible to readers, at most 30, in this order: first the articles with a
front-page weight, lowest weight first; then those without one, ordered by first publication,
newest first. Ties are broken by first publication, newest first, and then by the higher article
id. The first article SHALL be shown as the lead story, the others as cards. Each entry SHALL show
the live revision's kicker (if not empty), headline, lead (if not empty), the byline and the
publication date, and SHALL link to `/articles/{id}`. Articles that are `DRAFT` or `OFFLINE`, or not
visible to readers for another reason, SHALL NOT appear, whatever their weight. When no article is
listed, the page SHALL show a note that there are no articles yet.

#### Scenario: Newest publication leads
- **WHEN** article A was published before article B, both are `PUBLISHED` in a published issue and neither has a weight
- **THEN** `GET /` shows B as lead story, followed by A, each linking to its article page

#### Scenario: Weighted article leads
- **WHEN** articles A (weight 1), B (weight 2) and C (no weight, published after A and B) are `PUBLISHED` in a published issue
- **THEN** `GET /` shows A as lead story, then B, then C

#### Scenario: Equal weights
- **WHEN** A and B both have weight 1, are `PUBLISHED` in a published issue, and B was published after A
- **THEN** B comes before A

#### Scenario: Weight of an offline article has no effect
- **WHEN** article A has weight 1 and is `OFFLINE`
- **THEN** `GET /` does not list A and the other articles keep their order

#### Scenario: Weight of a waiting article has no effect
- **WHEN** article A has weight 1 and is `PUBLISHED` in an issue that is not published
- **THEN** `GET /` does not list A and the other articles keep their order

#### Scenario: Offline and draft articles are hidden
- **WHEN** one article is `PUBLISHED`, one `OFFLINE` and one `DRAFT`, all in a published issue
- **THEN** `GET /` lists only the published one

#### Scenario: Unpublished changes do not leak
- **WHEN** a published article with live headline `Old` in a published issue has a newer unpublished revision with headline `New`
- **THEN** `GET /` shows `Old` and does not contain `New`

#### Scenario: Empty newspaper
- **WHEN** no article is visible to readers and a browser preferring German requests `GET /`
- **THEN** the response is `200` with the masthead and a note that there are no articles yet

#### Scenario: More than 30 articles
- **WHEN** 31 articles are `PUBLISHED` in published issues, none weighted
- **THEN** `GET /` lists the 30 most recently first-published ones

#### Scenario: Weighted article beyond the newest 30
- **WHEN** 31 articles are `PUBLISHED` in published issues and only the oldest has a weight
- **THEN** `GET /` lists the weighted one first and the 29 most recently first-published others after it

### Requirement: Article page renders the live revision
The system SHALL serve `GET /articles/{id}` as server-rendered HTML (`text/html; charset=UTF-8`)
with `<main>` carrying `data-view="article"`, working without JavaScript. For a `PUBLISHED`
article it SHALL show the masthead linking back to `/`, then the live revision's kicker,
headline, subheadline and lead (empty ones omitted), the byline with the author's display name
(the username when the display name is empty), the date of first publication and — when the live
revision was published on a later day — the date of that update, followed by the body. The page
title SHALL contain the headline and the newspaper name.

#### Scenario: Published article
- **WHEN** a visitor requests the article page of a published article with headline `Hello` by `Anna`
- **THEN** the response is `200` HTML with `data-view="article"`, the headline `Hello`, a byline naming `Anna` and the publication date

#### Scenario: Republished with changes
- **WHEN** an article first published on 2026-09-20 was edited and republished on 2026-09-25
- **THEN** the article page shows the content of the new live revision, the date 2026-09-20 and an update date 2026-09-25

#### Scenario: Unpublished changes stay hidden
- **WHEN** a published article has a newer unpublished revision
- **THEN** the article page shows only the live revision's content

### Requirement: Only published articles are reachable
`GET /articles/{id}` SHALL answer with an HTML `404` page when the id is unknown, not a number,
or the article is not visible to readers (`DRAFT`, `SUBMITTED`, `OFFLINE`, or `PUBLISHED` in an
issue that is not published or in no issue). The `404` page SHALL show the masthead and a link to
the front page and SHALL NOT reveal whether the article exists.

#### Scenario: Draft
- **WHEN** a visitor requests the article page of a draft
- **THEN** the response is `404` HTML with a link to `/`

#### Scenario: Offline article
- **WHEN** a visitor requests the article page of an article that was taken offline
- **THEN** the response is `404` and contains none of its content

#### Scenario: Waiting for its issue
- **WHEN** a visitor requests the article page of a `PUBLISHED` article whose issue is not published
- **THEN** the response is the same `404` page as for a draft and contains none of its content

#### Scenario: Unknown or malformed id
- **WHEN** a visitor requests `/articles/999999` or `/articles/abc`
- **THEN** the response is `404` HTML with the same page as for a draft

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

### Requirement: Private newspaper shows no articles without login
When the effective `visibility` is `private`, what a visitor sees SHALL depend on their reader
session and roles:

- **Anonymous visitor**: the front page SHALL show only the masthead, a note that the newspaper is
  private and a link to `/login`, listing no article. Every `GET /articles/{id}` — whatever the id
  and the article's status — SHALL redirect to `/login?next=/articles/{id}`, so the response does
  not reveal whether the article exists.
- **Logged in and entitled** (`READER`, `EDITOR_IN_CHIEF` or `PUBLISHER`): the front page and the
  article pages SHALL behave exactly as for a public newspaper, including the `404` page for
  unknown or unpublished articles.
- **Logged in but not entitled**: the front page SHALL show only the masthead and a note that the
  account has no access to this newspaper, listing no article, and every `GET /articles/{id}`
  SHALL be answered with the `404` page.

#### Scenario: Private front page
- **WHEN** the visibility is `private`, an article is `PUBLISHED` and an anonymous visitor requests `GET /`
- **THEN** the response is `200` with the masthead, the private note and a link to `/login`, and does not contain the article's headline

#### Scenario: Private article page
- **WHEN** the visibility is `private` and an anonymous visitor requests the page of a published article
- **THEN** the response redirects to `/login?next=/articles/{id}` and contains none of the article's content

#### Scenario: Unknown article of a private newspaper
- **WHEN** the visibility is `private` and an anonymous visitor requests `/articles/999999`
- **THEN** the response is the same redirect to the login as for a published article

#### Scenario: Entitled reader
- **WHEN** the visibility is `private`, an article is `PUBLISHED` and the logged-in reader `oma` (group `reader`) requests `GET /` and the article page
- **THEN** the front page lists the article and the article page shows its live revision

#### Scenario: Entitled reader and a draft
- **WHEN** the visibility is `private` and the logged-in reader `oma` requests the page of a draft
- **THEN** the response is the `404` page

#### Scenario: Account without newspaper role
- **WHEN** the visibility is `private`, an article is `PUBLISHED` and a logged-in user without any newspaper group requests `GET /` and the article page
- **THEN** the front page shows the no-access note without the headline, and the article page answers `404`

### Requirement: Stories and articles show their section
Every story on the front page, on issue pages and the article page SHALL show the name of the
article's section together with a marker in the section's colour. The section name SHALL be a link
to `/?section=<section id>`, except on the front page filtered by that very section, where it SHALL
link to `/` and be marked as the active section (`aria-current="page"`). The same SHALL apply to the
entries of the section bar.

#### Scenario: Story in a section
- **WHEN** a published article in a published issue belongs to section `Sport` with colour `blue` and a visitor requests `GET /`
- **THEN** the article's story shows `Sport` with a marker using the `blue` section colour, linked to `/?section=<id of Sport>`

#### Scenario: Article page
- **WHEN** a visitor opens the article page of a published article in section `Sport` in a published issue
- **THEN** the page shows `Sport` with its colour marker above the headline, linked to `/?section=<id of Sport>`

#### Scenario: Active section links back
- **WHEN** a visitor requests `/?section=<id of Sport>`
- **THEN** every `Sport` tag and the `Sport` entry of the section bar link to `/` and carry `aria-current="page"`, and the other sections link to their own filter

### Requirement: Lead image on the article page
When the live revision of a published article has a lead image, the article page SHALL show it
after the headline block (kicker, headline, subheadline) and before the lead, as a figure with
class `lead-image` containing an `<img>` whose `src` is `/media/{id}/web`, whose `srcset` offers
the `thumbnail` and `web` renditions with their widths, and whose `width` and `height` attributes
are those of the `web` rendition. A non-empty caption SHALL be shown below the image as
`<figcaption class="lead-image__caption">`, escaped like all other text. Without a lead image no
figure SHALL be rendered. Only the live revision's lead image SHALL ever appear.

#### Scenario: Article with lead image and caption
- **WHEN** a visitor opens a published article whose live revision has media 17 as lead image with caption `Our cat Minka`
- **THEN** the page contains a `figure.lead-image` with an image from `/media/17/web` and the figcaption `Our cat Minka`

#### Scenario: Caption with markup
- **WHEN** the caption is `<b>Minka</b>`
- **THEN** the page shows the caption as text and contains no `<b>` element from it

#### Scenario: Unpublished image change
- **WHEN** the live revision has lead image 17 and a newer working revision has lead image 18
- **THEN** the article page shows media 17 and never references `/media/18/`

### Requirement: Lead images on the front page
On the front page the lead story SHALL show its live revision's lead image (if any) using the
`web` rendition, and every other story card SHALL show its lead image (if any) using the
`thumbnail` rendition, each with `width` and `height` of the rendition used and the caption as
`figcaption` when it is not empty. Stories without a lead image SHALL be rendered as before.

#### Scenario: Lead story with image
- **WHEN** the newest published article has lead image 17
- **THEN** the lead story contains an image from `/media/17/web`

#### Scenario: Card with image
- **WHEN** an older published article has lead image 18
- **THEN** its card contains an image from `/media/18/thumbnail`

### Requirement: Readers see articles of live issues only
An article SHALL be **visible to readers** exactly when its status is `PUBLISHED` and it belongs to
an issue that is published. Every reader page, list and route that shows or serves an article or
its media SHALL show only articles visible to readers. A `PUBLISHED` article in an issue that is not
published, or in no issue, SHALL be treated like a draft.

#### Scenario: Collected in a planned issue
- **WHEN** issue 1 is published, issue 2 exists and is not published, and a new article is published and therefore appended to issue 2
- **THEN** `GET /` does not list it and `GET /articles/{id}` answers `404`

#### Scenario: Issue goes live
- **WHEN** issue 2 of the previous scenario is published
- **THEN** `GET /` lists the article and its article page shows it

#### Scenario: Blog mode
- **WHEN** issue 1 is the only issue and is published, and a new article is published
- **THEN** the article is listed on `GET /` at once

#### Scenario: Issue taken back
- **WHEN** a published issue holding article A is unpublished
- **THEN** A stays `PUBLISHED` but is no longer listed and its article page answers `404`

#### Scenario: Article without issue
- **WHEN** a `PUBLISHED` article belongs to no issue
- **THEN** it is not listed on `GET /` and its article page answers `404`

### Requirement: Front page filtered by section
`GET /?section=<id>` SHALL render the front page with only the articles of that section that are
visible to readers, in the order and layout of the unfiltered front page (lead story first, at most
30), under the same visibility and access rules as `GET /`. When the section has no such article,
the page SHALL show a note that the section has no articles yet. A malformed or unknown section id
SHALL get the reader's `404` page. Any other query parameter SHALL be ignored.

#### Scenario: Only Sport
- **WHEN** Sport holds published articles A and B and Kultur holds C, all in a published issue, and a visitor requests `/?section=<id of Sport>`
- **THEN** the page lists A and B and not C

#### Scenario: Weight inside the filter
- **WHEN** Sport's article B has weight 3 and A has none, both published in a published issue
- **THEN** `/?section=<id of Sport>` shows B as lead story

#### Scenario: Waiting articles stay hidden in the filter
- **WHEN** Sport's only published article belongs to an issue that is not published
- **THEN** `/?section=<id of Sport>` shows the note that Sport has no articles yet

#### Scenario: Empty section
- **WHEN** Kultur has no published article and a German browser requests `/?section=<id of Kultur>`
- **THEN** the response is `200` with a note that Kultur has no articles yet

#### Scenario: Unknown section
- **WHEN** a visitor requests `/?section=999999` and no such section exists
- **THEN** the response is the `404` page

#### Scenario: Private newspaper
- **WHEN** the newspaper is private and an anonymous visitor requests `/?section=<id>`
- **THEN** the page shows the private note and the login link, as `GET /` does, and lists no article
