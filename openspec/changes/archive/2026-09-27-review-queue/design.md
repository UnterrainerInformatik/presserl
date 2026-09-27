## Context

`GET /api/articles` (`ArticleResource.list` → `ArticleService.list`) builds one HQL query from the
filters `status`, `mine` and `pending` plus the visibility restriction, then maps each view to an
`ArticleSummaryDto` whose `allowedActions` come from `ArticlePolicy.allowedActions(newsroom,
article, revision, staffing)` with the staffing loaded by `StaffingService.forArticles`. `APPROVE`
is listed exactly when the user is not the author, a submission is pending and the user's approval
level is at least `pendingLevel`.

In the admin app `ArticleListScreen` shows a `PrimaryTabRow` over `enum class ListTab { MINE, ALL }`
(selected index = `tab.ordinal`) and loads `api.articles(mine = …)` in a `LaunchedEffect(tab,
loads)`. The route stack in `App.kt` keeps `Route.ArticleList(tab)`; returning from the editor
recomposes the list screen, which reloads. `ApiClient.articles` has no `pending` parameter yet.
See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**
- One source of truth for "may approve": the queue filter uses the very predicate behind
  `APPROVE`, so trust switches later shape the queue without touching it.
- No new endpoint; one extra optional query parameter.

**Non-Goals:**
- Doing the level comparison in SQL.
- Caching or pushing the count.

## Decisions

### D1 — Filter on the existing listing, not `GET /api/review-queue`
`docs/architecture.md` sketched `GET /api/review-queue`. A filter on `GET /api/articles` returns
the same summaries the lists already render, combines with `status`/`mine`/`pending`, and needs no
new DTO or client model. The architecture line is replaced.

```
GET /api/articles?awaitingMe=true            (combinable with status, mine, pending)
200 [ ArticleSummaryDto, ... ]               same shape and order as without the filter;
                                             every entry's allowedActions contains "APPROVE"
400                                          unknown status, as before
```

`awaitingMe=false` or absent means no filter. `mine=true&awaitingMe=true` is always empty (an
author never approves their own article) — no special case, it falls out of the predicate.

### D2 — Pre-filter in SQL, decide with `ArticlePolicy`
`awaitingMe` implies `pending`, so `ArticleResource.list` passes `pending || awaitingMe` to
`ArticleService.list` and additionally excludes the user's own articles in HQL
(`a.authorSub <> :viewer`) — both cheap and exact necessary conditions. After the summaries are
built, the resource keeps only those whose `allowedActions` contain `ArticleAction.APPROVE`.
*Alternative:* express "approval level ≥ pending level" in HQL (global roles plus
`SECTION_EDITOR` of `sectionId in :editedSections`) — rejected: it duplicates
`ApprovalChain.approverLevel` and would silently diverge once trust or other rules are added.
*Alternative:* filter only in Java without the SQL pre-filter — rejected: the publisher would load
every visible article to find a handful of pending ones.

### D3 — Count = list length, fetched by the list screen
The count shown in the tab label is `size` of the `awaitingMe=true` response. The list screen
fetches it in its own `LaunchedEffect(loads)`, independent of the selected tab, so it is refreshed
whenever the screen is (re)entered — e.g. returning from the editor after approving — and on
reload. When the selected tab is `QUEUE`, the queue response is also the list shown (no second
request). A count in `GET /api/me` or a count endpoint was rejected (non-goal; the list is small
and needed anyway once the tab is opened).

### D4 — Tab visibility and fallback
`ListTab` gains `QUEUE`. The tab row is built from the visible tabs (`MINE`, `ALL`, and `QUEUE`
only while the last queue response is non-empty); the selected index is the position of `tab` in
that list, no longer `tab.ordinal`. While the first queue response is outstanding the tab is not
shown. When a queue response is empty while `tab == QUEUE`, the screen calls `onTab(MINE)`. Label:
`Wartet auf mich (n)` / `Waiting for me (n)` via a formatted string resource `tab_queue`.
Visibility comes only from the response, never from roles (spec: "Review queue").

### D5 — API client
`ApiClient.articles(status, mine, pending = false, awaitingMe = false)`; each flag is sent only
when `true`, like `mine` today.

## Risks / Trade-offs

- [Pre-filter must stay a necessary condition of `APPROVE`] → the resource test covers the author
  exclusion and the level comparison, and the final decision is always `ArticlePolicy`.
- [Two requests per list load (list + queue)] → both are small; the queue request is skipped for
  the shown list when `tab == QUEUE`.
- [Count is stale while the user stays on the list] → refreshed on every entry and reload; polling
  is a non-goal.

## Migration Plan

No schema change. Backend and admin ship together in one image; an older admin simply never sends
`awaitingMe`. Rollback = previous image.
