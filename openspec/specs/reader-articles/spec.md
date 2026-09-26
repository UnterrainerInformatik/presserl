# reader-articles Specification

## Purpose

Shows published articles to readers: the front-page article list and the article page, both
rendering only the live revision, with article bodies turned into safe HTML and no content
exposed while the newspaper is private.

## Requirements

### Requirement: Front page lists published articles
When the effective `visibility` is `public`, the front page (`GET /`) SHALL list, below the
masthead, the articles whose status is `PUBLISHED`, ordered by first publication, newest first
(ties broken by the higher article id), at most 30. The first article SHALL be shown as the lead
story, the others as cards. Each entry SHALL show the live revision's kicker (if not empty),
headline, lead (if not empty), the byline and the publication date, and SHALL link to
`/articles/{id}`. Articles that are `DRAFT` or `OFFLINE` SHALL NOT appear. When no article is
listed, the page SHALL show a note that there are no articles yet.

#### Scenario: Newest publication leads
- **WHEN** article A was published before article B and both are `PUBLISHED`
- **THEN** `GET /` shows B as lead story, followed by A, each linking to its article page

#### Scenario: Offline and draft articles are hidden
- **WHEN** one article is `PUBLISHED`, one `OFFLINE` and one `DRAFT`
- **THEN** `GET /` lists only the published one

#### Scenario: Unpublished changes do not leak
- **WHEN** a published article with live headline `Old` has a newer unpublished revision with headline `New`
- **THEN** `GET /` shows `Old` and does not contain `New`

#### Scenario: Empty newspaper
- **WHEN** no article is `PUBLISHED` and a browser preferring German requests `GET /`
- **THEN** the response is `200` with the masthead and a note that there are no articles yet

#### Scenario: More than 30 articles
- **WHEN** 31 articles are `PUBLISHED`
- **THEN** `GET /` lists the 30 most recently first-published ones

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
or the article is `DRAFT`, `SUBMITTED` or `OFFLINE`. The `404` page SHALL show the masthead and a
link to the front page and SHALL NOT reveal whether the article exists.

#### Scenario: Draft
- **WHEN** a visitor requests the article page of a draft
- **THEN** the response is `404` HTML with a link to `/`

#### Scenario: Offline article
- **WHEN** a visitor requests the article page of an article that was taken offline
- **THEN** the response is `404` and contains none of its content

#### Scenario: Unknown or malformed id
- **WHEN** a visitor requests `/articles/999999` or `/articles/abc`
- **THEN** the response is `404` HTML with the same page as for a draft

### Requirement: Article bodies render to fixed, escaped HTML
The reader SHALL render a body in format version 1 by mapping each block to fixed HTML only:
`paragraph` to a paragraph, `subhead` to a subheading below the headline level, `quote` to a
block quotation, `list` to a bullet list with one item per entry. Within runs, `bold: true` SHALL
render as strong emphasis and a line feed as a line break. All text — in the body and in every
other article field — SHALL be HTML-escaped so that markup characters appear literally and are
never interpreted.

#### Scenario: All block types
- **WHEN** the live body contains a paragraph with a bold run, a subhead, a quote and a two-item list
- **THEN** the article page contains, in that order, a paragraph with a `<strong>` run, an `<h2>`, a `<blockquote>` and a `<ul>` with two `<li>`

#### Scenario: Line break inside a paragraph
- **WHEN** a paragraph run's text is `one\ntwo`
- **THEN** the paragraph renders `one`, a `<br>` and `two`

#### Scenario: Markup is shown as text
- **WHEN** the live headline is `<b>Hi</b>` and a paragraph run is `<script>alert(1)</script>`
- **THEN** the page contains `&lt;b&gt;Hi&lt;/b&gt;` and `&lt;script&gt;alert(1)&lt;/script&gt;` and no `<script>` element from the article

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
