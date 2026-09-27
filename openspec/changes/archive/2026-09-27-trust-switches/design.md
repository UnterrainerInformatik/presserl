## Context

`ApprovalChain.next` walks the levels above a starting level and keeps the first one that is
staffed (`Staffing.staffed`) or, for `PUBLISHER`, forced by the emergency-brake lock.
`StaffingService` loads `Staffing` per request: only when an author asks about their own articles
(`forArticles`) or after an approval (`forApproval`). Section roles and their store
(`SectionRoleStore`, Hibernate Reactive Panache) are the model for a small per-account table.
Account responses are built in `AccountResource` from Keycloak users plus section roles, with
`allowedActions` computed by `AccountPolicy`. The last migration is `V7__article_lock.sql`; the
database is PostgreSQL 18.

## Goals / Non-Goals

**Goals:**
- Trust is one more skip predicate in `ApprovalChain.next`, fed through the data the chain already
  receives, so every chain question (allowed actions, submit, approve) sees it without new call
  paths.
- One source of truth for "who may set/clear which entry" (`TrustPolicy`), used both for
  `trustScopes` and for authorising the `PUT`.

**Non-Goals:**
- Caching trust across requests; a family newspaper has a handful of rows.
- A separate trust resource or listing endpoint; trust is read through accounts only.

## Decisions

### Storage: one table, section nullable

```sql
-- V8__trust.sql
CREATE TABLE trust (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id TEXT        NOT NULL,
    level      TEXT        NOT NULL CHECK (level IN ('SECTION_EDITOR', 'EDITOR_IN_CHIEF', 'PUBLISHER')),
    section_id BIGINT      REFERENCES section (id) ON DELETE CASCADE,
    set_by     TEXT        NOT NULL,
    set_at     TIMESTAMPTZ NOT NULL,
    CHECK ((level = 'SECTION_EDITOR') = (section_id IS NOT NULL)),
    UNIQUE NULLS NOT DISTINCT (account_id, level, section_id)
);
```

`account_id` and `set_by` are Keycloak ids like `section_role.account_id`. `ON DELETE CASCADE`
implements "deleting a section deletes its trust entries" without touching the section delete
code. `NULLS NOT DISTINCT` (PostgreSQL ≥ 15) keeps one row per newspaper-wide level.
*Alternative:* a surrogate `section_id = 0` for newspaper-wide levels — rejected, it breaks the
foreign key. *Alternative:* two tables — rejected, twice the code for one concept.

New package `info.unterrainer.presserl.trust`: `TrustEntity`, `TrustStore`, value type
`TrustScope(ApprovalLevel level, Long sectionId)` (JSON shape `{level, sectionId}`, `sectionId`
`null` for newspaper-wide levels), `TrustPolicy`.

`TrustStore`:
- `scopesOf(accountId) → Uni<Set<TrustScope>>`
- `byAccount() → Uni<Map<String, List<TrustScope>>>` (ordered: `PUBLISHER`, `EDITOR_IN_CHIEF`, then
  section entries by section position — joined with `section` as `SectionRoleStore` does)
- `set(accountId, scope, setBy) → Uni<Boolean>` (insert unless present; `true` when inserted)
- `clear(accountId, scope) → Uni<Boolean>` (`true` when a row was deleted)

### Chain: trust travels inside `Staffing`

`Staffing` gains a fourth component `Map<String, Set<TrustScope>> trusts` (by account id, only
the accounts the request asks about) and a method `trusts(level, sectionId, authorSub)`.
`ApprovalChain.next`'s filter becomes:

```java
.filter(level -> locked && level == ApprovalLevel.PUBLISHER
        || staffing.staffed(level, sectionId, authorSub) && !staffing.trusts(level, sectionId, authorSub))
```

