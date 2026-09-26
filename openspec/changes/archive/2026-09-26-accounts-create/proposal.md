## Why

After M1 the newspaper has exactly one account — the bootstrapped publisher. Stage 2 of the
family example ("parents supervise", see `docs/roles-and-workflow.md`) needs a second person,
and nobody should have to open the Keycloak admin console to create one. This is the first of
the M2 changes: it lets publishers and editors-in-chief create accounts with a newspaper-wide
role and hand them over on a printed slip.

## What Changes

- New endpoints under `/api/accounts` (publishers and editors-in-chief):
  - `GET /api/accounts` — all accounts of the realm with their newspaper roles and whether they
    are enabled, plus the roles the requesting user may assign.
  - `GET /api/accounts/username-suggestion?firstName=…` — username derived from the first
    name (lower case, ASCII-folded, `-2`, `-3`… on collision).
  - `POST /api/accounts` — creates a Keycloak user with first name, optional last name,
    username, one or more newspaper roles (Keycloak groups) and a generated default password;
    the response carries that password exactly once.
- Default password: four words from a curated, kid-friendly German word list, joined by
  dashes (`tiger-wolke-apfel-leiter`), generated server-side with a secure random source.
- Delegation for newspaper-wide roles: publishers may assign `PUBLISHER`, `EDITOR_IN_CHIEF`,
  `READER`; editors-in-chief may assign `EDITOR_IN_CHIEF`, `READER`.
- Admin app: new "Accounts" screen reachable from the header — account list, "New account"
  form (username prefilled from the first name, editable), and after creation a printable
  account slip (newspaper name, web address, username, password).
- `ApiErrorDto`/`FieldError` become the shared error body for all API resources, not only
  articles (no wire change).
- Housekeeping: `ai/open-proposals.md` loses the finished M1 block; the M2 entry is trimmed to
  what remains after this change.

## Non-goals

- Sections, per-section roles (`SECTION_EDITOR`, `REPORTER`) and section-scoped delegation —
  next M2 change.
- Changing the roles of an existing account, password reset, locking/unlocking, deleting
  accounts — later M2 changes.
- Extending `GET /api/me` (scopes, allowed actions).
- QR code on the slip (M8), self-registration, e-mail of any kind.
- Forcing a password change on first login.

## Capabilities

### New Capabilities
- `accounts`: backend account management through the Keycloak service account — listing
  accounts, username derivation, pass-phrase generation, account creation with newspaper-wide
  roles and the delegation rule for those roles.
- `admin-accounts`: admin app screens for the account list, account creation and the printable
  account slip.

### Modified Capabilities
<!-- none: the admin header gains an entry point, specified in admin-accounts -->

## Impact

- **backend**: new `account` package (resource, service on top of the existing Keycloak Admin
  client, username deriver, pass-phrase generator, German word list resource); error DTOs move
  to a shared package; `@QuarkusTest`s against Keycloak Dev Services.
- **admin**: API client methods and DTOs, accounts screen, creation form, slip view with
  browser printing, German/English strings.
- **reader**: none.
- **deploy**: none — the realm template already has the groups and the service account holds
  `manage-users`, `view-users`, `query-users`, `query-groups`.
- **docs / ai**: `ai/primer/endpoints.md` gains the account endpoints; `http/accounts.http`;
  `ai/open-proposals.md` updated.
