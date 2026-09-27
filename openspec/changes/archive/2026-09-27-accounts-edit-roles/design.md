## Context

Newspaper roles are Keycloak group memberships (`publisher`, `editor-in-chief`, `reader`), read
by the backend from the token's `groups` claim (`NewspaperRoleAugmentor`). Section roles live in
the `section_role` table (`SectionRoleStore`, Hibernate Reactive, `@WithTransaction`). Account
creation (`AccountCreation`) already combines both stores all-or-nothing by compensation: Keycloak
first, database second, deleting the Keycloak user when the database step fails.

Delegation is already factored out: `RoleDelegation.assignableBy` (newspaper roles),
`SectionDelegation.mayChange` / `mayRemove` (section roles), `AccountPolicy.permitted` (actions on
accounts, "ranks above"). The section members endpoints stay untouched.

## Goals / Non-Goals

**Goals:**
- One endpoint that replaces both kinds of roles with the same checks as creation, applied to the
  difference only.
- Reuse the existing delegation classes and the create form's layout; no new rule vocabulary.

**Non-Goals:**
- A cross-store transaction; compensation is enough for a single-operator family newspaper.
- Optimistic locking (see proposal, Non-goals).

## Decisions

### D1 — Contract

```
PUT /api/accounts/{id}/roles
Content-Type: application/json

{ "roles": ["EDITOR_IN_CHIEF", "READER"],
  "sectionRoles": [ { "sectionId": 2, "role": "REPORTER" } ] }
```

Response `200` — the `AccountDto` as listed, `allowedActions` computed for the requester (may be
`[]` afterwards, e.g. after promoting someone to `EDITOR_IN_CHIEF` as an editor-in-chief):

```json
{ "id": "5f0c…", "username": "reader", "firstName": "Reader", "lastName": "",
  "roles": ["EDITOR_IN_CHIEF", "READER"],
  "sectionRoles": [ { "sectionId": 2, "role": "REPORTER" } ],
  "enabled": true, "allowedActions": ["RESET_PASSWORD", "LOCK"] }
```

Errors, all with the known error body `{"errors": [{"field": …, "message": …}]}` except the
empty-body `403` of the access rule:

| Status | When | Field |
|---|---|---|
| `400` | validation (every violation) | `roles`, `sectionRoles`, unknown field names |
| `403` | no account access | — (empty body) |
| `403` | `EDIT_ROLES` not permitted | `null`, `you may not edit the roles of account 'chief'` |
| `403` | an added/removed newspaper role not assignable | `roles` |
| `403` | a changed section role outside the scope | `sectionRoles` |
| `404` | unknown id or service account | `null` |
| `503` | Keycloak unavailable, or a failure after compensation | `null` |

`PUT` on a `/roles` sub-resource rather than `PATCH /api/accounts/{id}`: the body is the complete
role state (idempotent replacement), and names stay out of scope. Alternative considered: two
endpoints (newspaper roles / section roles) — rejected, the admin form saves both at once and
partial saves would need their own rollback story in the client.

`sectionRoles` is required (not defaulting to `[]`) so a client that forgets it cannot wipe every
section role by accident.

### D2 — `EDIT_ROLES` reuses the "ranks above" predicate

`AccountAction` becomes `EDIT_ROLES, RESET_PASSWORD, LOCK, UNLOCK` (declaration order =
`allowedActions` order). In `AccountPolicy.permitted`, `EDIT_ROLES` uses the same predicate as
`RESET_PASSWORD`. Alternative: "may change at least one role of the account" — rejected: an
editor-in-chief could then demote another editor-in-chief; editing roles is as sensitive as
resetting a password, so it follows the same "ranks above" rule.

### D3 — Only the difference is checked and written

`AccountRoleEdit` (new, next to `AccountCreation`) loads the target (Keycloak + section roles),
checks `EDIT_ROLES`, then computes:

- `added = requested − current`, `removed = current − requested` for newspaper roles; every one
  must be in `RoleDelegation.assignableBy(requester)`, else `403 roles`;
