## Context

Articles (`backend/.../article`) follow the rule order writer → visibility → role/ownership →
state, decided by the pure `ArticlePolicy.verdict(action, newsroom, article, latestRevision)` and
enforced by `ArticleService.require`. `Newsroom` combines the requester's token roles with their
section roles from the DB. Only the requester's roles are known per request; the roles of *other*
accounts (needed to know whether a level is staffed) live in Keycloak groups (`publisher`,
`editor-in-chief`) and in `section_role`. `AccountService.list()` already reads group members
through the Keycloak Admin client on a worker thread (`KeycloakCalls`).

`ArticleStatus.SUBMITTED` exists in code and in the `article.status` CHECK constraint but is never
written. The reader selects `status = PUBLISHED` joined with the live revision. `OptimisticLockException`
is already mapped to `409`.

Requirements: see `specs/approval-chain/spec.md`, `specs/articles/spec.md`,
`specs/admin-articles/spec.md`.

## Goals / Non-Goals

**Goals:**
- One pure, unit-testable chain calculator; `ArticlePolicy` stays the single place that decides actions.
- Staffing lookups at most once per request, and only when an answer depends on them.
- Trust (next change) fits in as one more "level is skipped" predicate without reshaping the code.

**Non-Goals:**
- Caching Keycloak group membership across requests.
- Storing the full planned chain on the article (levels are re-evaluated when an approval happens).

## Decisions

### D1 — Status model: `SUBMITTED` only before the first publication, `pendingLevel` always

A new nullable column `article.pending_level` marks every pending submission. A never-published
article moves `DRAFT → SUBMITTED`; a `PUBLISHED` or `OFFLINE` article keeps its status while it
carries a `pending_level`.

Alternative: `SUBMITTED` for every submission. Rejected: a published article with pending changes
would drop out of the reader (which selects `PUBLISHED`), and rejection would have to remember
whether to return to `PUBLISHED`, `OFFLINE` or `DRAFT`. Keeping the status means the reader, the
take-offline rule and the lists need no change, and rejection/withdrawal only clears
`pending_level` (plus `SUBMITTED → DRAFT`).

### D2 — Levels and the chain as pure code

```java
enum ApprovalLevel { SECTION_EDITOR, EDITOR_IN_CHIEF, PUBLISHER }   // bottom → top, compareTo = order
```
`ApprovalChain` (static, pure):
- `authorLevel(Newsroom requester, long sectionId)` → `Optional<ApprovalLevel>` (empty = reporter);
  used at submit and for `SUBMIT`/`PUBLISH` in `allowedActions` (the author is the requester).
- `approverLevel(Newsroom, long sectionId)` → `Optional<ApprovalLevel>` (same mapping; the author
  is excluded by the policy, not here).
- `next(Optional<ApprovalLevel> above, long sectionId, String authorSub, Staffing)` →
  `Optional<ApprovalLevel>`: the lowest level strictly above `above` (all levels when empty) that
  is staffed. `Optional.empty()` = nothing left → publish.

Submit uses `next(authorLevel, …)`; approve uses `next(approverLevel, …)`. Because the approver's
level is at least the pending level, which lies above the author's level, the author's level is
not needed at approval time — no snapshot column.

Trust later: `next` gains a "skipped because trusted" predicate next to "not staffed".

### D3 — Staffing snapshot, loaded lazily once per request

```java
record Staffing(Map<Long, Set<String>> sectionEditors, Set<String> editorsInChief, Set<String> publishers) {
    boolean staffed(ApprovalLevel level, long sectionId, String authorSub) { /* holders minus author non-empty */ }
    static Staffing NOT_NEEDED;   // for requests where no chain question is asked
}
```
- Section editors: one query over `section_role where role = 'SECTION_EDITOR'` (small table).
- Editors-in-chief and publishers: Keycloak group members (`members(0, LIST_MAX, true)`, ids) via
  a new `RoleHolders` bean in the `account` package, run through `KeycloakCalls`. Locked accounts
  count (spec) — no `enabled` filter, which also means no extra user lookups.
