## Why

With the approval chain in place, approvers can only find the articles waiting for them by
scanning "All articles" for the waiting marker, and `GET /api/articles?pending=true` also returns
articles the user cannot approve (their own submissions, levels above theirs). Parents supervising
a child, section editors and editors-in-chief need a "waiting for me" view that shows exactly
what they have to decide and how much of it there is (`docs/architecture.md`, view 3; M3 in
`docs/vision.md`).

## What Changes

- New filter `awaitingMe=true` on `GET /api/articles`: only articles the requesting user may
  approve right now, i.e. whose `allowedActions` contain `APPROVE`. It combines with the other
  filters like they combine with each other.
- Admin app: a third tab "Waiting for me" next to "My articles" and "All articles", listing
  `GET /api/articles?awaitingMe=true` and showing the number of waiting articles in its label.
  The tab is shown only while at least one article waits for the user, so a solo newspaper never
  sees a review queue (`docs/roles-and-workflow.md`). The count is refreshed whenever the lists
  are shown or reloaded; when the queue becomes empty while the tab is selected, the app switches
  to "My articles".
- Admin API client gains the `pending` and `awaitingMe` filters.
- Docs: `docs/architecture.md` replaces the planned `GET /api/review-queue` with the filter;
  `ai/primer/endpoints.md` documents `awaitingMe`; `http/articles.http` exercises it.

## Non-goals

- Trust switches (separate M3 change); the queue follows whatever `APPROVE` reports, so trust will
  shape it automatically once it exists.
- A separate count endpoint or a count in `GET /api/me`; the count is the length of the list.
- Polling, push notifications or e-mail when something new arrives.
- Showing the queue count outside the article lists (e.g. in the header).
- Sorting the queue differently from the other lists (newest change first).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `articles`: the listing gains the `awaitingMe=true` filter.
- `admin-articles`: the article lists gain the "Waiting for me" tab with its count.

## Impact

- **backend**: `ArticleResource.list` (new query parameter), `ArticleService.list` / filtering by
  the same predicate that grants `APPROVE`; tests.
- **admin**: `ApiClient.articles` (`pending`, `awaitingMe`), `ArticleListScreen` (`ListTab`,
  count, tab visibility, fallback), localized strings (de/en); tests.
- **reader / deploy**: none.
- **docs / primer / http**: `ai/primer/endpoints.md`, `docs/architecture.md`,
  `http/articles.http`.
- REST contract: additive (new optional query parameter); existing clients are unaffected.
