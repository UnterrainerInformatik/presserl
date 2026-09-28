## Context

Articles have a section, a status and a live revision; the reader lists published articles on
the front page and renders one article per page (`ReaderResource`, `ReaderArticles`, Qute tags
`masthead`, `story`, `leadImage`). `docs/architecture.md` lists **Issue** only as "number and
publication date; groups articles" and sketches the reader routes `/issues/{id}`,
`/print/article/{id}` and `/print/issue/{id}`; `docs/design-guidelines.md` §4 fixes the print
layout (A4 portrait, page numbers, masthead, 2–3 columns, `break-inside: avoid`, black on white,
browser PDF only). The `print` rendition (≤ 3000 px) exists and is served by `/media/{id}/print`
for lead images of live revisions of published articles. Newspaper actions in `GET /api/me` come
from `Newsroom.allowedActions()`. The reader OIDC tenant covers only the paths in
`quarkus.oidc.reader.tenant-paths`.

For motivation and scope see proposal.md; for behaviour see the specs `issues`, `admin-issues`,
`reader-issues`, `reader-print`.

## Goals / Non-Goals

**Goals:**
- One issue model that serves blog mode and planned issues without a mode switch.
- The issue assignment of a newly published article happens inside the publication transaction,
  so no article can slip past the newest issue.
- Print views are pure HTML + CSS on the existing reader stack; nothing new in the CSP.

**Non-Goals:**
- Page-accurate layout control (orphans across columns, fixed column balancing) beyond what CSS
  paged media in current browsers gives.
- Changing article visibility based on issues.

## Decisions

### D1 — Membership lives on the article, not in a join table
`article.issue_id` (nullable FK → `issue`) and `article.issue_position` (nullable int). A check
keeps both null or both set.
*Why:* "at most one issue per article" becomes a schema fact and reading an article's issue is
one column; deleting an issue frees its articles explicitly (see D10).
*Alternative:* `issue_article(issue_id, article_id, position)` with a unique `article_id` — same
guarantee, one more table and join for every article DTO.

Positions are rewritten `0..n-1` on every `PUT /api/issues/{id}/articles`. There is no unique
constraint on `(issue_id, issue_position)`: appending uses `max(position)+1` and two concurrent
first publications could get the same value; readers order by `(issue_position, id)`, so a tie
is harmless and the next reorder rewrites positions. A unique constraint would turn that race
into a failed publication, which is worse.

### D2 — "Newest issue" is the highest number; no open/closed state
The target of automatic appends is always the issue with the highest number. Blog mode is "one
issue, published"; planned issues are "the highest issue is not yet published". Starting a new
issue is creating one. *Alternative:* an explicit "open" flag — an extra state users must keep
consistent, with no case the highest-number rule does not cover.

The append happens in `ArticleService` where `goLive` runs, only when `article.publishedAt` was
`null` before (first publication) and `article.issueId` is `null`: one query
`select id, coalesce(max position) … order by number desc limit 1` for the newest issue and its
last position, then two field assignments on the managed article entity — same transaction,
same flush. Both publish paths (`publish` and the final `approve`) go through it.

### D3 — Numbering
`number` = `max(number)+1` at creation (1 when none), `UNIQUE` in the schema; a concurrent double
creation fails one request with the unique violation, mapped to `409` ("try again"). Numbers are
never renumbered. Deleting the highest (unpublished) issue lets its number be issued again —
acceptable because only unpublished issues can be deleted, so no reader ever saw that number
belong to something else unless the issue had been unpublished first; this is documented in the
primer.

### D4 — Published switch with timestamp, independent of the date
`published boolean` + `published_at timestamptz` (set on the switch to published, cleared on
unpublish), `publication_date date` optional. The date is what readers see; `published_at` is
bookkeeping for the admin app. *Alternative:* visibility from the date (scheduled publishing) —
excluded by the proposal; a blog issue has no date at all.

### D5 — `MANAGE_ISSUES` as its own action
Same holders as `MANAGE_SECTIONS`, but its own action keeps the header entry and the endpoint
check independent, so a later change (e.g. section editors assembling issues) touches one
predicate. Position in `allowedActions`: after `ASSIGN_SECTION_ROLES`, matching the header order.

### D6 — REST contract

`IssueDto` (list entries, and the base of the detail):
```json
{ "id": 4, "number": 4, "publicationDate": "2026-10-12", "published": false,
  "publishedAt": null, "articleCount": 2, "newest": true }
```
`GET /api/issues` → `200 { "issues": [ IssueDto, … ] }` (number descending).

