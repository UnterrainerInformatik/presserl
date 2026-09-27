## Context

The approval chain (`approval-chain-core`) is computed per request by `ApprovalChain.next` from the
author's level and the `Staffing`; `ArticlePolicy.verdict` decides every action from it. Taking
offline (`ArticleService.takeOffline`) only sets `status = OFFLINE`. Going online happens in one
place, `ArticleService.goLive` (publish and final approval). See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**
- Express the lock as one extra rule in the chain, so submit / publish / approve / `allowedActions`
  need no special cases of their own.
- Keep the REST contract additive.

**Non-Goals:**
- Tracking who set the lock (no column for it) — the review history stays the only audit trail.

## Decisions

### D1 — Storage: `article.locked BOOLEAN NOT NULL DEFAULT false`
Flyway `V7__article_lock.sql` adds the column and `CHECK (NOT locked OR status = 'OFFLINE')`, so a
locked article that is somehow published would fail at the database. Existing rows become unlocked,
which matches today's semantics (nothing was ever locked).
*Alternative:* `locked_by_sub`/`locked_at` columns — rejected as not needed (non-goal) and more to
keep consistent.

### D2 — The lock is a chain rule, not an action gate
`ApprovalChain.next(above, sectionId, authorSub, staffing, locked)`: a level qualifies when it is
staffed **or** (`locked` and it is `PUBLISHER`). Consequences, all falling out of existing code:
- author below `PUBLISHER`: chain not empty ⇒ `PUBLISH` forbidden, `SUBMIT` allowed;
- author holding `PUBLISHER`: nothing above `PUBLISHER` ⇒ chain empty ⇒ may publish directly;
- lower approvals move the article up to `PUBLISHER` at most, never online;
- a submission pending when the lock is set needs no migration: the next approval recomputes.
Trust (later) becomes another skip predicate; the lock predicate wins over it for `PUBLISHER`.
*Alternative:* checking `locked` in `ArticlePolicy` for `PUBLISH`/`APPROVE` — rejected: it would
duplicate the chain logic and break "approval settles up to the approver's level".

### D3 — Setting and clearing
- `takeOffline`: `locked = user.has(PUBLISHER)`. (Only `PUBLISHED` articles can be taken offline,
  so the previous value is always `false`.)
- `goLive`: `locked = false` — covers publish and the final approval.
- `unlock`: `locked = false`, `updatedAt = now`; nothing else.
Reject and withdraw leave the lock alone.

### D4 — `UNLOCK` action and endpoint
`ArticleAction.UNLOCK` between `TAKE_OFFLINE` and `DELETE`. Policy: `FORBIDDEN` unless the user holds
`PUBLISHER`; `CONFLICT` unless `locked`. Publishers see every article, so the 404 path only matters
for non-publishers. Messages: `403` "only a publisher may unlock this article", `409` "this article
is not locked".

```
POST /api/articles/{id}/unlock          (no body)
200 ArticleDto   { ..., "status": "OFFLINE", "locked": false, "allowedActions": [...] }
403 / 404 / 409  error body as for the other article actions
```

### D5 — Representation
`ArticleDto` and `ArticleSummaryDto` get `boolean locked`, placed after `pendingLevel`:

```json
{ "id": 7, "status": "OFFLINE", "liveRevision": 1, "hasUnpublishedChanges": false,
  "pendingLevel": null, "locked": true, "version": 5, ...,
  "allowedActions": ["UNLOCK"] }
```

### D6 — Admin app
`ArticleDto`/`ArticleSummaryDto` (Kotlin) get `locked: Boolean = false` (tolerant default);
`ApiClient.unlock(id)`; `EditorActions.unlock`; the editor shows a banner ("Locked by a publisher —
only a publisher can put it back online") and an outlined "Unlock" button next to "Take offline";
the list entry shows a "Locked" line like the waiting line. Strings go into the existing resources
(`values` German, `values-en` English).

## Risks / Trade-offs

- [A publisher who pulls the brake on their own article locks it too] → harmless: their chain is
  empty and publishing clears the lock.
- [If every `PUBLISHER` role is removed, a locked article of a non-publisher waits for `PUBLISHER`
  forever] → accepted; a publisher is required for the newspaper anyway, and the bootstrap
  publisher cannot be locked by the account rules.
- [Old admin builds ignore `locked` and `UNLOCK`] → additive contract; they just don't show them.

## Migration Plan

`V7` runs on startup; rollback = redeploy the previous image after dropping the column manually
(no data depends on it).
