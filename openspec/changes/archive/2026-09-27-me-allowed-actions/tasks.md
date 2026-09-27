## 1. Backend

- [x] 1.1 Add enum `NewspaperAction` (`WRITE_ARTICLES`, `MANAGE_SECTIONS`, `ASSIGN_SECTION_ROLES`, `ADMINISTER_ACCOUNTS`) in `auth`, documented like `ArticleAction`
- [x] 1.2 Add `Newsroom.allowedActions()` mapping per design D2; confirm `MANAGE_SECTIONS` and `ASSIGN_SECTION_ROLES` use the same predicates as `SectionService` (`canManage`, create/update/order) and `SectionDelegation`, extracting a shared `Newsroom` method where they re-state the condition
- [x] 1.3 Extend `MeDto` with `List<NewspaperAction> allowedActions`; `MeResource` builds the `Newsroom` via `NewsroomService` and combines it with `namedRolesOf`
- [x] 1.4 Unit tests in `NewsroomTest`: actions for publisher, editor-in-chief, reader only, no roles, reporter only, section editor only, reporter in one and section editor in another section (order checked)
- [x] 1.5 `@QuarkusTest` for `GET /api/me`: publisher gets all four actions; user without groups gets `[]`; `SECTION_EDITOR` in `Sport` gets the three actions; `REPORTER` gets `["WRITE_ARTICLES"]`; changing the section role changes the next response with the same token

## 2. Admin

- [x] 2.1 Add `allowedActions: List<String> = emptyList()` to `MeDto` in `Dtos.kt`; extend `DtoTest` (with, without, and with an unknown action)
- [x] 2.2 Add a pure function mapping `allowedActions` to the visible header entries (none when fewer than two) and use it in `App.kt`; remove `canAdministerAccounts` and its callers
- [x] 2.3 Show the notice `no_writing_role` instead of the route stack when `WRITE_ARTICLES` is missing (no `/api/articles` request); add the de/en strings
- [x] 2.4 Kotlin tests for the entry mapping (publisher, section editor, reporter, reader, unknown value) and for the strings in `LabelsTest` if applicable

## 3. Contract/Docs

- [x] 3.1 Update `GET /api/me` in `ai/primer/endpoints.md` (example, `allowedActions` table, ordering, clients ignore unknown values; drop the "M2 extends this response" note)
- [x] 3.2 Update the `/api/me` line in `docs/architecture.md`
- [x] 3.3 Extend `http/me.http` to assert the publisher's `allowedActions`

## 4. Verification

- [x] 4.1 Run backend tests for `auth` and `section` (`NewsroomTest`, `/api/me` tests, `SectionResourceTest`) and the admin `commonTest` suite
- [x] 4.2 Run `http/me.http` against a live backend
- [x] 4.3 Headless check of the admin app (Playwright): publisher sees three entries; reporter sees none and the article list; reader sees the notice; stop every server started
