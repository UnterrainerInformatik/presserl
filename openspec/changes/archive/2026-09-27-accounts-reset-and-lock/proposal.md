## Why

Accounts can be created, but once a child has lost the slip or forgotten the pass-phrase, nobody
can help without the Keycloak admin console, and a publisher cannot shut an account out. Password
reset "by anyone above the person" and locking by publishers are the remaining account-care
pieces of M2 promised in `docs/roles-and-workflow.md`, and every running newspaper needs them.

## What Changes

- New `POST /api/accounts/{id}/password-reset`: sets a newly generated four-word pass-phrase (not
  temporary), ends the account's Keycloak sessions and answers `200` with the account and the
  password, exactly like the creation response. Works on locked accounts, which stay locked.
- New `POST /api/accounts/{id}/lock` and `POST /api/accounts/{id}/unlock` for publishers only:
  lock disables the Keycloak user and ends its sessions, unlock enables it again. Both answer
  `200` with the account and are idempotent.
- **Reset rule "only someone above"**: rank publisher > editor-in-chief > section editor >
  reporter/reader. A publisher resets every account except publishers; an editor-in-chief every
  account holding neither `PUBLISHER` nor `EDITOR_IN_CHIEF`; a section editor only accounts with
  no newspaper role above `READER`, no `SECTION_EDITOR` role anywhere and at least one section
  role, all of them `REPORTER` in the section editor's own sections.
- **Lock rule**: publishers lock and unlock every account except publishers. Nobody resets or
  locks their own account.
- `GET /api/accounts` lists `allowedActions` per account (`RESET_PASSWORD`, `LOCK`, `UNLOCK`) —
  additive; clients render buttons from it and never re-implement the rules.
- Admin app: per-account actions "Reset password", "Lock", "Unlock" with a confirmation dialog;
  after a reset the same printable account slip as after creation; locked accounts marked
  "locked"; German and English texts.
- Contract, primer, `.http` files, `docs/architecture.md` (unlock route) and
  `ai/open-proposals.md` updated.

## Non-goals

- Changing newspaper roles of existing accounts; deleting accounts or sections.
- Self-service password change in the admin app (users use the Keycloak account console) and any
  "forgot password" flow.
- Revoking already issued access tokens: they stay valid until they expire (Keycloak default a
  few minutes); only refreshing stops immediately.
- A reason or audit trail for locks beyond the INFO log line.
- Printing several slips at once.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `accounts`: `allowedActions` in the account list; new password-reset, lock and unlock
  endpoints with their delegation rules.
- `admin-accounts`: account row actions with confirmation, slip after a reset, "locked" marker.

## Impact

- **backend**: `account` package — new `AccountAction` enum and `AccountPolicy` (rank and scope
  rules), `AccountService` gains reset/lock/unlock/session logout on the Keycloak Admin API,
  `AccountResource` gains three endpoints and computes `allowedActions`; `AccountDto` extended;
  tests (`AccountPolicyTest`, `AccountResourceTest`).
- **admin**: `AccountDto` and `ApiClient`, account list row actions and confirmation dialog, slip
  screen reused for resets, German/English strings; tests.
- **contract/docs**: `ai/primer/endpoints.md`, `http/accounts.http`, `docs/architecture.md`,
  `ai/open-proposals.md`.
- **reader, deploy**: none (the backend's service account already manages users; verify the
  realm template grants `manage-users`, which session logout needs).