- per section: `current`/`requested` role (or none); for every differing section
  `SectionDelegation.mayChange(requester, section, current, requested)` or `mayRemove`, else
  `403 sectionRoles`. Unknown sections (not in `SectionRoleStore.sectionIds()`) → `400
  sectionRoles`, checked before permissions like in creation.

Unchanged roles are never checked, so a section editor can edit a reporter who also holds
`READER` without being able to assign `READER`. Empty diff → return the current account, no
write, no log line.

Validation: `AccountRequestValidator` gains `validateRoles(JsonNode)` returning a new
`EditRolesRequest(roles, sectionRoles)`, sharing the existing `roles`/`sectionRoles` readers and the
"at least one role" rule; allowed fields `roles`, `sectionRoles`; both required.

### D4 — Write order and compensation

1. Keycloak: `AccountService.changeGroups(id, added, removed)` — join added, leave removed. On a
   failure midway it reverts the steps already done (leave what it joined, join what it left),
   logs an error if the revert fails too, and rethrows (`503`).
2. Database: `SectionRoleStore.replace(accountId, diff, assignedBy)` in one `@WithTransaction`:
   delete removed sections, update changed rows (`assignedBy`/`assignedAt` refreshed), insert new
   ones. Unchanged rows keep their `assignedBy`/`assignedAt`.
3. When step 2 fails, `changeGroups(id, removed, added)` reverts step 1 (error log if that fails,
   naming the account so the operator can fix it in the Keycloak console), then the failure is
   answered with `503`.

Keycloak first, because its multi-call change is the part without a transaction; reverting the
database instead would need to restore `assignedBy`/`assignedAt` of deleted rows. Same order as
creation.

### D5 — Effect on the person's session

Nothing is ended. Section roles are read from the database per request and apply at once.
Newspaper roles come from the token; Keycloak recomputes `groups` on the next refresh (access token
lifespan, a few minutes), so a removed role lingers at most that long — the same window the lock
already accepts. Log line (INFO, only when something changed):
`Roles of account '<username>' changed by '<user>': roles [READER] -> [EDITOR_IN_CHIEF, READER], section roles [...] -> [...]`.

### D6 — Admin app

- `AccountAction` (admin enum) gains `EDIT_ROLES`; `AccountDto.actions()` keeps ignoring unknown
  values.
- New `Route.EditRoles(account, assignableRoles, sections)`, opened from the row action; the list
  already holds `assignableRoles` and loads `GET /api/sections` for "New account".
- `EditRolesModel` (pure, unit-tested like `NewAccountModel`): state = requested roles, preset from
  the account; per newspaper role `editable = role in assignableRoles`; per section
  `editable = assignableRoles non-empty and (current role is null or in assignableRoles)`, choices =
  none + `assignableRoles`; sections not editable and without a role of the account are omitted.
  `canSave = changed && (roles ∪ sectionRoles) non-empty && !saving`. `submit` sends the full state.
- `EditRolesScreen` reuses `SectionRoleRow` and `FormField`-style error display from
  `AccountScreens.kt`; read-only roles render as disabled checkboxes / a plain label.
- Error mapping reuses `NewAccountModel`'s `refusal()` logic (moved to a shared helper) for
  `roles`/`sectionRoles` field errors.

## Risks / Trade-offs

- [Compensation fails, e.g. Keycloak dies between step 1 and its revert] → roles are
  inconsistent; error log names the account and both role sets; the publisher can re-save the form
  (the endpoint is idempotent on the full state).
- [Demoted person keeps newspaper privileges until token refresh] → accepted, documented in the
  primer (same as lock).
- [Two admins edit the same account] → last write wins; the diff is computed from a fresh read, so
  only roles the second editor actually changed are touched in Keycloak, but its database write
  replaces section roles with its full state.
- [Changed `allowedActions` order] → clients rendering from the list are unaffected; tests with
  exact arrays are updated in this change.

## Migration Plan

No database migration (existing `section_role` table). Deploy backend and admin together as usual;
an old admin app ignores `EDIT_ROLES`.
