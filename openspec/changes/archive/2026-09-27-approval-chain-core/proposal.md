## Why

Today only a publisher can publish, and only their own articles; editors-in-chief, section editors
and reporters can write but never get an article online. The approval chain described in
`docs/roles-and-workflow.md` is what turns Presserl from a solo newspaper into the family
newspaper of growth stages 2–4 (parents approve the child's articles), and it is the first thing
the first real deployment (M7) needs.

## What Changes

- **Approval chain (backend).** An author submits an article; the server computes the levels it
  must pass — section editor of the article's section → editor-in-chief → publisher — starting
  directly above the author's highest role in that section. A level is skipped when nobody other
  than the author holds its role (this generalises the documented "section without section editor"
  rule to every level, so no article waits for a level nobody can fill). When no level remains,
  the article is published at once.
- **Cumulative approval.** Any user other than the author who holds the waiting level's role or a
  higher one may approve or reject. An approval settles every level up to and including the
  approver's highest level; the article then waits for the next applicable level above, or is
  published when none remains. A publisher's approval therefore always publishes.
- **Rejection with a note.** Reject requires a note; the submission ends, a never-published article
  returns to `DRAFT`, a published or offline article keeps its status and live revision.
- **Withdraw.** The author may withdraw a submission. While a submission is pending the article's
  content and section are frozen (`EDIT` is not offered).
- **Submissions of published articles.** Changes to a published article, and bringing an
  offline article back online, go through the same chain. Such an article keeps its status
  (`PUBLISHED` or `OFFLINE`) and the reader keeps showing the live revision while the submission is
  pending; `SUBMITTED` is used only for articles that were never published.
- **Contract (additive).** `ArticleDto`/`ArticleSummaryDto` gain `pendingLevel`
  (`SECTION_EDITOR` | `EDITOR_IN_CHIEF` | `PUBLISHER` | `null`). New endpoints
  `POST /api/articles/{id}/submit`, `/approve`, `/reject` (body `{"note": …}`), `/withdraw` and
  `GET /api/articles/{id}/reviews` (approvals and rejections, newest first). `allowedActions` gains
  `SUBMIT`, `APPROVE`, `REJECT`, `WITHDRAW`. `GET /api/articles` gains the filter `pending=true`.
- **BREAKING (behaviour):** `PUBLISH` is no longer tied to the `PUBLISHER` role. It is offered to
  the author exactly when the chain for them is empty (today: an author holding `PUBLISHER`, since
  another publisher always exists otherwise; with trust, later, more authors); otherwise the author
  gets `SUBMIT`. `POST …/publish` answers `403` when a level applies. The response shape
  is unchanged; the admin app is updated in the same change.
- **Admin app.** The editor shows Submit, Approve, Reject (with a note dialog) and Withdraw from
  `allowedActions`, shows "waiting for approval by …" from `pendingLevel`, and lists the article's
  reviews with their notes. The article lists show the pending level next to the status.
- **Docs.** `docs/roles-and-workflow.md` and `docs/architecture.md` describe the generalised skip
  rule, cumulative approval, withdraw, frozen content while pending and `pendingLevel` on
  published/offline articles; `ai/primer/endpoints.md` and `http/articles.http` follow the
  contract.

## Capabilities

### New Capabilities

- `approval-chain`: submitting, approving, rejecting and withdrawing articles; level computation
  and skip rules; the reviews record.

### Modified Capabilities

- `articles`: publishing no longer requires `PUBLISHER` but an empty chain; editing and moving are
  frozen while a submission is pending; `allowedActions` gains the chain actions; representations
  and the list filter gain `pendingLevel`/`pending`; status `SUBMITTED` is now written.
- `admin-articles`: editor actions for submit/approve/reject/withdraw, pending-level display in
  editor and lists, reviews shown in the editor.

## Non-goals

- **Trust switches** (per-person skip of a level) — next M3 change; the chain is built so trust
  becomes one more skip rule.
- **Review queue** (a dedicated "waiting for me" view, counts, notifications) — later M3 change;
  approvers find pending articles via the lists and the `pending=true` filter.
- **Emergency-brake lock** — later M3 change; taking offline stays unchanged and never locks.
- No comments on individual passages, no diff view between revisions, no approval of a specific
  older revision (always the latest revision is approved).
- No configuration keys: the chain follows roles only.
- No change to the reader: it keeps showing `PUBLISHED` articles with their live revision.

## Impact

- **Backend:** `article` package — `ArticlePolicy` (new actions, publish rule), `ArticleService`
  (submit/approve/reject/withdraw), new chain calculator and level enum, `ArticleEntity`
  (`pendingLevel`, author's level at submit), new review entity and DTO, `ArticleResource`
  endpoints, list filter; lookup of newspaper-role holders via the Keycloak Admin client
  (`account` package) and of section editors via the DB. Flyway migration `V6`. JUnit/AssertJ and
  `@QuarkusTest` coverage.
- **Reader:** none (query stays `status = PUBLISHED` on the live revision).
- **Admin:** `Dtos.kt`, `ApiClient.kt`, editor actions/screen/model, list entries, labels and
  de/en strings; Kotlin tests.
- **Deploy:** none (no new settings; the Keycloak service account already reads group members).
- **Docs/contract:** `ai/primer/endpoints.md` (Articles section), `docs/roles-and-workflow.md`,
  `docs/architecture.md`, `docs/diagrams/review-decision.puml`/`article-lifecycle.puml` (+ SVG) if
  they contradict the generalised rules, `http/articles.http`, `ai/open-proposals.md` (M3 entry
  reduced to trust, review queue and emergency brake — done at propose time).
- **Compatibility:** response fields and endpoints are additive; clients that render from
  `allowedActions` keep working. Existing `SUBMITTED` rows do not exist (never written).
