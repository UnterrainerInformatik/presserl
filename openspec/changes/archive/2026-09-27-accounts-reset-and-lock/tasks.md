## 1. Backend — policy and account shape

- [x] 1.1 `AccountAction` enum (`RESET_PASSWORD`, `LOCK`, `UNLOCK`) and pure `AccountPolicy.allowedActions(Newsroom, AccountDto)` per design D1
- [x] 1.2 `AccountPolicyTest`: self, publisher target, publisher/editor-in-chief/section editor/reporter/reader requesters, section editor with reporter in own + foreign section, target that is `SECTION_EDITOR`, plain reader, roleless account, lock vs. unlock by `enabled`
- [x] 1.3 `AccountDto.allowedActions` (additive); `GET /api/accounts` and `POST /api/accounts` fill it for the requesting user

## 2. Backend — endpoints

- [x] 2.1 `AccountService.find` returns newspaper roles (user groups); new blocking `resetPassword(id, password)`, `setEnabled(id, boolean)`, `logout(id)` via `keycloakCall` (D3)
- [x] 2.2 `AccountException.notFound`; mapper answers `404` with the shared error body
- [x] 2.3 `AccountResource`: `POST /{id}/password-reset` (`200 CreatedAccountDto`), `POST /{id}/lock`, `POST /{id}/unlock` (`200 AccountDto`) with the D3 flow: access rule, `404`, policy `403` with error body, idempotent lock/unlock, session logout, INFO logs without password
- [x] 2.4 `AccountResourceTest` (`@QuarkusTest`): scenarios of the `accounts` delta spec — allowedActions per requester, reset (new password logs in, old fails, prior refresh token rejected), reset of a locked account, lock/unlock (login blocked/restored, refresh token rejected), idempotent lock, `403` cases (editor-in-chief lock, publisher → publisher, own account, section editor scope, reader), `404` unknown id and service account; restore test users' state afterwards

## 3. Admin

- [x] 3.1 `AccountDto.allowedActions` (default empty) and `ApiClient.resetPassword`, `lock`, `unlock`; `DtoTest`/`ApiClientTest` for the new calls and fields
- [x] 3.2 `AccountListModel` (pending confirmation, running request, error, row replacement, reset result) with `AccountListModelTest`: cancel sends nothing, confirmed lock replaces the row, `403` shows an error and keeps the list, reset yields the slip data
- [x] 3.3 `AccountRow` action buttons from `allowedActions`, confirmation dialog naming the account (reset text: old password stops working), "locked" marker replacing "disabled"
- [x] 3.4 Navigation: reset success → `AccountSlipScreen` with the returned account and password; "Done" → reloaded list
- [x] 3.5 German and English strings for actions, dialogs, marker and errors; `LabelsTest` extended if it covers key parity

## 4. Contract / Docs

- [x] 4.1 `ai/primer/endpoints.md`: `allowedActions` on the account shape (list and create), sections for password-reset, lock, unlock with auth rules, errors, side effects and the access-token note
- [x] 4.2 `http/accounts.http`: reset, lock, lock again, unlock, refused lock as editor-in-chief, unknown id
- [x] 4.3 `docs/architecture.md`: add `POST /api/accounts/{id}/unlock`, state reset rule (never publishers) and lock rule; `docs/roles-and-workflow.md`: reset excludes publishers, locking excludes publishers and self
- [x] 4.4 `ai/open-proposals.md`: remove password reset and lock/unlock from M2, fix the stale "articles in sections are proposed" sentence

## 5. Verification

- [x] 5.1 Backend account tests (`AccountPolicyTest`, `AccountResourceTest`, `RoleDelegationTest`) and admin tests green (commands per `reference_build_and_test.md`)
- [x] 5.2 Run `http/accounts.http` against a live dev backend
- [x] 5.3 Drive the admin app headless (Playwright): lock/unlock `reader` with confirmation, reset `reader` and see the slip, `chief` sees only "Reset password"; stop every server started