- `StaffingService.forArticles(newsroom, articles)` loads the snapshot only when the requester does
  **not** hold `PUBLISHER` (a publisher's own chain is always empty, D2) **and** authored at least
  one of the articles; otherwise it returns `NOT_NEEDED`. Approve always loads it (next level).
- `ArticlePolicy.verdict/allowedActions` take the `Staffing` as an extra argument; DTO factories
  (`ArticleDto.of`, `ArticleSummaryDto.of`) receive it from the resource. The service methods
  return the views, the resource loads the staffing for them and maps.

Alternative: count holders with a DB mirror of Keycloak groups. Rejected: a second source of truth
for newspaper-wide roles; the admin client call is cheap for a family newspaper. Alternative:
cache for N seconds. Rejected for now: role changes must act at once (spec "Staffing changes"),
and invalidation across account edits is not worth it yet.

### D4 — Policy rules

`ArticleAction` order: `EDIT, SUBMIT, PUBLISH, WITHDRAW, APPROVE, REJECT, TAKE_OFFLINE, DELETE`.

| Action | FORBIDDEN unless | CONFLICT when |
|---|---|---|
| `EDIT` | author ∧ may write in section | pending |
| `SUBMIT` | author ∧ may write in section ∧ `next(authorLevel)` present | pending, or `PUBLISHED` without unpublished changes |
| `PUBLISH` | author ∧ may write in section ∧ `next(authorLevel)` empty | pending, or `PUBLISHED` without unpublished changes |
| `WITHDRAW` | author | not pending |
| `APPROVE`, `REJECT` | ¬author ∧ `approverLevel ≥ pendingLevel` | not pending |
| `TAKE_OFFLINE` | unchanged | unchanged |
| `DELETE` | unchanged | unchanged |

For `APPROVE`/`REJECT` on an article that is not pending, the FORBIDDEN check uses "holds any
approval level for the section"; so an approver sees `409` on a draft, others `403` (rule order
role → state). `PUBLISH` for a publisher-author keeps today's behaviour exactly. Headline (`400`)
is checked after the policy, as today for publish.

### D5 — Approval and rejection effects

- **Approve:** load staffing → `next(approverLevel)`; present → `pending_level = next`; empty →
  same effect as `publish` (shared private method: mark latest revision published, set
  `live_revision`, `published_at` on first publication, status `PUBLISHED`), `pending_level = null`.
  Record a review `APPROVED` with the level the article waited for.
- **Reject:** `pending_level = null`, `SUBMITTED → DRAFT`; record `REJECTED` with the note.
- **Withdraw:** like reject without record.
- All four and submit set `updated_at` and bump `version`. No request `version`: concurrent
  decisions are serialised by the `@Version` optimistic lock (`409` for the loser, mapper exists).

### D6 — Schema (Flyway `V6__approval_chain.sql`)

```sql
ALTER TABLE article ADD COLUMN pending_level TEXT
    CHECK (pending_level IN ('SECTION_EDITOR', 'EDITOR_IN_CHIEF', 'PUBLISHER'));
ALTER TABLE article ADD CONSTRAINT article_submitted_pending
    CHECK ((status = 'SUBMITTED') = (pending_level IS NOT NULL AND live_revision IS NULL));
CREATE INDEX article_pending_idx ON article (pending_level) WHERE pending_level IS NOT NULL;

CREATE TABLE article_review (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id            BIGINT      NOT NULL REFERENCES article (id) ON DELETE CASCADE,
    revision              INT         NOT NULL,
    decision              TEXT        NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    level                 TEXT        NOT NULL CHECK (level IN ('SECTION_EDITOR', 'EDITOR_IN_CHIEF', 'PUBLISHER')),
    reviewer_sub          TEXT        NOT NULL,
    reviewer_username     TEXT        NOT NULL,
    reviewer_display_name TEXT        NOT NULL,
    note                  TEXT,
    created_at            TIMESTAMPTZ NOT NULL,
    CHECK ((decision = 'REJECTED') = (note IS NOT NULL))
);
CREATE INDEX article_review_article_idx ON article_review (article_id, created_at DESC);
```
Additive; existing rows satisfy the constraint (no `SUBMITTED` rows exist, `pending_level` is null).
`V2` is not touched (its "SUBMITTED not written yet" comment stays as history).

### D7 — REST contract

