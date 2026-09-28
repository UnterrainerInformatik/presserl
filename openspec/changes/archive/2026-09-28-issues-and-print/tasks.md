## 1. Backend — issues

- [x] 1.1 Flyway `V11__issues.sql` as in design D10 (`issue` table, `article.issue_id` + `issue_position` with symmetric check and partial index, issue 1, existing published articles ranked by first publication); `IssueEntity`; `ArticleEntity.issueId` / `issuePosition`
- [x] 1.2 `NewspaperAction.MANAGE_ISSUES` (after `ASSIGN_SECTION_ROLES`) and `Newsroom.mayManageIssues()` (`PUBLISHER` or `EDITOR_IN_CHIEF`) feeding `allowedActions()`; update `GET /api/me` tests for publisher, editor-in-chief, section editor, reporter
- [x] 1.3 `issue` package: `IssueDto`, `IssueDetailDto`, `IssueRefDto`, request records, validator (ISO date or null, unknown fields rejected, `articleIds` unknown/repeated → field `articleIds`), `IssueException` + mappers (`400`/`404`/`409` in `ApiErrorDto` shape)
- [x] 1.4 `IssueService`: list (number desc, `articleCount`, `newest`), create (`max+1`, unique violation → `409`), read with article summaries in `(issue_position, id)` order, set date, publish/unpublish (idempotent, `publishedAt`, INFO log), set articles (one transaction, positions `0..n-1`, move from other issues, unlisted → no issue), delete (published → `409`; clear articles' columns, then delete; INFO log)
- [x] 1.5 `IssueResource` `/api/issues…` guarded by `MANAGE_ISSUES` (`403` otherwise, malformed id → `404`)
- [x] 1.6 `ArticleService`: on first publication (`publish` and the publishing `approve`) of an article without issue, append it to the highest-numbered issue in the same transaction; nothing when no issue exists; later publications untouched
- [x] 1.7 `ArticleDto` and `ArticleSummaryDto` gain `issue` (`IssueRefDto` or `null`), loaded with the existing queries (join, no N+1)
- [x] 1.8 `@QuarkusTest`s for the endpoints: every scenario of spec `issues` (list order and flags, create/next number/invalid date/unknown field, read order, date set and clear, publish/unpublish idempotent and articles unchanged, set articles reorder/move/drop/empty/unknown/repeated with nothing changed, delete planned/published `409`/unknown `404`, reporter `403`, no token `401`)
- [x] 1.9 `@QuarkusTest`s for the append rule: blog mode, planned issue collects, approval path appends, republication keeps issue and position, manually assigned draft stays, no issue exists; `issue` in `ArticleDto`/summaries
- [x] 1.10 Migration test: a database with published and never-published articles before `V11` ends with issue 1 holding the published ones in first-publication order (Flyway target version in a test, or a dedicated `@QuarkusTest` profile)

## 2. Admin — issues

- [x] 2.1 API client: `IssueDto`, `IssueDetailDto`, `IssueRefDto`, `issue` on `ArticleDto`/summary DTO, calls for all `/api/issues` endpoints; `ApiClientTest`/`DtoTest` cases (request JSON incl. explicit `null` date, decoding)
- [x] 2.2 Navigation: "Issues" header entry for `MANAGE_ISSUES` between "Sections" and "Accounts"; `NavigationTest` cases from the `admin-shell` scenarios
- [x] 2.3 `IssueListModel` + list screen (label, localized date or "no date", live chip, count, newest marker with hint, "New issue" with optional date → opens detail)
- [x] 2.4 `IssueDetailModel` + detail screen: date field (`yyyy-mm-dd` parse, localized display, clear), live switch, reader/print links in a new tab while live, delete with in-app confirmation while not live
- [x] 2.5 Article list of the issue: order with lead-story marker and "not shown to readers" marker for non-`PUBLISHED`, up/down, remove, "Add articles" picker from `GET /api/articles` without the issue's own articles, naming the current issue of each candidate; each change sends the full order and shows the response
- [x] 2.6 German and English strings for all issue texts (`Ausgaben`, `Neue Ausgabe`, `Erscheinungsdatum`, …); `StringsTest`/`LabelsTest` coverage
- [x] 2.7 Kotlin tests for the models: list mapping, create flow, date parse errors, publish/unpublish, move up/down produces the right `articleIds`, remove, add from another issue, delete confirm/cancel, error messages keep state

## 3. Reader — issue pages

- [x] 3.1 Extract the access handling of `ReaderResource.article` into one helper (login redirect with requested path via `LoginTarget.loginFor(path)`, `404` page for non-entitled, `noStore`) and use it for the article page unchanged
- [x] 3.2 `ReaderIssues`: published issue by id, its published articles in issue order (reusing the live-published query shape), published issues with the headline of their first published article, newest published issue + count of published issues — each without N+1
- [x] 3.3 `ReaderPage` issue line + archive flag; `masthead` tag renders `.masthead__issue` (label, long date, link) and the "All issues" link; front page fills it from the newest published issue
- [x] 3.4 Routes `GET /issues` and `GET /issues/{id}` with templates `issues.html` (`data-view="issues"`) and `issue.html` (`data-view="issue"`, lead story + cards via `story`, print link, empty note); `404` page for unknown/malformed/unpublished
- [x] 3.5 `quarkus.oidc.reader.tenant-paths` += `/issues`, `/issues/*`, `/print/*`; verify `ReaderTenantScope` keeps them on the reader tenant
- [x] 3.6 Messages de/en: issue label, all issues, no issues yet, issue empty, print
- [x] 3.7 Tests: every scenario of spec `reader-issues` (order, unpublished articles left out, not live `404`, date line, archive lists only live issues, masthead blog mode / several / none, private anonymous redirect to `/login?next=/issues`, entitled reader `private, no-store`, logged-in without role `404`)

## 4. Reader — print views

- [x] 4.1 `ReaderArticle`/`ReaderImage` carry the `print` rendition (third left join)
- [x] 4.2 Routes `GET /print/article/{id}` and `GET /print/issue/{id}` with templates `printArticle.html` (`data-view="print-article"`) and `printIssue.html` (`data-view="print-issue"`: front page with masthead, issue line, full lead story; further articles in full inside `.print-columns`, each under a section header); shared tag for a full article (headline block, `print` figure, lead, byline, body)
- [x] 4.3 Print toolbar (screen only): "Print" button with `data-print`, back link; `/reader/print.js` static file binding `window.print()`; no inline script
- [x] 4.4 Print links on the article page and the issue page (`.print-link`)
- [x] 4.5 `reader.css`: screen paper preview for print views; `@page` A4 portrait with margins and page counter; `@media print` black on white via tokens, `pt` sizes, hidden tools/section bar/toolbar/links, `break-inside: avoid` on figures and headline blocks, `break-after: avoid` on section headers, orphans/widows; `--presserl-grid-columns` default 2 inside `[data-view="print-issue"]` driving the column count
- [x] 4.6 Tests: every scenario of spec `reader-print` (content and `print` image URL, unpublished changes not leaking, draft `404`, issue order with section headers, unpublished articles left out, not live `404`, stylesheet contains `@page` A4 + counter, print link on article page, private anonymous redirect with `next`, CSP header unchanged `'self'`)

## 5. Contract and docs

- [x] 5.1 `ai/primer/endpoints.md`: `MANAGE_ISSUES` in `GET /api/me`, `IssueDto`/`IssueDetailDto`, every `/api/issues` endpoint with errors and side effects, `issue` in `ArticleDto` and summaries, the append rule, number reuse note; reader routes `/issues`, `/issues/{id}`, `/print/article/{id}`, `/print/issue/{id}`
- [x] 5.2 `http/issues.http` (all endpoints incl. errors) and `http/reader.http` (issue pages, print views); run them against a live dev backend
- [x] 5.3 `docs/architecture.md`: Issue in the data model, reader routes and tenant paths, REST sketch marked implemented, views; `docs/roles-and-workflow.md`: editors-in-chief and publishers manage issues, blog mode vs planned issues
- [x] 5.4 `docs/design-guidelines.md` §4 and §5: print view details as built (page numbers support note, columns token), new `data-view` values `issues`, `issue`, `print-article`, `print-issue` and classes `.masthead__issue`, `.print-columns`, `.print-toolbar`, `.print-link`, `.print-section-header`
- [x] 5.5 `deploy/INSTALL.md` "Updating": note to switch issue 1 live (blog mode) or date and publish it after upgrading
- [x] 5.6 `ai/open-proposals.md`: remove the M6 section

## 6. Verification

- [x] 6.1 `./mvnw verify` in `backend/` and `./gradlew check` in `admin/`
- [x] 6.2 Headless UI check (Playwright) in dev: in the admin app switch issue 1 live, publish an article and see it appended; create issue 2, add an article from issue 1, reorder, publish it; reader front page shows "Ausgabe 2" and the archive link; issue page order matches
- [x] 6.3 Print check: Chromium headless `page.pdf()` of `/print/article/{id}` and `/print/issue/{id}` — A4, page numbers, first page break after the lead story, two columns, images unsplit, black on white in dark mode; record the Firefox page-number result in the design guidelines
- [x] 6.4 Stop every server/container started for verification (check with `ps`/`ss`/`docker`)
