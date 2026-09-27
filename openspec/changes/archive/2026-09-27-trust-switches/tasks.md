## 1. Backend

- [x] 1.1 Add Flyway migration `V8__trust.sql` (table `trust` as in design.md: level check, nullable `section_id` with `ON DELETE CASCADE`, section-iff-`SECTION_EDITOR` check, `UNIQUE NULLS NOT DISTINCT (account_id, level, section_id)`)
- [x] 1.2 New package `trust`: `TrustEntity`, `TrustScope(level, sectionId)`, `TrustStore` (`scopesOf`, `byAccount` ordered PUBLISHER, EDITOR_IN_CHIEF, sections by position, `set` insert-unless-present returning whether it changed and tolerating a racing duplicate, `clear`)
- [x] 1.3 `Staffing`: add `trusts` component and `trusts(level, sectionId, authorSub)`; widen the Javadoc; `NOT_NEEDED` still throws
- [x] 1.4 `StaffingService`: `forArticles` loads the requester's trust, `forApproval(authorSub)` loads the author's; update `ArticleService.approve` accordingly
- [x] 1.5 `ApprovalChain.next`: skip levels that trust the author (lock branch first); update class Javadoc
- [x] 1.6 `TrustPolicy`: trust levels of a requester, `mayGrant`, ordered `scopes(requester, target, trusts)` per the accounts spec
- [x] 1.7 `AccountDto`: add `trusts` and `trustScopes` (before `allowedActions`); `withTrusts(...)`; `withAllowedActionsFor` also fills `trustScopes`; load trust in `GET /api/accounts` (`TrustStore.byAccount`) and in `AccountResource.target(...)` so create, roles, lock/unlock and password-reset responses carry both fields
- [x] 1.8 `AccountRequestValidator.validateTrust` (level, sectionId required iff `SECTION_EDITOR` and existing, `trusted` boolean, unknown fields rejected, every violation reported)
- [x] 1.9 `PUT /api/accounts/{id}/trust` in `AccountResource`: load target with trusts, 403 error body unless the scope is in `TrustPolicy.scopes`, `set`/`clear`, INFO log only on change, answer the account for the requester
- [x] 1.10 Unit tests: `ApprovalChainTest` (trusted SE per section only, trusted EIC/PUBLISHER, fully trusted → empty chain, lock overrides trust, approval skips trusted levels above); `TrustPolicyTest` (scopes for publisher, editor-in-chief, section editor, EIC who is also SE, self, not-below targets, stale entries clearable, locked accounts)
- [x] 1.11 `@QuarkusTest` integration tests for the spec scenarios: trust endpoint (set, clear by another holder, idempotent set keeps setter, 400 cases, 403 cases, 404), `trusts`/`trustScopes` in `GET /api/accounts`, section delete cascades, chain effects (direct publish after trust, SE trust skips to EIC, other section unaffected, pending article keeps waiting, withdraw then PUBLISH offered, lock overrides trust)

## 2. Admin

- [x] 2.1 `Dtos.kt`: `TrustScopeDto(level, sectionId: Long?)`; `AccountDto.trusts` and `trustScopes` defaulting to `emptyList()`; `ApiClient.setTrust(id, level, sectionId, trusted)`
- [x] 2.2 `AccountListModel`: `requestTrust(account, scope, on)` — confirmation when turning on, immediate request when turning off; replace the account on success, error message and unchanged state on failure; unknown levels ignored
- [x] 2.3 `AccountScreens`: "trusted by" markers (level label, "· section" for section editor) and one switch per `trustScopes` entry; confirmation dialog naming the account and level
- [x] 2.4 German (`values`) and English (`values-en`) strings for markers, switch labels and the confirmation
- [x] 2.5 Kotlin tests: DTO decoding with and without the new fields, `ApiClient.setTrust` request body, `AccountListModel` trust flow (confirm, cancel, off without confirmation, 403 keeps state)

## 3. Contract / Docs

- [x] 3.1 `ai/primer/endpoints.md`: `trusts`/`trustScopes` in `AccountDto` and every account response example, trust rules table, new `PUT /api/accounts/{id}/trust` section, chain rule (trusted levels skipped, lock wins, pending submissions not moved)
- [x] 3.2 `http/accounts.http`: set trust, clear trust, refused and invalid requests
- [x] 3.3 `docs/roles-and-workflow.md` (trust no longer "planned"; own highest level only, per section for section editors, clearable by any holder, pending submissions not moved, EIC-as-SE limitation) and `docs/architecture.md` (trust endpoint implemented, data model `Trust` with section, drop "trust is not implemented yet")

## 4. Verification

- [x] 4.1 Run backend tests for the article, account and trust packages and the admin tests (`reference_build_and_test.md`)
- [x] 4.2 Run the new `.http` requests against a live dev backend
- [x] 4.3 Drive the admin app headless: publisher trusts `chief` (with confirmation), `chief`'s draft then offers Publish; publisher clears trust; stop every server started
