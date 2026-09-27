## 1. Backend

- [x] 1.1 `ArticleResource.list`: add `@QueryParam("awaitingMe") boolean awaitingMe`; pass `pending || awaitingMe` to `ArticleService.list` and keep only summaries whose `allowedActions` contain `APPROVE` when `awaitingMe` is set
- [x] 1.2 `ArticleService.list`: new `excludeOwn` flag (set for `awaitingMe`) adding `a.authorSub <> :viewer` to the query; update its Javadoc
- [x] 1.3 `@QuarkusTest` integration tests for the spec scenarios: section editor sees only the `SECTION_EDITOR` article, own submission excluded for `chief`, publisher sees all waiting articles, reporter and solo publisher get `[]`, `awaitingMe` combined with `status`, `mine=true&awaitingMe=true` is empty

## 2. Admin

- [x] 2.1 `ApiClient.articles`: add `pending` and `awaitingMe` flags, sent only when `true`
- [x] 2.2 `ListTab.QUEUE`; `ArticleListScreen` fetches the queue on every entry/reload, builds the tab row from the visible tabs (queue only while non-empty, label with count), selects by position, falls back to `MINE` when the queue empties while selected, and reuses the queue response as the list when `QUEUE` is selected
- [x] 2.3 Formatted string `tab_queue` in German (`values`, "Wartet auf mich (%1$d)") and English (`values-en`, "Waiting for me (%1$d)")
- [x] 2.4 Kotlin tests: `ApiClient.articles` sends `awaitingMe=true` / `pending=true` only when set; visible-tab computation (empty queue → no `QUEUE`, fallback to `MINE`)

## 3. Contract / Docs

- [x] 3.1 `ai/primer/endpoints.md`: `awaitingMe` filter on `GET /api/articles` (semantics = `APPROVE` in `allowedActions`, combinable)
- [x] 3.2 `http/articles.http`: `GET /api/articles?awaitingMe=true` request
- [x] 3.3 `docs/architecture.md`: replace the planned `GET /api/review-queue` line with the `awaitingMe` filter and mark the review-queue view as implemented

## 4. Verification

- [x] 4.1 Run backend tests for the article/approval packages and the admin tests (`reference_build_and_test.md`)
- [x] 4.2 Run the new `.http` request against a live dev backend
- [x] 4.3 Drive the admin app headless: a section editor sees "Waiting for me (n)", approves one article, the count drops, and the tab disappears when empty; a solo publisher sees no queue tab; stop every server started