`StaffingService.forArticles` loads the requester's own trust (it is only loaded when the requester
is an author); `forApproval()` becomes `forApproval(String authorSub)` and loads the author's. Both
add one indexed query. `NOT_NEEDED` keeps throwing when asked.
*Alternative:* a separate `Trusts` parameter on `next` and all `ArticlePolicy` methods — rejected,
it touches every call site that already passes `Staffing` for the same purpose ("facts about the
newsroom the chain needs"). The record's Javadoc is widened accordingly; the name stays to keep
the diff small.

Because the lock branch is checked first, the lock overrides trust as the spec requires. Pending
submissions are untouched: `pendingLevel` is only written in submit/approve, which recompute the
chain anyway.

### Policy: `TrustPolicy`

Pure functions over `Newsroom` (requester) and `AccountDto` (target, with roles and section roles)
plus the target's current `trusts`:

- `trustLevels(Newsroom)` → the requester's scopes they act for: `{PUBLISHER}` /
  `{EDITOR_IN_CHIEF}` / `{SECTION_EDITOR × each own section}` / `{}`.
- `mayGrant(requester, target, scope)` → scope ∈ trust levels, target ≠ requester, target below:
  `PUBLISHER`: no `PUBLISHER`, has `EDITOR_IN_CHIEF` or any section role; `EDITOR_IN_CHIEF`:
  neither, has any section role; `SECTION_EDITOR`/S: neither, `REPORTER` in S.
- `scopes(requester, target, trusts)` → ordered union of grantable scopes and existing entries in
  the requester's trust levels, excluding the own account. Used for `trustScopes` and, with
  `contains`, to authorise the `PUT` (setting a scope that is only clearable is then a harmless
  no-op because the entry exists).

### REST contract

`AccountDto` gains two fields (also in `POST /api/accounts`, `PUT …/roles`, lock/unlock and
password-reset responses):

```json
{ "id": "5f0c…", "username": "reader", "firstName": "Reader", "lastName": "",
  "roles": ["READER"], "sectionRoles": [ { "sectionId": 1, "role": "REPORTER" } ],
  "enabled": true,
  "trusts": [ { "level": "SECTION_EDITOR", "sectionId": 1 } ],
  "trustScopes": [ { "level": "EDITOR_IN_CHIEF", "sectionId": null } ],
  "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "LOCK"] }
```

New endpoint:

```
PUT /api/accounts/{id}/trust
Content-Type: application/json

{ "level": "SECTION_EDITOR", "sectionId": 1, "trusted": true }
```

- `200` → the `AccountDto` above, computed for the requester.
- `400` → `{"errors": [{"field": "sectionId", "message": "is required for SECTION_EDITOR"}]}`
  (fields `level`, `sectionId`, `trusted`; unknown fields at their name; unknown section at
  `sectionId`).
- `403` empty body → no account access; `403` error body →
  `{"errors": [{"field": null, "message": "you may not change trust of account 'reader' at SECTION_EDITOR"}]}`.
- `404` unknown id or service account; `503` Keycloak unavailable.

Validation lives in `AccountRequestValidator.validateTrust(json)` next to the other body
validators. The endpoint loads the target like `target(…)` does (Keycloak user + section roles)
plus its trust entries, checks `TrustPolicy.scopes(...).contains(scope)`, then calls
`TrustStore.set/clear`. Logging: `Trust of account 'reader' at SECTION_EDITOR in section 1 set by
'nogroups'` / `… cleared by …`, only when something changed.

`GET /api/accounts` loads `TrustStore.byAccount()` alongside `SectionRoleStore.byAccount()` (one
more query). `AccountDto.withAllowedActionsFor(requester)` also fills `trustScopes`, so every
response path computes both from the same place; a `withTrusts(...)` helper sets `trusts`.

### Admin app

- `Dtos.kt`: `TrustScopeDto(level: String, sectionId: Long?)`; `AccountDto.trusts`,
  `AccountDto.trustScopes` with default `emptyList()` (tolerant of older backends); unknown
  `level` values filtered out when rendering.
- `ApiClient.setTrust(id, level, sectionId, trusted): AccountDto`.
- `AccountListModel`: `requestTrust(account, scope, on)`: `on` → pending confirmation (reuses the
  `PendingAction` mechanism with a new variant), `off` → sends at once; success replaces the
  account, failure sets the error message.
- `AccountScreens`: trust markers under the role labels; switches in the row's action area, one
  per `trustScopes` entry, labelled with the role label of the level (plus "· section name").
- Strings (de/en): "Vertraut von" / "Trusted by", confirmation "Artikel von {name} warten dann
  nicht mehr auf {level}." / "Articles by {name} will no longer wait for {level}.".

## Risks / Trade-offs

- [An editor-in-chief who is also the only section editor of a section cannot trust for the
  section-editor level (own-highest-level rule), so reporters there always wait for that level] →
  They approve that level with their editor-in-chief approval anyway (it settles every level up to
  theirs), and their own editor-in-chief trust skips the next one. Documented in
  `roles-and-workflow.md`; revisit if it annoys in practice.
- [Stale entries after role changes silently become active again when the person regains the role]
  → Intended ("trust is per person"); entries are visible in the list and clearable.
- [`Staffing` now carries more than staffing] → Javadoc updated; renaming is left for a later
  refactoring to keep this change focused.
- [Concurrent set/clear by two holders] → Last write wins; the unique constraint prevents duplicate
  rows (an insert racing another insert catches the constraint violation and reports "unchanged").

## Migration Plan

`V8__trust.sql` only adds a table; no data migration. Existing chains are unchanged because no
entries exist. Rollback: revert the code; the table is ignored (drop it with a correction
migration if needed — never edit `V8` once applied).
