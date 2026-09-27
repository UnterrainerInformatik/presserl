## Why

Parents act as publishers and need an emergency brake: when they take an article offline, it must
stay offline until a publisher decides otherwise (`docs/roles-and-workflow.md`). Today this holds
only by accident — the `PUBLISHER` level happens to be staffed for every author without
`PUBLISHER`. As soon as trust switches let a level be skipped, a trusted child could bring an
article back online that a parent took down. The lock makes the rule explicit and has to exist
before trust does; the API also has to tell clients that an article is locked.

## What Changes

- Taking a published article offline as a user holding `PUBLISHER` **locks** it (emergency brake).
  Taking offline by anyone else behaves as today.
- While an article is locked, the `PUBLISHER` level is always part of its chain for an author
  without `PUBLISHER`, whether or not it is staffed (and, later, whether or not it trusts the
  author). So the author cannot publish it directly; a submission — also one pending when the lock
  is set — reaches a publisher, and lower approvals only move it up to `PUBLISHER`.
- The lock ends when the article goes online (publish by a publisher-author, or a publisher's
  approval).
- New action `UNLOCK` / `POST /api/articles/{id}/unlock`: a publisher lifts the lock without putting
  the article online; afterwards the ordinary chain applies again.
- Article and article summary representations carry `locked` (boolean).
- Admin app: the list and the editor show that an article is locked; the editor offers "Unlock"
  exactly when `allowedActions` contain `UNLOCK`.
- Docs: `docs/architecture.md` and `docs/roles-and-workflow.md` no longer describe the lock as
  planned; `ai/primer/endpoints.md` documents `locked`, `UNLOCK` and the endpoint.

## Non-goals

- Locking an article that is already `OFFLINE` (taken offline by someone else) — the publisher would
  have to wait for it to be back online; can be added later if needed.
- Recording who locked the article or when, or a history of locks.
- Notifications to the author.
- Trust switches and the review queue (separate M3 changes).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `articles`: taking offline by a publisher sets the lock; `allowedActions` gains `UNLOCK`;
  representations carry `locked`; new unlock endpoint.
- `approval-chain`: the `PUBLISHER` level is mandatory in the chain of a locked article; going
  online clears the lock.
- `admin-articles`: lock indicator in list and editor, Unlock action.

## Impact

- **backend**: Flyway migration `V7` (column `article.locked`, check that only `OFFLINE` articles are
  locked); `ArticleEntity`, `ArticleAction` (`UNLOCK`), `ArticlePolicy`, `ApprovalChain.next`,
  `ArticleService` (take offline, go live, unlock), `ArticleResource`, `ArticleDto`,
  `ArticleSummaryDto`; tests.
- **admin**: DTOs (`locked`), API client (`unlock`), editor actions, list and editor labels; tests.
- **reader**: none (offline articles are not shown anyway).
- **docs / primer / http**: `ai/primer/endpoints.md`, `docs/architecture.md`,
  `docs/roles-and-workflow.md`, `http/articles.http`.
- REST contract: additive (`locked` field, `UNLOCK` action value, new endpoint); clients that
  ignore unknown actions keep working.
