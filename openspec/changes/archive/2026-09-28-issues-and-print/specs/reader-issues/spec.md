## Purpose

Shows live issues to readers: each issue as its own newspaper page, an archive of all live
issues, and the current issue named in the front page masthead.

## ADDED Requirements

### Requirement: Issue page
`GET /issues/{id}` SHALL render a published issue with `data-view="issue"` on `<main>`: the
masthead (linked to `/`) with the issue line — the localized label ("Ausgabe 3" / "Issue 3") and,
when set, the long publication date — then the issue's articles whose status is `PUBLISHED`, in
issue order, the first of them as lead story and the others as cards, exactly as the front page
renders stories (live revision only, lead images, section tags), and a link to
`/print/issue/{id}`. An issue without published articles SHALL show a note that it has no
articles yet. A malformed or unknown id and an issue that is not published SHALL get the reader's
`404` page.

#### Scenario: Issue in order
- **WHEN** published issue 2 holds articles B, A, C (all `PUBLISHED`)
- **THEN** `GET /issues/{id}` shows B as lead story, then A and C as cards, and the masthead reads "Ausgabe 2" for a German browser

#### Scenario: Unpublished articles are left out
- **WHEN** published issue 2 holds articles X (`OFFLINE`) and Y (`PUBLISHED`)
- **THEN** the page shows Y as lead story and does not contain X's headline

#### Scenario: Issue not live
- **WHEN** issue 4 is not published and a visitor requests `/issues/{id}` for it
- **THEN** the response is the `404` page

#### Scenario: Issue with publication date
- **WHEN** published issue 3 has publication date 2026-10-12 and a German browser requests it
- **THEN** the masthead reads "Ausgabe 3" and "12. Oktober 2026"

### Requirement: Issue archive
`GET /issues` SHALL render, with `data-view="issues"`, the published issues ordered by number,
highest first, each with its label, publication date (if set) and the live headline of its first
published article (if any), linking to `/issues/{id}`. With no published issue it SHALL show a
note that there are no issues yet.

#### Scenario: Archive lists live issues only
- **WHEN** issues 1 and 2 are published and issue 3 is not
- **THEN** `GET /issues` lists issue 2, then issue 1, and not issue 3

### Requirement: Front page names the current issue
When at least one issue is published, the front page masthead SHALL show the issue line of the
published issue with the highest number, linked to its issue page. When more than one issue is
published it SHALL also link to `/issues` ("Alle Ausgaben" / "All issues"). With no published
issue the masthead SHALL show no issue line. The front page's article list itself SHALL stay as it
is (published articles, newest first publication first).

#### Scenario: Blog mode
- **WHEN** only issue 1 is published
- **THEN** the front page masthead shows "Ausgabe 1" linking to `/issues/{id}` and no link to `/issues`

#### Scenario: Several issues
- **WHEN** issues 1, 2 and 3 are published and issue 4 is not
- **THEN** the front page masthead shows "Ausgabe 3" and a link to `/issues`

#### Scenario: Nothing live yet
- **WHEN** no issue is published
- **THEN** the front page masthead shows no issue line and no link to `/issues`

### Requirement: Issue pages follow the newspaper's visibility
`/issues` and `/issues/{id}` SHALL apply the same access rules as the article page: in a private
newspaper an anonymous visitor SHALL be redirected to `/login` with the requested path as `next`,
a logged-in visitor without a newspaper role SHALL get the `404` page, and the pages SHALL be sent
with `Cache-Control: private, no-store` for a private newspaper or a logged-in visitor.

#### Scenario: Anonymous visitor of a private newspaper
- **WHEN** the visibility is `private` and an anonymous visitor requests `/issues`
- **THEN** the response redirects to `/login?next=/issues`

#### Scenario: Entitled reader of a private newspaper
- **WHEN** the visibility is `private` and the logged-in reader `oma` requests a published issue
- **THEN** the issue page is shown with `Cache-Control: private, no-store`
