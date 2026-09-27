## Context

`GET /api/me` (`MeResource`) returns `username`, `displayName`, `roles` from the token and
`sectionRoles` from `SectionRoleStore.namedRolesOf`. The server's access rules live in `Newsroom`
(`isAdministrator`, `isWriter`, `mayAdministerAccounts`, `isSectionEditorAnywhere`, …), built per
request by `NewsroomService.of(CurrentUser)`. The admin app re-implements one of them as
`canAdministerAccounts(me)` in `AccountScreens.kt` and uses it to show all three header entries at
once; a user holding only `READER` still lands on the article list, whose request fails with `403`.

Articles (`ArticleAction`) and accounts (`AccountAction`) already carry `allowedActions`; this
change applies the same pattern to the user.

## Goals / Non-Goals

**Goals:**
- One server-side source for the newspaper-wide actions, reusing `Newsroom`.
- Admin navigation decided only from `allowedActions`.
- A clear screen for a logged-in user without a writing role.

**Non-Goals:**
- Changing any access rule, adding approval-chain actions, per-section data in `/api/me`, or
  refreshing `/api/me` after login.

## Decisions

### D1 — Response shape

```http
GET /api/me
Authorization: Bearer <token>
```
```json
{ "username": "nogroups", "displayName": "No Groups", "roles": [],
  "sectionRoles": [ { "sectionId": 1, "sectionName": "Sport", "role": "SECTION_EDITOR" } ],
  "allowedActions": ["WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"] }
```
`allowedActions` is always present (`[]` for none), in enum order. Errors unchanged (`401`).

Alternative considered: boolean flags (`canWrite`, `canManageSections`, …) like `SectionListDto`.
Rejected: a list of action names matches `ArticleDto`/`AccountDto`, and M3 can add actions without
new fields; clients ignore unknown values.

### D2 — Action enum and mapping

New enum `info.unterrainer.presserl.auth.NewspaperAction` (`WRITE_ARTICLES`, `MANAGE_SECTIONS`,
`ASSIGN_SECTION_ROLES`, `ADMINISTER_ACCOUNTS`). The mapping lives next to the rules, as
`Newsroom.allowedActions()` returning `List<NewspaperAction>`:

| Action | Newsroom predicate |
|---|---|
| `WRITE_ARTICLES` | `isWriter()` |
| `MANAGE_SECTIONS` | `isAdministrator()` (what `SectionService` checks for create/update/order and reports as `canManage`) |
| `ASSIGN_SECTION_ROLES` | `isAdministrator() \|\| isSectionEditorAnywhere()` |
| `ADMINISTER_ACCOUNTS` | `mayAdministerAccounts()` |

`MANAGE_SECTIONS` and `ASSIGN_SECTION_ROLES` must use the same predicates the section endpoints
use; implementation checks `SectionService`/`SectionDelegation` and reuses their method rather than
re-stating the condition. `ASSIGN_SECTION_ROLES` and `ADMINISTER_ACCOUNTS` coincide today but mean
different endpoints and may diverge (e.g. M3), so both are kept.

Why the enum sits in `auth` while `Newsroom` sits in `section`: `MeDto` lives in `auth`, and
`Newsroom` already depends on `auth` (`CurrentUser`, `NewspaperRole`), so no cycle is added.

### D3 — MeResource builds a Newsroom

`MeResource` combines `NewsroomService.of(user)` (for the actions) with
`SectionRoleStore.namedRolesOf(sub)` (for the named section roles) via `Uni.combine().all()`.
Two small queries per call; `/api/me` is called once per login, so no shared query is warranted.

### D4 — Admin navigation

`MeDto` (Kotlin) gains `allowedActions: List<String> = emptyList()` (default keeps older servers
and existing tests decoding). A small pure function in the admin code maps `allowedActions` to the
visible `NavEntry` set (Articles/Sections/Accounts, per the admin-shell spec; none when fewer than
two), so it is unit-testable without Compose. `canAdministerAccounts(me)` is removed;
`Screen.LoggedIn` exposes the entries instead.

Without `WRITE_ARTICLES`, `LoggedIn` shows a notice (new string `no_writing_role`, de: "Dieses
Konto hat keine Rolle zum Schreiben von Artikeln.", en: "This account has no role for writing
articles.") instead of the route stack; the header with logout stays.

## Risks / Trade-offs

- [Drift between `allowedActions` and endpoint checks] → the mapping reuses the exact `Newsroom`
  predicates; `@QuarkusTest` asserts actions per role combination alongside the existing access
  tests.
- [Stale actions after a role change during a session] → accepted, as today for `roles`; section
  roles are read per call, so a reload of the app shows the change.
- [Admin talks to an older backend without the field] → default `emptyList()` would hide
  everything; both ship in the same image, so not a real deployment case.
