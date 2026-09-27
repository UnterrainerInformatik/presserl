## Why

M2 promises sections and per-section roles, but so far a newspaper has neither: every account
holds newspaper-wide roles only, and nobody can be made section editor or reporter of *Sports*.
Sections and section roles are the base for the approval chain (M3) and for articles filed under
a section, so they come next.

## What Changes

- New **sections**: editors-in-chief and publishers create sections (name, colour from a fixed
  palette), rename them, change their colour and reorder them. Each section gets a stable slug
  for later reader URLs.
- New **section roles** `SECTION_EDITOR` and `REPORTER`, stored in the Presserl database per
  account and section (not in Keycloak).
- **Delegation extended to section roles**: publishers and editors-in-chief assign both section
  roles in every section; a section editor assigns `SECTION_EDITOR` and `REPORTER` in their own
  sections only. The same rule governs removing a section role. Reporters assign nothing.
- **Section editors administer accounts within their scope**: they may open the account list and
  create accounts that carry only section roles of their own sections.
- `POST /api/accounts` accepts optional `sectionRoles`; `roles` may then be empty, but an account
  needs at least one role of either kind.
- `GET /api/accounts` lists each account's section roles (additive field).
- `GET /api/me` returns the caller's section roles (additive field) so the admin app can show
  the right entries.
- Admin app: new "Sections" screen (list, create, rename, colour, reorder, members with role
  assignment/removal); "New account" offers section roles; header entries follow the new rules.
- Contract, primer and `.http` files updated; `docs/roles-and-workflow.md` states that
  reporters do not delegate.

## Non-goals

- Articles in sections (article gets a section, reporters and section editors may write) — the
  next change, `articles-in-sections`.
- Deleting sections; the default section from `presserl.section.default`.
- Reader section pages (`/sections/{slug}`), section bar, section colours in the reader theme.
- Changing newspaper-wide roles of existing accounts, password reset, lock/unlock.
- Trust switches and the approval chain (M3); `allowedActions` in `GET /api/me`.
- Cleaning up section roles of accounts deleted directly in Keycloak.

## Capabilities

### New Capabilities
- `sections`: section entity and `/api/sections` endpoints — list, create, update (name,
  colour), reorder, members; section roles and the delegation rule for assigning and removing
  them.
- `admin-sections`: the "Sections" screen of the admin app including member management.

### Modified Capabilities
- `accounts`: access for section editors; `sectionRoles` in list and create; the
  "at least one role" rule; delegation refusal for section roles.
- `admin-accounts`: entry point for section editors; section role choices in "New account";
  section roles shown in the account list.
- `api-authentication`: `GET /api/me` returns `sectionRoles`.

## Impact

- **backend**: Flyway `V3__sections.sql` (`section`, `section_role`); new `section` package
  (entities, service, resource, validation, delegation); `RoleDelegation` extended;
  `AccountResource`/`AccountService` switch from `@Blocking` to reactive with Keycloak calls
  offloaded, access check extended to section editors; `MeResource`/`MeDto` extended.
- **admin**: DTOs and `ApiClient`, `Route.Sections` and member screens, "New account" form,
  account list, header, German/English strings.
- **contract/docs**: `ai/primer/endpoints.md`, `http/sections.http`, `http/accounts.http`,
  `docs/roles-and-workflow.md`, `docs/architecture.md` (routes, data model),
  `ai/open-proposals.md`.
- **reader, deploy**: none.
