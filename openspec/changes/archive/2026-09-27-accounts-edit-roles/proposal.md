## Why

Roles of an existing account can today only be changed per section (`/api/sections/{id}/members`);
newspaper roles (`PUBLISHER`, `EDITOR_IN_CHIEF`, `READER`) cannot be changed at all without the
Keycloak admin console. When a family member becomes editor-in-chief, stops being one, or moves
between sections, the publisher needs one place to change everything the person holds.

## What Changes

- New `PUT /api/accounts/{id}/roles` with `roles` and `sectionRoles` replaces an account's
  newspaper roles (Keycloak groups) and section roles (database) together, all or nothing.
- Who may edit follows the password-reset rule ("ranks above the person"): publishers every
  account not holding `PUBLISHER`, editors-in-chief accounts holding neither `PUBLISHER` nor
  `EDITOR_IN_CHIEF`, section editors pure reporters of their own sections; nobody their own account.
- Every role that is added or removed must be assignable by the requesting user (newspaper roles
  at or below the own level, section roles within the own scope); unchanged roles need no
  permission. The result must keep at least one role of either kind.
- `allowedActions` gains `EDIT_ROLES`; the order becomes `EDIT_ROLES`, `RESET_PASSWORD`, `LOCK`,
  `UNLOCK` (additive for clients that ignore unknown values; exact lists in scenarios change).
- Newspaper-role changes take effect for the person with their next token refresh (a few minutes);
  section-role changes at once. No session is ended.
- Admin app: account rows offer "Edit roles" (from `allowedActions`), opening a form prefilled with
  the current roles, laid out like "New account"; roles the user may not change are shown
  read-only.
- Contract, primer, `.http` files, docs and `ai/open-proposals.md` updated.

## Non-goals

- Changing names or the username of an account; deleting accounts.
- Concurrent-edit detection (ETag/version): the last save wins.
- Ending sessions or revoking tokens when a role is removed.
- Changing the section members endpoints or screen; they stay as they are.
- Extending `GET /api/me` (separate M2 item).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `accounts`: `EDIT_ROLES` in `allowedActions` of the account list; new role-editing endpoint with
  its delegation rules and validation.
- `admin-accounts`: "Edit roles" row action and form.

## Impact

- **Backend:** `account` package (policy, validator, resource, service for group leave/join with
  compensation), `SectionRoleStore` (replace an account's section roles in one transaction).
  Tests: JUnit 5 + AssertJ unit and `@QuarkusTest`.
- **Admin:** API client/DTO (`EDIT_ROLES`, update call), account list action, new edit-roles
  screen and model, strings (de/en). Kotlin tests.
- **Contract/Docs:** `ai/primer/endpoints.md`, `http/accounts.http`, `docs/roles-and-workflow.md`
  where it describes account administration, `ai/open-proposals.md`.
- **Reader, Deploy:** not affected.
