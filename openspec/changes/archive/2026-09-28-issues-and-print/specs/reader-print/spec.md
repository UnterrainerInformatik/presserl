## Purpose

Lets readers put the newspaper on paper: a print view for a single article (to pin on the wall)
and one for a whole issue, laid out for A4 and printed or saved as PDF with the browser.

## ADDED Requirements

### Requirement: Article print view
`GET /print/article/{id}` SHALL render a `PUBLISHED` article's live revision with
`data-view="print-article"` on `<main>`: a compact masthead (newspaper name and subtitle, no
tools, no section bar), the section name, kicker, headline, subheadline, lead image in the
`print` rendition with its caption, lead, byline with publication date, and the whole body, in a
single column. A malformed or unknown id and an article that is not `PUBLISHED` SHALL get the
reader's `404` page.

#### Scenario: Print a published article
- **WHEN** a visitor of a public newspaper requests `/print/article/{id}` of a published article with lead image
- **THEN** the page contains its headline, body and an `<img>` pointing to `/media/{mediaId}/print`

#### Scenario: Unpublished changes do not leak
- **WHEN** a published article with live headline `Old` has a newer unpublished revision with headline `New`
- **THEN** the print view shows `Old` and does not contain `New`

#### Scenario: Draft has no print view
- **WHEN** a visitor requests the print view of a `DRAFT`
- **THEN** the response is the `404` page

### Requirement: Issue print view
`GET /print/issue/{id}` SHALL render a published issue with `data-view="print-issue"` on
`<main>`: a first page with the large masthead, the issue line (label and, when set, publication
date) and the lead story in full; then every further `PUBLISHED` article of the issue in issue
order and in full, set in columns, each preceded by a section header naming its section. Articles
that are not `PUBLISHED` SHALL be left out. An issue without published articles SHALL show only
the masthead and a note. A malformed or unknown id and an issue that is not published SHALL get
the reader's `404` page.

#### Scenario: Whole issue in order
- **WHEN** published issue 2 holds B (`Sport`), A (`Kultur`), C (`Sport`), all `PUBLISHED`
- **THEN** the print view shows B as lead story on the first page, then A under a "Kultur" header and C under a "Sport" header

#### Scenario: Issue not live
- **WHEN** issue 4 is not published
- **THEN** `/print/issue/{id}` answers the `404` page

### Requirement: Print layout
Both print views SHALL, when printed, use A4 portrait pages with margins and the page number in
the page footer, print black on white regardless of the theme's colours and dark mode (section
colours only as rules), size text in absolute print units independent of the reader's text size,
and keep figures and the headline block of an article together on one page. The issue print view
SHALL set the articles after the first page in two columns by default; a fork theme SHALL be able
to change the number of columns through `--presserl-grid-columns` within
`[data-view="print-issue"]`. Images SHALL be the `print` rendition. Masthead tools, the text-size
switch, the section bar, links' decoration and the print button SHALL NOT be printed.

#### Scenario: Print styles are present
- **WHEN** a visitor requests a print view
- **THEN** the page links the reader stylesheet, which contains `@page` rules for A4 and a page counter in the footer

#### Scenario: Theme sets three columns
- **WHEN** the fork's `custom.css` contains `@media print { [data-view="print-issue"] { --presserl-grid-columns: 3; } }`
- **THEN** the issue print view prints the articles after the first page in three columns

### Requirement: Print button
Each print view SHALL show on screen, but not in print, a "Drucken" / "Print" button that opens
the browser's print dialog, and a link back to the article or issue page. The button SHALL be
driven by a same-origin script so the reader's content security policy stays `'self'`; without
script the page SHALL still be printable via the browser.

#### Scenario: Button opens the print dialog
- **WHEN** a visitor opens `/print/article/{id}` in a browser and presses "Drucken"
- **THEN** the browser's print dialog opens

### Requirement: Links to the print views
The article page SHALL link to `/print/article/{id}` and the issue page to `/print/issue/{id}`
("Drucken" / "Print"), both hidden when the page itself is printed.

#### Scenario: Article page links its print view
- **WHEN** a visitor opens a published article's page
- **THEN** it contains a link to `/print/article/{id}` of that article

### Requirement: Print views follow the newspaper's visibility
The print views SHALL apply the same access and caching rules as the article page: in a private
newspaper an anonymous visitor SHALL be redirected to `/login` with the requested path as `next`,
a logged-in visitor without a newspaper role SHALL get the `404` page, and the pages SHALL be sent
with `Cache-Control: private, no-store` for a private newspaper or a logged-in visitor. Their
`print` images are served under the existing rules of `/media/{id}/{kind}`.

#### Scenario: Anonymous visitor of a private newspaper
- **WHEN** the visibility is `private` and an anonymous visitor requests `/print/issue/{id}`
- **THEN** the response redirects to `/login` with `next` set to that path
