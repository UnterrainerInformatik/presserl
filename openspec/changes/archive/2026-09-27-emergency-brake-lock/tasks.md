## 1. Backend

- [x] 1.1 Add Flyway migration `V7__article_lock.sql`: `article.locked BOOLEAN NOT NULL DEFAULT false` with `CHECK (NOT locked OR status = 'OFFLINE')`; add `locked` to `ArticleEntity`
- [x] 1.2 Extend `ApprovalChain.next` with the `locked` parameter (PUBLISHER qualifies while locked) and pass `article.locked` from `ArticlePolicy.chainIsEmpty`, `ArticleService.submit` and `ArticleService.approve`
- [x] 1.3 Add `ArticleAction.UNLOCK` (between `TAKE_OFFLINE` and `DELETE`) with its verdict in `ArticlePolicy` (403 without `PUBLISHER`, 409 when not locked), update the policy's Javadoc table and the 403/409 messages in `ArticleService.require`
- [x] 1.4 `ArticleService`: set the lock in `takeOffline` when the user holds `PUBLISHER`, clear it in `goLive`, add `unlock`; add `POST /api/articles/{id}/unlock` to `ArticleResource`
- [x] 1.5 Add `locked` to `ArticleDto` and `ArticleSummaryDto` (after `pendingLevel`)
- [x] 1.6 Unit tests: `ApprovalChain.next` with lock (unstaffed PUBLISHER, publisher-author, approval above lower levels) and `ArticlePolicy` verdicts for `UNLOCK`, `PUBLISH`/`SUBMIT` on locked articles
- [x] 1.7 `@QuarkusTest` integration tests for the spec scenarios: brake by publisher, no lock by others, unlock (200/403/409/404), allowedActions on locked articles, publisher-author republishes, reporter's locked article through the chain back online with `locked` `false`

## 2. Admin

- [x] 2.1 Add `locked: Boolean = false` to the Kotlin `ArticleDto` and `ArticleSummaryDto`; add `ApiClient.unlock(id)`
- [x] 2.2 `EditorActions.unlock` from `UNLOCK`; editor shows the lock banner and an Unlock button wired to the API, showing the returned state
- [x] 2.3 List entry shows a "Locked" line for `locked` articles; add German (`values`) and English (`values-en`) strings
- [x] 2.4 Kotlin tests: DTO decoding of `locked` (present and absent), `actionsFor` with `UNLOCK`

## 3. Contract / Docs

- [x] 3.1 `ai/primer/endpoints.md`: `locked` in `ArticleDto`/summary, `UNLOCK` in the action table and order, lock on take-offline, new `POST /api/articles/{id}/unlock` section, chain rule for locked articles
- [x] 3.2 `http/articles.http`: take offline as publisher and unlock requests
- [x] 3.3 `docs/architecture.md` (emergency-brake lock no longer "later", unlock endpoint, drop "`offline` does not lock") and `docs/roles-and-workflow.md` (unlock by a publisher)
- [x] 3.4 Remove the emergency-brake entry from `ai/open-proposals.md`

## 4. Verification

- [x] 4.1 Run backend tests for the article/approval packages and the admin tests (`reference_build_and_test.md`)
- [x] 4.2 Run the new `.http` requests against a live dev backend
- [x] 4.3 Drive the admin app headless: publisher takes an article offline, sees the lock and unlocks it; stop every server started
