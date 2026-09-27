## 1. Backend — policy and validation

- [x] 1.1 Add `EDIT_ROLES` as first constant of `AccountAction`; `AccountPolicy.permitted` treats it like `RESET_PASSWORD` (javadoc table updated)
- [x] 1.2 Extend `AccountPolicyTest`: `EDIT_ROLES` for publisher/editor-in-chief/section editor, own account, publisher target, locked target, new `allowedActions` order
- [x] 1.3 Add `EditRolesRequest(roles, sectionRoles)` and `AccountRequestValidator.validateRoles(JsonNode)` (both fields required, known roles, duplicates collapsed, section roles once each, at least one role, unknown fields rejected), sharing the existing readers
- [x] 1.4 Unit tests for `validateRoles`: missing `sectionRoles`, missing `roles`, empty both, duplicate section, unknown role, unknown field, valid body

## 2. Backend — writing the roles

- [x] 2.1 `AccountService.changeGroups(id, added, removed)`: join/leave groups, revert completed steps on failure (error log when the revert fails), rethrow; unit test with a failing Keycloak step
- [x] 2.2 `SectionRoleStore.replace(accountId, requested, assignedBy)` in one transaction: delete removed, update changed (`assignedBy`/`assignedAt`), insert new, leave unchanged rows untouched
- [x] 2.3 `AccountRoleEdit.edit(requester, id, request)`: load target, check `EDIT_ROLES` (`403` field `null`), unknown sections (`400 sectionRoles`), diff checks via `RoleDelegation` / `SectionDelegation` (`403 roles` / `403 sectionRoles`), no-op for an empty diff, Keycloak then database with compensation (D4), INFO log with before/after
- [x] 2.4 Unit tests for the diff checks of `AccountRoleEdit` (unchanged `READER` kept by a section editor, removing `READER` refused, section outside scope refused, editor-in-chief adding `PUBLISHER` refused)
- [x] 2.5 `PUT /api/accounts/{id}/roles` in `AccountResource`, returning the `AccountDto` with `allowedActions` for the requester

## 3. Backend — integration tests

- [x] 3.1 `AccountResourceTest`: exact `allowedActions` lists updated to include `EDIT_ROLES`
- [x] 3.2 `AccountResourceTest`: publisher makes `reader` editor-in-chief (`GET /api/me` after a new login shows it), moves a reporter from `Sport` to `Kultur`, unchanged body answers `200`
- [x] 3.3 `AccountResourceTest`: `400` no role left / missing `sectionRoles` / unknown section (account unchanged), `404` unknown id, `403` own account, editor-in-chief on editor-in-chief, section editor on plain reader
- [x] 3.4 `AccountResourceTest`: section editor keeps `READER` while promoting a reporter; `403 roles` when removing `READER`; `403 sectionRoles` outside their section
- [x] 3.5 Compensation test: database step fails → Keycloak groups restored (service-level test with a failing store)

## 4. Admin

- [x] 4.1 `ApiClient.editRoles(id, EditRolesRequest)` and `EditRolesRequest` DTO; `ApiClientTest` and `DtoTest` cover request body and `EDIT_ROLES` parsing
- [x] 4.2 Admin `AccountAction` gains `EDIT_ROLES`; row action "Edit roles" opens `Route.EditRoles(account, assignableRoles, sections)`
- [x] 4.3 Move the field-error mapping of `NewAccountModel` into a shared helper used by both models
- [x] 4.4 `EditRolesModel`: preset from the account, editable/read-only newspaper roles and sections, omitted sections, `canSave`, submit with full state, error mapping; `EditRolesModelTest`
- [x] 4.5 `EditRolesScreen` (reusing `SectionRoleRow`), "Save" returns to the reloaded list, "Cancel" sends nothing
- [x] 4.6 German and English strings ("Rollen bearbeiten" / "Edit roles", form title, read-only hint); `StringsTest` if it enumerates keys

## 5. Contract / Docs

- [x] 5.1 `ai/primer/endpoints.md`: `EDIT_ROLES` in the account-actions table and `AccountDto` order, new `PUT /api/accounts/{id}/roles` section (body, response, errors, side effects, token-refresh note)
- [x] 5.2 `http/accounts.http`: role edit success, no-op, `400` no role, `403` editor-in-chief on editor-in-chief
- [x] 5.3 `docs/roles-and-workflow.md`: role changes of existing accounts in the Accounts section; `docs/architecture.md` if it lists the account routes
- [x] 5.4 `ai/open-proposals.md`: drop the `accounts-edit-roles` mention from M2 once implemented

## 6. Verification

- [x] 6.1 Run `AccountPolicyTest`, `AccountServiceTest`, `AccountResourceTest`, the new backend unit tests and the section tests touching `SectionRoleStore`
- [x] 6.2 Run `./gradlew check` in `admin/`
- [x] 6.3 Run `http/accounts.http` against `quarkus:dev`
- [x] 6.4 Drive the admin app headless (Playwright): publisher promotes `reader`, section editor edits their reporter (read-only "Leser"), cancel sends nothing; stop every server started