`POST /api/issues` body `{ "publicationDate": "2026-10-12" }` or `{ "publicationDate": null }`
or `{}` → `201`, `Location: /api/issues/{id}`, body `IssueDetailDto`.

`GET /api/issues/{id}` → `200 IssueDetailDto`:
```json
{ "id": 4, "number": 4, "publicationDate": null, "published": false, "publishedAt": null,
  "articleCount": 2, "newest": true,
  "articles": [ { …ArticleSummaryDto… }, { … } ] }
```
`PUT /api/issues/{id}` body `{ "publicationDate": "2026-10-12" | null }` (field required) →
`200 IssueDetailDto`.

`POST /api/issues/{id}/publish`, `POST /api/issues/{id}/unpublish` → `200 IssueDetailDto`.

`PUT /api/issues/{id}/articles` body `{ "articleIds": [9, 4, 6] }` → `200 IssueDetailDto`;
`400` with `{ "fields": [ { "field": "articleIds", "message": "unknown article 999999" } ] }` (the
existing `ApiErrorDto`/`FieldError` shape).

`DELETE /api/issues/{id}` → `204`; `409` (`ApiErrorDto`, field `null`) when published.

Errors throughout: `401` no token, `403` without `MANAGE_ISSUES`, `404` unknown id (also for a
malformed id), `400` validation with unknown fields rejected (as the sections endpoints).

`ArticleDto` and `ArticleSummaryDto` gain `"issue": { "id": 4, "number": 4 }` or `null`
(`IssueRefDto`). The summaries inside `IssueDetailDto` carry it as well (always this issue).
The article list query joins `issue` once, no N+1.

`GET /api/issues` and the detail are not filtered by article visibility: `MANAGE_ISSUES` holders
are editors-in-chief or publishers, who see every article anyway.

### D7 — Reader: shared story rendering, new `ReaderIssues` queries
The issue page reuses the front page's `story` tag and the `LIVE_PUBLISHED` query shape, extended
by `a.issueId = :issue order by a.issuePosition, a.id`. `ReaderIssues` provides: published issue
by id, published issues (number desc) with the live headline of their first published article
(one query with a lateral/`distinct on` subselect, or two queries — no N+1), and the newest
published issue plus the count of published issues for the masthead. `ReaderPage` gains an
optional `issueLine` (label, date, link) and `archiveLink` flag, filled only on the front page and
the issue pages; the `masthead` tag renders them as `.masthead__issue`.

Access handling is factored out of `article()` into one helper used by all new routes (private +
anonymous → login redirect with the requested path, private + not entitled → `404` page,
`noStore` rules). `LoginTarget` gains a generic `loginFor(path)` used for all of them;
`loginForArticle` becomes a call of it.

`quarkus.oidc.reader.tenant-paths` gains `/issues`, `/issues/*`, `/print/*`.

### D8 — Print views: own templates, one stylesheet
Two templates `printArticle.html` and `printIssue.html` on the same `layout/reader` (so
`custom.css` and `data-text-size` still apply on screen) plus a `print.js` script include. Print
rules live in `reader.css` under `@media print` and `@page`:

