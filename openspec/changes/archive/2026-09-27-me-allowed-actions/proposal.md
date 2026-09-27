## Why

The admin app decides which header entries it shows by re-deriving the server's access rules from
`roles` and `sectionRoles` of `GET /api/me` (`canAdministerAccounts` in `AccountScreens.kt`). Every
rule change on the server then needs a matching change in the client, and M3 (approval chain) will
add more such rules. Articles and accounts already carry server-computed `allowedActions`; the
logged-in user should get the same for the newspaper-wide actions, so clients render from the
server's decision instead of duplicating it.

## What Changes

- `GET /api/me` gains `allowedActions` (additive): the newspaper-wide actions the user may perform
  now, in a fixed order — `WRITE_ARTICLES`, `MANAGE_SECTIONS`, `ASSIGN_SECTION_ROLES`,
  `ADMINISTER_ACCOUNTS`. The values are computed from the same `Newsroom` predicates that guard the
  endpoints. Clients ignore values they do not know.
- No separate "scopes" field: the scope is already expressed by `roles` and `sectionRoles`, and per
  section by `canWrite`/`assignableRoles` of `GET /api/sections`.
- The admin app shows its header entries from `allowedActions` instead of deriving them from roles:
  "Articles" with `WRITE_ARTICLES`, "Sections" with `MANAGE_SECTIONS` or `ASSIGN_SECTION_ROLES`,
  "Accounts" with `ADMINISTER_ACCOUNTS`.
- A user without `WRITE_ARTICLES` (e.g. only `READER`) sees a notice that their account has no
  writing role instead of an article list that fails with `403`.
- `ai/primer/endpoints.md`, `docs/architecture.md` and `http/me.http` follow the new response.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `api-authentication`: the current-user endpoint returns `allowedActions`.
- `admin-shell`: header entries follow `allowedActions`; users without `WRITE_ARTICLES` get a
  notice instead of the article list.
- `admin-accounts`: the "Accounts" entry follows `ADMINISTER_ACCOUNTS` instead of role checks.
- `admin-sections`: the "Sections" entry follows `MANAGE_SECTIONS`/`ASSIGN_SECTION_ROLES` instead
  of role checks.

## Non-goals

- No change to who may do what: the actions mirror today's endpoint rules exactly.
- No per-section or per-article information in `/api/me` (that stays on `GET /api/sections` and
  the article responses).
- No approval-chain actions (review, approve, lock); M3 adds them to this list.
- No live refresh of `allowedActions` after a role change; the app reads `/api/me` at login, as
  today.

## Impact

- **Backend:** `MeDto`, `MeResource` (now builds a `Newsroom` via `NewsroomService`), new enum of
  newspaper-wide actions; `@QuarkusTest` coverage.
- **Admin:** `MeDto` in `Dtos.kt`, header/navigation in `App.kt`, `canAdministerAccounts` removed,
  notice for users without writing role, new localized string (de/en); Kotlin tests.
- **Reader:** none.
- **Deploy:** none.
- **Docs/contract:** `ai/primer/endpoints.md` (`GET /api/me`), `docs/architecture.md` (`/api/me`
  line), `http/me.http`.
- **Compatibility:** additive; older clients ignore the new field.
