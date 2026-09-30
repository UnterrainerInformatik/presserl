## MODIFIED Requirements

### Requirement: Article print view
`GET /print/article/{id}` SHALL render the live revision of an article visible to readers (status
`PUBLISHED` in a published issue) with `data-view="print-article"` on `<main>`: a compact masthead
(newspaper name and subtitle, no tools, no section bar), the section name, kicker, headline,
subheadline, lead image in the `print` rendition with its caption, lead, byline with publication
date, and the whole body, in a single column. A malformed or unknown id and an article that is not
visible to readers SHALL get the reader's `404` page.

#### Scenario: Print a published article
- **WHEN** a visitor of a public newspaper requests `/print/article/{id}` of a published article with lead image in a published issue
- **THEN** the page contains its headline, body and an `<img>` pointing to `/media/{mediaId}/print`

#### Scenario: Unpublished changes do not leak
- **WHEN** a published article with live headline `Old` has a newer unpublished revision with headline `New`
- **THEN** the print view shows `Old` and does not contain `New`

#### Scenario: Draft has no print view
- **WHEN** a visitor requests the print view of a `DRAFT`
- **THEN** the response is the `404` page

#### Scenario: Article of a planned issue has no print view
- **WHEN** a visitor requests the print view of a `PUBLISHED` article whose issue is not published
- **THEN** the response is the `404` page