- `@page { size: A4 portrait; margin: 18mm 16mm 20mm; @bottom-center { content: counter(page); } }`
  — page-margin boxes are supported by current Chromium; the implementation checks Firefox and
  records the result in the design guidelines. Where unsupported, the page prints without a
  number (the browser's own header/footer option remains).
- print colours forced to ink `#000` / paper `#fff` via the colour tokens inside `@media print`
  (overrides dark mode, which is also inside a media query — the print block comes later).
- font sizes in `pt` inside `@media print` (`--presserl-text-size: 10.5pt` for issue columns,
  `12pt` for the article view), independent of `data-text-size`.
- issue: `.print-issue__front` ends with `break-after: page`; `.print-columns` uses
  `column-count: var(--presserl-_print-columns)` where the internal token is derived from
  `--presserl-grid-columns`, defaulted to `2` inside `[data-view="print-issue"]` (the global
  default 12 would be nonsense there); `column-gap` and a thin rule.
- `figure`, `.print-headline-block` (section header + kicker + headline + subheadline): 
  `break-inside: avoid`; the section header additionally `break-after: avoid`.
- section headers: section name in small caps with a top rule in the section colour
  (`border-top` — rules are the only allowed colour in print).
- hidden in print: `.masthead__tools`, `.section-bar`, `.text-size-switch`, `.print-toolbar`,
  `.print-link`.

The screen rendering of a print view shows the page as a paper preview (white sheet, max width
210mm, shadow) so the reader sees what will come out.

`print.js` (served from `/reader/print.js`, static): attaches `window.print()` to
`[data-print]` buttons. No inline script, so the CSP stays `script-src 'self'`.
*Alternative:* no button, only a hint "Ctrl+P" — children and grandparents do not know it.

The article print view uses the `print` rendition as `src` with `width`/`height` of that
rendition; no `srcset` (the printer is the target, the screen preview can afford it).
`ReaderArticle`/`ReaderImage` gain the `print` rendition (third left join in the query).

### D9 — Admin app
`IssueListScreen` + `IssueDetailScreen` with plain models (`IssueListModel`, `IssueDetailModel`)
in `ui/issue/`, following the section screens: API calls through `ApiClient`, errors via the
existing `Attempt` pattern, confirmation dialogs inside the app. The article order is edited
locally and sent as a whole after every single move/remove/add (optimistic list, replaced by the
response). The picker loads `GET /api/articles` (no filter) once when opened and filters out the
issue's own articles client-side. Date entry: a simple `yyyy-mm-dd` text field with a parser and
localized display — the Compose date picker is not reliable in the Wasm target and adds nothing
for one date. Reader links open `window.open(url, "_blank")` through the existing browser interop.

### D10 — Migration `V11__issues.sql`
```sql
CREATE TABLE issue (
    id               BIGSERIAL PRIMARY KEY,
    number           INT NOT NULL UNIQUE CHECK (number > 0),
    publication_date DATE,
    published        BOOLEAN NOT NULL DEFAULT FALSE,
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT issue_published_at CHECK (published = (published_at IS NOT NULL))
);
ALTER TABLE article
    ADD COLUMN issue_id       BIGINT REFERENCES issue (id),
    ADD COLUMN issue_position INT,
    ADD CONSTRAINT article_issue_position CHECK ((issue_id IS NULL) = (issue_position IS NULL));
CREATE INDEX article_issue_idx ON article (issue_id, issue_position) WHERE issue_id IS NOT NULL;

INSERT INTO issue (number) VALUES (1);
UPDATE article a SET issue_id = i.id, issue_position = ranked.pos
  FROM issue i,
       (SELECT id, row_number() OVER (ORDER BY published_at, id) - 1 AS pos
          FROM article WHERE published_at IS NOT NULL) ranked
 WHERE i.number = 1 AND a.id = ranked.id;
```
The foreign key has no delete action on purpose: `ON DELETE SET NULL` would clear `issue_id` but
leave `issue_position`, violating the symmetric check. The delete endpoint therefore clears both
columns of the issue's articles and then deletes the issue, in one transaction; a forgotten
clear fails loudly on the FK instead of leaving half-assigned rows.

## Risks / Trade-offs

- [Page numbers not printed in older browsers without page-margin boxes] → acceptable; the
  browser's own header/footer option prints numbers; documented in the design guidelines.
- [Long articles in columns produce uneven columns and widowed headings] → `break-after: avoid`
  on section headers and headline blocks, `orphans/widows: 3`; exact balancing is out of scope.
- [An article in an unpublished (planned) issue is already visible on the front page and its
  article page] → intended (proposal non-goal); the issue only hides the issue. Families who want
  "everything at once" publish the articles when the issue goes live.
- [Concurrent first publications share a position] → harmless tie, ordered by id (D1).
- [Deleting the highest issue reuses its number] → only unpublished issues can be deleted (D3).
- [Admin sends the full order; two editors editing the same issue overwrite each other] → last
  write wins, like section order; no optimistic lock for issues in this change.
- [Issue print view of a big issue loads many `print` renditions (up to ~1 MB each)] → `loading="lazy"`
  is useless for printing; the view is opened on purpose. Acceptable for family-sized issues.

## Migration Plan

1. Deploy: Flyway `V11` creates `issue`, adds the article columns, creates issue 1 (not live)
   and puts every already published article into it. Nothing becomes visible until someone
   publishes issue 1 (blog mode) — the front page is unchanged.
2. Upgrade note in the release/`INSTALL.md` "Updating" section: "Open *Issues* in the admin app
   and switch issue 1 live for blog mode, or date and publish it as your first issue."
3. Rollback: the previous image ignores the new table and columns; nothing to undo. (Applied
   migrations are never edited; a correction would be `V12`.)
