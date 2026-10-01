## 1. Backend — data and deletion requests

- [x] 1.1 Migration `V18__account_deletion.sql`: table `account_deletion_request`, drop `NOT NULL` on the username/display-name snapshot columns of `article`, `article_revision`, `article_review`, `media` (D1, D2)
- [x] 1.2 Entity + repository for deletion requests; `POST`/`DELETE /api/me/deletion-request` (idempotent, first time kept, INFO log, service account `403`) and `deletionRequestedAt` in `GET /api/me` (D6)
- [x] 1.3 Tests: request, repeat keeps the time, withdraw, reader-only account, service account `403`, `GET /api/me` field

## 2. Backend — deleting accounts

- [x] 2.1 `AccountPolicy`: `DELETE` rule incl. publisher-with-request and other-enabled-publisher check (D5); `allowedActions` order `…, LOCK, UNLOCK, DELETE`; `deletionRequestedAt` in `AccountDto`
- [x] 2.2 `AccountDataEraser.erase(sub)`: withdraw pending submissions of authored articles, null name snapshots, delete section roles / marker / trust on the account / request (D3 steps 2–4, D4)
- [x] 2.3 `DELETE /api/accounts/{id}`: one transaction, Keycloak delete last, `204`/`403`/`404`/`503`, INFO log (D3)
- [x] 2.4 DTOs: author, revision author, reviewer, uploader names nullable; `null` passes through article, review and media endpoints
- [x] 2.5 Tests: every scenario of the `accounts` delta (allowed actions incl. locked and publisher cases, delete reporter, editor-in-chief refused, own account, publisher with/without request, last publisher, Keycloak failure rolls back)
- [x] 2.6 Tests: anonymisation — published article stays live with `null` names, pending submission withdrawn and gone from "Waiting for me", image kept with `null` uploader, review kept with `null` reviewer, trust set by the deleted account stays, erase is idempotent

## 3. Reader

- [x] 3.1 Byline `null` → `byline.former` ("ehemaliges Redaktionsmitglied" / "former newsroom member") on front, article, issue and print pages
- [x] 3.2 `GET /account-deletion` page + template + de/en messages, public like `/legal-notice`, link to `/legal-notice` only when a notice exists (D8)
- [x] 3.3 Link "Konto löschen" / "Delete an account" at the end of `/legal-notice`
- [x] 3.4 Tests: byline of a deleted author (de/en), page with and without legal notice, private newspaper anonymous access, cache headers, link on the legal notice

## 4. Admin app

- [x] 4.1 API client/DTOs: deletion request calls, `deletionRequestedAt` in me/account models, `DELETE` action, delete call; nullable author/reviewer/uploader names
- [x] 4.2 Header user line as button opening "My account", "deletion requested" marker (admin-shell delta)
- [x] 4.3 "My account" screen: request / withdraw with confirmations, publisher explanation, way back; de/en strings
- [x] 4.4 Account list: pending requests first with marker and date, "Delete" with the confirmation text of the spec, removal from the list, error message on refusal
- [x] 4.5 `authorLabel` helper for `null` names in article list, editor (byline, last changed by, review history) and media views
- [x] 4.6 Kotlin tests: list ordering and marker, action mapping incl. `DELETE`, `authorLabel`, My-account state transitions (fake API client)

## 5. Contract and docs

- [x] 5.1 `ai/primer/endpoints.md`: new endpoints, `deletionRequestedAt`, `DELETE` in `allowedActions`, nullable name fields meaning "deleted account", access-token remark
- [x] 5.2 `http/`: requests for request/withdraw/`GET /api/me`/delete incl. refused cases; run them against `quarkus:dev`
- [x] 5.3 `docs/roles-and-workflow.md`: deletion requests and deleting next to locking
- [x] 5.4 `deploy/INSTALL.md`: deleting the last publisher in the Keycloak admin console; recovery SQL for one sub (D4)
- [x] 5.5 `docs/play-console.md`: Data safety deletion answer, account-deletion URL, "Open points" entry closed

## 6. Homepage (external repository)

- [x] 6.1 `../../JAVASCRIPT/homepage`: page `/app/presserl/account-deletion` (de/en) per D9; privacy policy "Your rights" links it; build the site locally

## 7. Verification

- [x] 7.1 `./mvnw verify` (backend) and `./gradlew check` (admin) green
- [x] 7.2 Admin web app headless against `quarkus:dev`: reporter requests deletion, publisher sees it first and deletes, byline in the reader shows "ehemaliges Redaktionsmitglied", deleted login fails
- [x] 7.3 Android (A54) against `quarkus:dev`: "My account" request/withdraw works on the phone
- [x] 7.4 After deployment: `https://presserl.unterrainer.info/account-deletion` and the homepage page reachable from outside (staging checked 2026-10-01 via the public IP; homepage live 2026-10-01 after pin-deploys-to-babylon5, German and English verified headless)
