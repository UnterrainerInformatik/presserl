## ADDED Requirements

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

## MODIFIED Requirements

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
