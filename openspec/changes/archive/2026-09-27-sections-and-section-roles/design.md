## Context

- Newspaper-wide roles are Keycloak groups and reach the backend through the token
  (`NewspaperRoleAugmentor` → `@RolesAllowed`). Section roles live in the database
  (`docs/roles-and-workflow.md`, `docs/architecture.md` data model), so they are **not** in the
  token and `@RolesAllowed` cannot see them.
- `AccountResource` is `@Blocking` because the Keycloak Admin client blocks. Hibernate Reactive
  (Panache) must run on a Vert.x event-loop context; it refuses worker threads. Account endpoints
  now need both: Keycloak and the `section_role` table.
- `RoleDelegation.assignableBy(CurrentUser)` decides newspaper roles; `UsernameDeriver` folds
  names to `a-z0-9-`.
- The dev realm has the users `publisher`, `chief`, `reader`, `nogroups`; tests give `nogroups`
  and `reader` section roles through the API.

## Goals / Non-Goals

**Goals:**
- One per-request view of "who am I in this newspaper" (newspaper roles + section roles) that
  account, section and `/api/me` endpoints share, and that M3 can reuse for the approval chain.
- Section-role delegation as a pure, unit-tested function next to `RoleDelegation`.

**Non-Goals:**
- Putting section roles into the token (Keycloak mapper or custom claim).
- Optimistic locking for sections (a family edits sections rarely; last write wins).

## Decisions

### D1 — Data model (`V3__sections.sql`)

```sql
CREATE TABLE section (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT        NOT NULL,
    slug       TEXT        NOT NULL UNIQUE,
    color      TEXT        NOT NULL CHECK (color IN ('red','orange','yellow','green','teal','blue','purple','pink')),
    position   INT         NOT NULL,
    settings   JSONB       NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX section_name_lower_idx ON section (lower(name));

CREATE TABLE section_role (
    section_id  BIGINT      NOT NULL REFERENCES section (id) ON DELETE CASCADE,
    account_id  TEXT        NOT NULL,   -- Keycloak user id = token sub
    role        TEXT        NOT NULL CHECK (role IN ('SECTION_EDITOR', 'REPORTER')),
    assigned_by TEXT        NOT NULL,   -- sub of the acting user
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (section_id, account_id)
);
CREATE INDEX section_role_account_idx ON section_role (account_id);
```

- The primary key enforces "one section role per account and section"; roles are cumulative, so
  `SECTION_EDITOR` + `REPORTER` in the same section would be redundant.
- `settings` is created now (architecture: overrides per section) but not exposed yet.
- `position` has no unique constraint: reordering rewrites all positions in one transaction and
  a unique constraint would need to be deferrable. Ties (concurrent creates) sort by `id`.
- Colours are palette **keys**, not hex values, so the reader theme (M4) maps them to
  `--presserl-section-<key>` tokens and forks can restyle them.
- Alternative considered: a `role` table for all roles incl. newspaper-wide ones — rejected, the
  split Keycloak/DB is settled in the docs.

### D2 — `Newsroom`: the caller's roles per request

`NewsroomService.of(CurrentUser) → Uni<Newsroom>` loads the caller's `section_role` rows.
`Newsroom` (record: `CurrentUser user`, `Map<Long, SectionRole> sectionRoles`) answers
`isAdministrator()` (publisher or editor-in-chief), `isSectionEditorAnywhere()`,
`roleIn(sectionId)`. Access checks that need section roles are done in the resources/services via
`Newsroom`, not with `@RolesAllowed`:

- `/api/accounts/*`: `@Authenticated`; `403` unless `isAdministrator() || isSectionEditorAnywhere()`.
- `/api/sections`: `@Authenticated`; create/update/order require `isAdministrator()`;
  members endpoints require a non-empty `SectionDelegation.assignable(newsroom, sectionId)`.

Alternative considered: a `SecurityIdentityAugmentor` that adds `SECTION_EDITOR` as a Quarkus
role — rejected: it would run a DB query on every request (reader included) and a global
"SECTION_EDITOR" role loses the section scope anyway.

### D3 — Section-role delegation

`SectionDelegation.assignable(Newsroom, sectionId) → List<SectionRole>` (pure):

| Caller | in section S |
|---|---|
| `PUBLISHER` or `EDITOR_IN_CHIEF` | `SECTION_EDITOR`, `REPORTER` |
| `SECTION_EDITOR` in S | `SECTION_EDITOR`, `REPORTER` |
| anyone else (incl. `REPORTER` in S) | — |

A change from role *a* to role *b* needs both in `assignable`; removal needs the current role.
Reporters do not delegate: the roles table in `docs/roles-and-workflow.md` gives reporters no
administration, and the delegation example names section editors only; the doc sentence
"Anyone may create accounts…" is tightened to "Everyone from section editor up…" in this change.

`RoleDelegation.assignableBy` (newspaper roles) stays as is; a section editor without newspaper
role gets `[]`.

### D4 — Reactive account endpoints with Keycloak offloaded

