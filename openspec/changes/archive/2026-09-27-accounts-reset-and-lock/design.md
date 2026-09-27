## Context

- Accounts are Keycloak users; newspaper roles are Keycloak groups, section roles live in the
  `section_role` table (`SectionRoleStore`). `AccountService` wraps the blocking Keycloak Admin
  client and is called through `KeycloakCalls` from reactive endpoints; `Newsroom` is the
  per-request view of the requesting user (token roles + section roles).
- `AccountDto` already carries `enabled`; the admin list shows a "disabled" marker for it.
- `PassPhraseGenerator` creates the four-word pass-phrase; creation returns it once in
  `CreatedAccountDto { account, password }` and the admin app shows `AccountSlipScreen`.
- Articles already expose `allowedActions` from a pure policy (`ArticlePolicy`); clients never
  re-implement rules. Accounts follow the same pattern.
- The backend's service account holds `realm-management/manage-users`
  (`deploy/keycloak/presserl-realm.json`), which covers credential reset, user update and user
  session logout. Dev realm users: `publisher`, `chief`, `reader`, `nogroups`.
- The Keycloak user id equals the token's `sub`, so "own account" is `id == sub`.

## Goals / Non-Goals

**Goals:**
- One pure, unit-tested function deciding reset/lock/unlock for (requesting newsroom, target
  account), used for both `allowedActions` and endpoint enforcement.
- Reuse the creation response shape and the slip screen for resets.

**Non-Goals:**
- Revoking issued access tokens (would need token introspection on every request).
- Admin-app self-service password change.

## Decisions

### D1 — `AccountAction` and `AccountPolicy`

```java
public enum AccountAction { RESET_PASSWORD, LOCK, UNLOCK }

public static List<AccountAction> allowedActions(Newsroom requester, AccountDto target)
```

`AccountPolicy` is a pure static class (like `ArticlePolicy`), input is the target's newspaper
roles, section roles and `enabled`:

- self (`target.id == requester.user().sub()`) → nothing.
- target holds `PUBLISHER` → nothing.
- `RESET_PASSWORD`: requester is publisher; or requester is editor-in-chief and target does not
  hold `EDITOR_IN_CHIEF`; or target's newspaper roles ⊆ {`READER`}, target's section roles are
  non-empty and each is `REPORTER` in a section where `requester.isSectionEditorOf(id)`.
- `LOCK`: requester is publisher and target enabled. `UNLOCK`: requester is publisher and target
  disabled.

Alternative considered: extend `RoleDelegation` — rejected, it answers "which roles may I
assign", a different question; the rank rule lives in one place in `AccountPolicy`.

### D2 — Endpoints and shapes

```
POST /api/accounts/{id}/password-reset   (no body)
200 { "account": { "id": "…", "username": "reader", "firstName": "Reader", "lastName": "",
                   "roles": ["READER"], "sectionRoles": [], "enabled": true,
                   "allowedActions": ["RESET_PASSWORD", "LOCK"] },
      "password": "tiger-wolke-apfel-leiter" }

POST /api/accounts/{id}/lock             (no body)
POST /api/accounts/{id}/unlock           (no body)
200 { "id": "…", "username": "reader", …, "enabled": false,
      "allowedActions": ["RESET_PASSWORD", "UNLOCK"] }

GET /api/accounts → each account additionally has "allowedActions": [...]
```

Errors: `401`; `403` with empty body when the caller may not administer accounts at all (existing
`newsroom()` check); `403` with an error body (`AccountException.forbidden`, field `null`,
message naming the action) when the policy refuses; `404` (`AccountException.notFound`) for
unknown ids and service accounts; `503` Keycloak unavailable. `POST /api/accounts` responses gain
`allowedActions` too (computed for the creator), keeping one `AccountDto` shape.

The response is `200` (not `204`) so the admin app updates one row without reloading the list.

### D3 — Flow per action

1. `newsroom()` (access rule) → `keycloakCalls.call(() -> service.find(id))` including newspaper
   roles (new: `find` reads the user's groups) → `404` when empty.
2. `sectionRoles.of(accountId)` → full target `AccountDto`.
3. `AccountPolicy` check → `403` when refused.
4. Keycloak call on a worker thread:
   - reset: `users().get(id).resetPassword(credential(passphrase, temporary=false))`, then
     `users().get(id).logout()`;
   - lock: when enabled, `update(enabled=false)` then `logout()`; unlock: when disabled,
     `update(enabled=true)`. Already in the target state → no Keycloak write (idempotent).
5. Log at INFO `Password of account '%s' reset by '%s'` / `Account '%s' locked|unlocked by '%s'`
   — never the password.
6. Respond with the updated account and freshly computed `allowedActions`.

Logout after the password reset: if it fails, the password is already changed; the endpoint then
answers `503` and logs the error. The admin can retry (a new pass-phrase is generated) — accepted
for a rare failure instead of a compensation step.

### D4 — Admin app

- `AccountDto.allowedActions: List<String> = emptyList()` (default keeps old fixtures working);
  `ApiClient.resetPassword(id): CreatedAccountDto`, `lock(id)`, `unlock(id): AccountDto`.
- `AccountRow` shows text buttons for the listed actions; a shared `ConfirmDialog` (Material3
  `AlertDialog`) names the account. State lives in a small `AccountListModel` (testable without
  UI): pending confirmation, running request, error message, row replacement on success.
- Reset success navigates to `AccountSlipScreen` with the returned `CreatedAccountDto`; "Done"
  goes back to the list (reloaded). The slip screen needs no change beyond its heading, which may
  stay the same.
- The existing `account_disabled` label becomes "locked"/"gesperrt".

### D5 — Docs and contract

`ai/primer/endpoints.md` gets three sections and `allowedActions` on the account shape;
`http/accounts.http` exercises reset, lock, unlock and the refusals; `docs/architecture.md` lists
`POST /api/accounts/{id}/unlock` and fixes the reset rule text ("anyone above the person, never
publishers"); `ai/open-proposals.md` drops reset/lock from M2 and the stale
"articles in sections are proposed" sentence.

## Risks / Trade-offs

- [Access tokens outlive a lock by their lifetime] → documented; Keycloak's default access-token
  lifespan is short, refresh stops at once through session logout.
- [Target changes between policy check and Keycloak write] (e.g. becomes publisher concurrently)
  → accepted; family scale, both actions are reversible.
- [Two publishers cannot help each other] → by decision; a locked-out publisher needs the
  Keycloak admin console. Documented in `docs/roles-and-workflow.md` if not already clear.
- [`GET /api/accounts` computes the policy per account] → pure in-memory checks, negligible for
  ≤ 1000 accounts.
