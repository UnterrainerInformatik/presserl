## 1. Backend — data and shared pieces

- [x] 1.1 `V3__sections.sql` (`section`, `section_role`, indexes, check constraints) per design D1
- [x] 1.2 `SectionEntity`, `SectionRoleEntity` (Panache reactive), `SectionColor` (palette enum, lower-case wire values), `SectionRole` enum (`SECTION_EDITOR`, `REPORTER`)
- [x] 1.3 Extract `Slugs.fold`/suffixing from `UsernameDeriver` (D6); `UsernameDeriver` uses it, existing `UsernameDeriverTest` stays green; `SlugsTest` for 40-char/`section` fallback and `Sport & Spiel` → `sport-spiel`
- [x] 1.4 `Newsroom` record + `NewsroomService.of(CurrentUser)` (D2); unit tests for `isAdministrator`, `isSectionEditorAnywhere`, `roleIn`
- [x] 1.5 `SectionDelegation.assignable(Newsroom, sectionId)` and change/removal checks (D3) with unit tests covering the D3 table incl. reporter → none

## 2. Backend — reactive account endpoints

- [x] 2.1 `KeycloakCalls` helper on `Vertx#executeBlocking`; `@QuarkusTest` proving a Keycloak call followed by a Panache query in one request (spike first, D4 risk)
- [x] 2.2 `AccountResource` → `@Authenticated`, returns `Uni`; access via `Newsroom` (admin or section editor anywhere, else `403` empty body); existing account tests stay green
- [x] 2.3 `AccountDto.sectionRoles` (list: join `section_role` rows of all listed users, ordered by section position)
- [x] 2.4 `CreateAccountRequest.sectionRoles` + validation: not a list, unknown section, duplicate section, unknown role (`400 sectionRoles`); `roles` may be empty; both empty → `400 roles`
- [x] 2.5 Creation flow per D4: newspaper-role check (`403 roles`), section-role scope check (`403 sectionRoles`), Keycloak create, DB insert, compensating Keycloak delete on DB failure; log line with section roles, never the password
- [x] 2.6 `MeDto.sectionRoles` (`sectionId`, `sectionName`, `role`, by position); `MeResource` loads them

## 3. Backend — section endpoints

- [x] 3.1 `SectionRequestValidator` (name trimmed 1–40, no control chars, colour in palette, unknown fields; all violations at once)
- [x] 3.2 `SectionService`: list, create (last position, default colour `palette[count % 8]`, slug with suffix), update (keeps slug/position), reorder (exact id set, one transaction); `409 name` on case-insensitive duplicates (pre-check + unique index violation mapped)
- [x] 3.3 Members: list (Keycloak lookup of member ids via `KeycloakCalls`, missing users omitted, section editors first then username), assign/replace with delegation, remove with delegation; `404` for unknown section/account/membership, service accounts treated as unknown; INFO log per change with actor
- [x] 3.4 `SectionResource` at `/api/sections` (`@Authenticated`): `GET`, `POST` (`201` + `Location`), `PUT /{id}`, `PUT /order`, `GET /{id}/members`, `PUT|DELETE /{id}/members/{accountId}`; `SectionException` + mappers using the shared error body

## 4. Backend — tests

- [x] 4.1 `SectionResourceTest`: list (empty, order, `canManage`, `assignableRoles` for publisher/reader/section editor), create (slug `sport-spiel`, colour default, slug collision `sport-2`), `403` for reader, `400` blank name/bad colour/unknown field, `409` duplicate name, update keeps slug, `404` unknown id, reorder incl. `400 ids`
- [x] 4.2 Members tests: publisher makes `nogroups` section editor → `GET /api/me` shows it; section editor adds `reader` as reporter in own section (`200`) and is refused in another (`403`); reporter refused (`403`) for list and assign; replace role; delete (`204`) and delete non-member (`404`); unknown account (`404`); unknown role (`400`)
- [x] 4.3 Account tests: section editor → `200` list with `assignableRoles` `[]`, reporter → `403`; list shows `sectionRoles`; create with section role only (publisher and section editor); `403 sectionRoles` outside scope; `403 roles` for section editor with `READER`; `400 sectionRoles` unknown section; `400 roles` when both empty; log line contains section roles
- [x] 4.4 Unit test: DB failure after Keycloak creation deletes the Keycloak user (stubbed realm)
- [x] 4.5 Test isolation: section fixtures cleaned per test (sections and roles deleted in `@BeforeEach`/`TestSupport`)

## 5. Admin app

- [x] 5.1 DTOs (`SectionDto`, `SectionListDto`, `MemberDto`, `MemberListDto`, `SectionRoleDto`, `MeDto.sectionRoles`, `AccountDto.sectionRoles`, `CreateAccountRequest.sectionRoles`) and `ApiClient` methods `sections()`, `createSection()`, `updateSection()`, `reorderSections()`, `members()`, `assignMember()`, `removeMember()`; `ApiClientTest`/`DtoTest` cases
- [x] 5.2 Header: entries Articles · Sections · Accounts; Sections and Accounts shown for administrators and section editors anywhere (from `/api/me`); routes `Sections`, `SectionForm`, `SectionMembers`
- [x] 5.3 `SectionListScreen`: colour marker + name, empty hint, "New section"/"Edit"/up/down only with `canManage`, open members only with non-empty `assignableRoles`
- [x] 5.4 `SectionFormModel` (name, colour, server field errors, `canSave`) with unit tests; `SectionFormScreen` with eight swatches and localized colour names, default colour preselected on create
- [x] 5.5 `SectionMembersModel` (members, candidate accounts without a role in the section, assignable roles) with unit tests; `SectionMembersScreen` with add, change role, remove with in-app confirmation, error message + reload
- [x] 5.6 "New account": load sections, one row per section with assignable roles (none / roles), `NewAccountModel.canCreate` accepts section roles only; `NewAccountModelTest` extended; section-role field errors shown
- [x] 5.7 Account list shows section roles as "role label · section name" (section names from `GET /api/sections`)
- [x] 5.8 German and English strings (Ressort/Ressorts/Section(s), Ressortleiter/Section editor, Redakteur/Reporter, colour names, empty hint, confirmation); `StringsTest`/`LabelsTest` extended

## 6. Contract / Docs

- [x] 6.1 `ai/primer/endpoints.md`: new "Sections" part with all section endpoints; account list/create and `/api/me` additions; access and delegation tables
- [x] 6.2 `http/sections.http` (list, create, update, reorder, members, assign, remove, refusals) and `http/accounts.http`/`http/me.http` updated; run against `quarkus dev`
- [x] 6.3 `docs/roles-and-workflow.md`: delegation sentence "Everyone from section editor up…", reporters do not delegate
- [x] 6.4 `docs/architecture.md`: section routes, section palette keys in the data model
- [x] 6.5 `ai/open-proposals.md`: M2 trimmed to role changes, password reset, lock/unlock, `/api/me` scopes and allowed actions; mention `articles-in-sections` as next change (done at propose time)

## 7. Verification

- [x] 7.1 Run the backend suite and the admin suite (`reference_build_and_test.md`); both green
- [ ] 7.2 Manual check in dev mode: `chief` creates `Sport` and `Kultur`, reorders them, makes `nogroups` section editor of `Sport`; `nogroups` logs in, sees Sections/Accounts, creates a reporter for `Sport` and prints the slip — not performed: waived by Gerald on 2026-09-27 (covered by .http run and automated tests)
- [x] 7.3 `openspec validate sections-and-section-roles --strict`