`AccountResource` drops `@Blocking` and returns `Uni`. Every Keycloak call goes through
`io.vertx.mutiny.core.Vertx#executeBlocking(Uni)` (wrapped in a small `KeycloakCalls` helper),
which runs the blocking code on a worker and resumes on the caller's Vert.x context, so Panache
calls can follow in the same chain. `AccountService` keeps its blocking methods (unit tests stay
unchanged); the resource/an orchestration layer composes them with the DB work.

Alternative considered: stay `@Blocking` and call the Panache `Uni` with `.await()` —
rejected: Hibernate Reactive refuses non-event-loop threads. Alternative: a JDBC datasource for
section roles — rejected: a second persistence style for one table (Flyway's JDBC datasource is
for migrations only).

Account creation order (all or nothing):
1. validate body; load `Newsroom`; check newspaper roles (`403 roles`) and every section role
   (`400 sectionRoles` for unknown sections, `403 sectionRoles` for scope);
2. Keycloak: username check, create user, join groups (existing compensation);
3. DB transaction: insert `section_role` rows;
4. on failure in 3: delete the Keycloak user (same `deleteOrphan` path), rethrow.

### D5 — REST contract

`GET /api/sections` → `200`
```json
{
  "canManage": true,
  "sections": [
    { "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0,
      "assignableRoles": ["SECTION_EDITOR", "REPORTER"] }
  ]
}
```

`POST /api/sections` `{"name": "Sport", "color": "green"}` (`color` optional) → `201`,
`Location: /api/sections/1`, body = one section (as in the list).

`PUT /api/sections/1` `{"name": "Sportnews", "color": "blue"}` → `200`, one section.

`PUT /api/sections/order` `{"ids": [2, 1]}` → `200`, same body as `GET /api/sections`.

`GET /api/sections/1/members` → `200`
```json
{
  "assignableRoles": ["SECTION_EDITOR", "REPORTER"],
  "members": [
    { "accountId": "5f0c…", "username": "nogroups", "firstName": "No", "lastName": "Groups",
      "role": "SECTION_EDITOR" }
  ]
}
```

`PUT /api/sections/1/members/5f0c…` `{"role": "REPORTER"}` → `200`, one member.
`DELETE /api/sections/1/members/5f0c…` → `204`.

`GET /api/accounts` — each account gains `"sectionRoles": [{"sectionId": 1, "role": "REPORTER"}]`.

`POST /api/accounts`
```json
{ "firstName": "Max", "lastName": "", "username": "max", "roles": [],
  "sectionRoles": [{ "sectionId": 1, "role": "REPORTER" }] }
```
→ `201` as before; `account.sectionRoles` filled.

`GET /api/me` → `{"username": …, "displayName": …, "roles": [...],
"sectionRoles": [{"sectionId": 1, "sectionName": "Sport", "role": "SECTION_EDITOR"}]}`.

Errors use the shared body `{"errors": [{"field": …, "message": …}]}`: `400`, `403` (with field
for delegation refusals, empty body for access refusals as today), `404` (empty body), `409`
(`name`), `503` (Keycloak). All changes are additive for existing clients: new fields only,
`roles: []` newly accepted when `sectionRoles` is non-empty.

### D6 — Slugs

`UsernameDeriver`'s folding is generalised to `Slugs.fold(text, maxLength, fallback)`; usernames
keep 32/`user`, sections use 40/`section`. The suffixing (`-2`, `-3`, … shortening the base) is
shared; the "shorter than 3" rule stays username-only. Slugs never change on rename, so later
reader URLs stay stable.

### D7 — Admin app

- `MeDto` gains `sectionRoles`; `Screen.LoggedIn` exposes `canAdministerAccounts`
  (= administrator or section editor anywhere), used for the "Accounts" and "Sections" entries.
- Header sections: Articles · Sections · Accounts; `Route.Sections`, `Route.SectionForm(section?)`,
  `Route.SectionMembers(id)`.
- Reordering by up/down buttons (large touch targets, per design guidelines) instead of drag and
  drop — no Compose drag support needed and works on touch.
- "New account" loads `GET /api/sections` alongside and renders one row per section with
  non-empty `assignableRoles`: segmented choice "none / Ressortleiter / Redakteur".
- German UI term for section: "Ressort" (header "Ressorts"), matching "Ressortleiter".
- Removal confirmation is an in-app dialog (no browser `confirm`).

## Risks / Trade-offs

- [`executeBlocking` + Panache context handling is new in this code base] → task 2.1 proves it
  first with a `@QuarkusTest` that runs Keycloak then Panache in one request before the rest is
  built on it.
- [Orphan `section_role` rows when a user is deleted directly in Keycloak] → ignored in lists
  (members join against existing users); cleanup is out of scope and harmless.
- [A section editor can remove other section editors of their section, incl. themselves] →
  follows "at or below own level"; publishers see everything and can fix it. Documented.
- [Account list for section editors shows all accounts] → acceptable for a family newspaper and
  needed to pick existing people as reporters; no passwords or secrets are listed.
- [Every account/section request adds one small DB query for the caller's section roles] →
  indexed by `account_id`; negligible at newspaper scale.

## Migration Plan

`V3__sections.sql` is additive; existing rows are untouched. Rollback = deploy the previous image;
the extra tables are ignored by it. No Keycloak realm change.