`ArticleDto` and `ArticleSummaryDto` gain `"pendingLevel": "SECTION_EDITOR" | "EDITOR_IN_CHIEF" | "PUBLISHER" | null`
(after `hasUnpublishedChanges`).

```http
POST /api/articles/{id}/submit      → 200 ArticleDto | 400 headline | 403 | 404 | 409
POST /api/articles/{id}/approve     → 200 ArticleDto | 403 | 404 | 409
POST /api/articles/{id}/withdraw    → 200 ArticleDto | 403 | 404 | 409
POST /api/articles/{id}/reject      → 200 ArticleDto | 400 note | 403 | 404 | 409
Content-Type: application/json
{ "note": "Please add who scored." }
```
Reject body: object with exactly `note` (string); unknown fields → `400` naming them; missing,
non-string, blank after trim, > 1000 code points, or control characters other than `\n` → `400`
naming `note`. The body is validated before the policy (like `PUT`), so a malformed body is `400`
for everyone.

```http
GET /api/articles/{id}/reviews → 200
```
```json
[ { "decision": "REJECTED", "level": "EDITOR_IN_CHIEF", "revision": 2,
    "reviewer": { "username": "chief", "displayName": "Chief" },
    "note": "Too short", "createdAt": "2026-09-27T10:05:00Z" },
  { "decision": "APPROVED", "level": "SECTION_EDITOR", "revision": 2,
    "reviewer": { "username": "nogroups", "displayName": "No Groups" },
    "note": null, "createdAt": "2026-09-27T10:01:00Z" } ]
```
`GET /api/articles?pending=true` (combinable with `status`, `mine`). Error body unchanged.

### D8 — Admin app

- `Dtos.kt`: `pendingLevel: String? = null` on article DTOs; `ReviewDto`; `ApiClient`: `submit`,
  `approve`, `reject(note)`, `withdraw`, `reviews`.
- `Actions.kt`: `EditorActions` gains `submit`, `withdraw`, `approve`, `reject`. Bottom-right
  primary: Publish or Submit (never both, server guarantees) or Approve; Reject next to Approve;
  Withdraw as secondary. Submit saves pending changes first (same path as publish).
- Reject dialog: in-app `AlertDialog` with a multi-line field (max 1000), confirm disabled while
  blank; a `400` with field `note` is shown in the dialog.
- Pending notice: "Wartet auf Freigabe durch {role label}" using the existing role labels in
  `Labels.kt` (`SECTION_EDITOR` → Ressortleiter, …); same text in list entries.
- Reviews panel below the actions, loaded on open and after every action; the newest rejection's
  note shown as a banner above the fields when no submission is pending.
- Strings in `values` (de) and `values-en`.

### D9 — Docs

`docs/roles-and-workflow.md` (approval chain section): skip rule "no account other than *A* holds
the level's role" replaces the section-only rule; cumulative approval; withdraw; content frozen
while waiting; published/offline articles keep their status while changes wait. Diagrams
`review-decision.puml` and `article-lifecycle.puml` updated accordingly (also dropping the
lifecycle's `Offline → Draft`/`Offline → delete` edges, which contradict the implemented rules)
and re-rendered. `docs/architecture.md`: data model line for `Article` (pending level) and
`ReviewNote` → `ArticleReview`.

## Risks / Trade-offs

- [Keycloak round-trip on requests of non-publisher authors, and on every approve] → only two
  group-member calls, skipped entirely for publishers and for lists without own articles; add a
  short cache later if it shows up in logs.
- [Keycloak unreachable] → requests that need staffing fail with `500`/`503` like the account
  endpoints; publishers are unaffected (no lookup).
- [Staffing changes between display and action] → the action re-evaluates; worst case the user
  sees `403`/`409` and the refreshed `allowedActions`.
- [Locked sole holder keeps a level "staffed"] → accepted: higher roles can always approve
  (cumulative), so no article is stuck; publishers cannot be locked.
- [Behaviour change of `PUBLISH`] → today it is offered only to publisher-authors, whose chain is
  empty, so no currently offered action disappears; only `SUBMIT` is new.

## Migration Plan

Deploy the new image; Flyway runs `V6` (additive). Rollback: the previous image ignores the new
column and table; rows left in `SUBMITTED` would show as submitted drafts that the old code cannot
act on — withdraw or approve pending submissions before rolling back.
