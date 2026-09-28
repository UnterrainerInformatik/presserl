## Why

Presserl promises "a real newspaper — front page, sections, issues, bylines, print views", but so
far articles only exist as one endless stream and nothing can be put on paper. M6 (Print) closes
that gap: articles are grouped into issues, and readers can print a single article (for the wall)
or a whole issue (to hand to grandma). Issues come first because the issue print view needs them;
both parts ship together as one change so the milestone lands in one step.

Families use issues in two ways, and both must work without ceremony:

- **Blog mode** — one issue that is live from the start and simply grows; there is no publication
  date, and maybe there is never a second issue.
- **Planned issues** — the newest issue collects new articles while it is not yet live; once it
  is complete it gets a publication date and is published explicitly, and the next one is started.

## What Changes

**Issues (backend, admin, contract)**

- New entity **Issue**: `number` (assigned on creation, next after the highest, never changed),
  optional `publicationDate` (a calendar date, display only), `published` switch (live or not)
  and an **ordered list of articles** — the first one is the issue's lead story. An article
  belongs to at most one issue.
- **Newest issue collects new articles:** when an article is published for the first time and
  belongs to no issue, it is appended to the issue with the highest number — whether or not that
  issue is live. Later publications (after edits or taking offline) do not move it.
- **Initial issue:** the migration creates issue 1, not live; on an existing installation it
  receives every article that has been published before, in order of first publication.
- New newspaper action **`MANAGE_ISSUES`** (`PUBLISHER`, `EDITOR_IN_CHIEF`) in `GET /api/me`.
- New endpoints, all requiring `MANAGE_ISSUES`: `GET/POST /api/issues`,
  `GET/PUT/DELETE /api/issues/{id}`, `POST /api/issues/{id}/publish|unpublish`,
  `PUT /api/issues/{id}/articles` (the complete ordered article list; listed articles move over
  from other issues, dropped ones belong to no issue afterwards). Deleting is only possible for
  an issue that is not live; its articles then belong to no issue.
- `ArticleDto` and the article summaries of `GET /api/articles` gain `issue`
  (`{ "id", "number" }` or `null`).
- **Admin app:** new header entry "Issues" (with `MANAGE_ISSUES`): list of issues, create, set or
  clear the publication date, switch live/not live, delete (not live only), and per issue the
  ordered article list with moving up/down, removing and adding articles; links to the issue page
  and its print view in the reader.

**Reader: issue pages and print views**

- `GET /issues/{id}` — a live issue as a newspaper page: masthead with issue number and date, its
  lead story and cards in issue order (published articles only), link to the print view.
- `GET /issues` — archive of the live issues, newest first. The front page masthead names the
  newest live issue (linked); the link to the archive appears only when more than one issue is
  live.
- `GET /print/article/{id}` — a published article on A4: compact masthead, single column, large
  type, lead image in the `print` rendition.
- `GET /print/issue/{id}` — a live issue on A4: front page with large masthead (issue number,
  date) and the lead story, then the other articles in two columns (three via theme token), each
  under a section header; figures and headline blocks never split across pages; page numbers in
  the page footer.
- Print views are black on white regardless of theme and dark mode, sized in `pt` independent of
  the reader's text size, and show a screen-only "Print" button (same-origin script, CSP stays
  `'self'`). Article and issue pages link to their print view. Same access rules as the article
  page (private newspaper: login redirect / `404`).
- New documented `data-view` values `issues`, `issue`, `print-article`, `print-issue` and styling
  classes for the fork theme.

**Housekeeping**

- `ai/primer/endpoints.md`, `http/issues.http`, `http/reader.http`, `docs/architecture.md`,
  `docs/design-guidelines.md`, `docs/roles-and-workflow.md`; M6 removed from
  `ai/open-proposals.md`.

## Capabilities

### New Capabilities
- `issues`: the issue entity, the initial issue, numbering, publication switch, article
  membership and order, appending newly published articles to the newest issue, the
  `/api/issues` endpoints and the `issue` reference on articles.
- `admin-issues`: the issues screens of the administration app.
- `reader-issues`: the reader's issue page, the issue archive and the issue line on the front
  page masthead.
- `reader-print`: the print views for an article and an issue, their layout rules and the links
  to them.

### Modified Capabilities
- `api-authentication`: `GET /api/me` lists the new action `MANAGE_ISSUES`.
- `admin-shell`: the header offers "Issues" for `MANAGE_ISSUES`.

## Non-goals

- Server-side PDF generation; "Print → Save as PDF" in the browser is enough.
- Issue titles/themes ("Summer issue"), cover images, per-issue editorials.
- Hiding articles from the front page or article pages while their issue is not live: article
  visibility stays governed by the article's own status. An issue that is not live is only
  hidden as an issue (issue page, archive, issue print view).
- Scheduled publishing (going live automatically on the publication date).
- Choosing the issue in the article editor; assignment is done in the issues screen or
  automatically.
- Section pages (`/sections/{slug}`) and a general article archive.
- Printing images inside article bodies (the body has none yet) and print layouts other than
  A4 portrait.

## Impact

- **backend:** new `issue` package (entity, service, resource, DTOs, validation, exception
  mappers); `ArticleService` appends on first publication; `ArticleDto`/`ArticleSummaryDto` gain
  `issue`; `NewspaperAction.MANAGE_ISSUES` + `Newsroom.allowedActions`; Flyway
  `V11__issues.sql` (table `issue`, `article.issue_id` + `issue_position`, issue 1, existing
  published articles).
- **reader:** `ReaderResource` routes `/issues`, `/issues/{id}`, `/print/article/{id}`,
  `/print/issue/{id}` (reader OIDC tenant paths extended); `ReaderIssues` queries; templates
  `issues.html`, `issue.html`, `printArticle.html`, `printIssue.html`, masthead issue line;
  `reader.css` print rules; `/reader/print.js`; messages (de/en).
- **admin:** API client (`IssueDto`, issue endpoints, `issue` on articles), issues list and detail
  screens with models, navigation entry, strings (de/en), Kotlin tests.
- **deploy:** none (theme templates keep working; forks may style the new views).
- **docs:** `docs/architecture.md` (data model, reader routes, API list, views),
  `docs/design-guidelines.md` (print views, styling API), `docs/roles-and-workflow.md` (who
  manages issues), `ai/primer/endpoints.md`, `http/issues.http`, `http/reader.http`,
  `ai/open-proposals.md`.
