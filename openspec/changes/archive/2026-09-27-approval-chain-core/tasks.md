## 1. Backend — model and chain

- [x] 1.1 Flyway `V6__approval_chain.sql` per design D6 (`pending_level`, `article_submitted_pending`, partial index, `article_review`); `ArticleEntity.pendingLevel`; update the `ArticleStatus` Javadoc (`SUBMITTED` = never published and waiting)
- [x] 1.2 `ApprovalLevel` enum and pure `ApprovalChain` (`authorLevel`, `approverLevel`, `next`) per D2
- [x] 1.3 `Staffing` record with `staffed(level, sectionId, authorSub)` and `NOT_NEEDED`; section-editor holders query in `SectionRoleStore`; `RoleHolders` bean in `account` reading `editor-in-chief`/`publisher` group member ids through `KeycloakCalls`; `StaffingService.forArticles` / `forApproval` with the lazy rule of D3
- [x] 1.4 Unit tests `ApprovalChainTest`: author levels (reporter, section editor in own/foreign section, editor-in-chief, publisher), `next` with every staffing combination (unstaffed section-editor level, unstaffed editor-in-chief level, author as sole holder, locked holder counted), empty chain for publishers
- [x] 1.5 `ArticleReviewEntity`, `ReviewDto` (+ `ReviewerDto` or reuse `AuthorDto` shape) and `ReviewDecision` enum

## 2. Backend — policy, service, endpoints

- [x] 2.1 `ArticleAction` gains `SUBMIT`, `WITHDRAW`, `APPROVE`, `REJECT` in the order of D4; `ArticlePolicy.verdict/allowedActions` take `Staffing` and implement the D4 table (EDIT/PUBLISH conflict while pending, PUBLISH by empty chain); update the class Javadoc table and `ArticleService.require` messages
- [x] 2.2 Extend `ArticlePolicyTest` for every row of D4, including the spec scenarios of "Server-computed allowed actions" (reporter draft, reporter submitted, section editor on waiting article, publisher unchanged)
- [x] 2.3 `ArticleService.submit/approve/reject/withdraw` per D5 with a shared private publish step; `delete` keeps working while pending (reviews cascade); `save` refuses while pending (via policy)
- [x] 2.4 Reject body validation (`note`: object with only `note`, trimmed, 1–1000 code points, `\n` allowed, no other control characters) with field errors like `ArticleContentValidator`; unit tests
- [x] 2.5 `ArticleResource`: `POST …/submit`, `…/approve`, `…/reject`, `…/withdraw`, `GET …/reviews`, `pending` query parameter; load `Staffing` for the returned views and pass it to `ArticleDto.of`/`ArticleSummaryDto.of`; `pendingLevel` in both DTOs
- [x] 2.6 `ArticleService.list` supports `pending`; `GET …/reviews` returns newest first and `404` for invisible articles
- [x] 2.7 `@QuarkusTest` `ApprovalChainResourceTest` covering the approval-chain spec scenarios: reporter → section editor → editor-in-chief → publisher; section without section editor; higher role approves lower level; changes to a published article (reader keeps revision 1 until approval, then 2); offline back online via chain; publisher cannot submit; submit twice / without headline; save while pending `409`; author promoted to publisher cannot approve own; level too low `403`; foreign section `404`; nothing to approve `409`; reject draft / published / blank note; withdraw by author and by others; reviews listing and `404`; staffing change takes effect at once; take offline keeps `pendingLevel`; delete submitted article; `pending=true` filter; version increases
- [x] 2.8 Adjust existing `ArticleResourceTest` expectations that change (reporter draft now lists `SUBMIT`; editor-in-chief publish still `403`) and run `ReaderArticlesTest` unchanged
- [x] 2.9 Fix `ReaderFixtures` (pre-existing since V5 made `article.section_id` `NOT NULL`): insert fixture articles into the first section by position, creating one when none exists, and give `SUBMITTED` fixtures a `pending_level` (required by `article_submitted_pending`), so the reader suites run again

## 3. Admin

- [x] 3.1 `Dtos.kt`: `pendingLevel` on `ArticleDto`/`ArticleSummaryDto` (default `null`), `ReviewDto`; `ApiClient`: `submit`, `approve`, `reject(note)`, `withdraw`, `reviews`; extend `DtoTest` and `ApiClientTest`
- [x] 3.2 `Actions.kt`: `submit`, `withdraw`, `approve`, `reject` from `allowedActions`; tests for each combination the spec names
- [x] 3.3 `EditorModel`/`EditorScreen`: buttons per D8 (Submit saves first like Publish), reject dialog with blank-check and server `note` error, pending notice, reviews panel and rejection banner; reload reviews after every action; `EditorModelTest` coverage
- [x] 3.4 Article list entries show "waits for approval by …" when `pendingLevel` is set
- [x] 3.5 Strings (de `values`, en `values-en`): submit, withdraw, approve, reject, reject dialog title/hint/confirm, pending notice, reviews heading, decision labels; `LabelsTest` for the pending-level label mapping

## 4. Contract/Docs

- [x] 4.1 `ai/primer/endpoints.md`: `pendingLevel` in `ArticleDto`/summary, `allowedActions` table and order, new endpoints (submit, approve, reject, withdraw, reviews) with errors and side effects, `pending` filter, status note (`SUBMITTED` now written), versions paragraph, drop "cannot publish yet (M3)"
- [x] 4.2 `http/articles.http`: chain walk-through with reporter (`reader`), section editor (`nogroups`), editor-in-chief (`chief`) and publisher, including reject with note, withdraw and reviews
- [x] 4.3 `docs/roles-and-workflow.md` and `docs/architecture.md` per design D9
- [x] 4.4 `docs/diagrams/review-decision.puml` and `article-lifecycle.puml` per D9; render SVGs via `plantuml.unterrainer.info` (`-L`, `charset=utf-8`)
- [x] 4.5 `ai/open-proposals.md`: M3 entry reduced to trust switches, review queue and emergency brake (done at propose time)

## 5. Verification

- [x] 5.1 Backend: `./mvnw test -Dtest='ApprovalChain*,ArticlePolicyTest,ArticleResourceTest,ReaderArticlesTest'` plus the migration test class; then the article/section suites
- [x] 5.2 Admin: `./gradlew check`
- [x] 5.3 Run `http/articles.http` against a live `quarkus:dev`
- [x] 5.4 Headless admin check (Playwright): reporter submits, section editor approves, editor-in-chief rejects with note, reporter sees the banner, resubmits, publisher approves and the article appears in the reader; stop every server started
