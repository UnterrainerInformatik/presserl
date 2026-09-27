## 1. Backend — migration and bootstrap

- [x] 1.1 Add `V5__article_section_not_null.sql` (create `General` only when unfiled articles exist and no section does; file unfiled articles under the first section by position; `SET NOT NULL`)
- [x] 1.2 Verify the fill step: a test that migrates a separate schema to V4 via the Flyway API, inserts sections and an article without a section, migrates to V5 and asserts the article belongs to the first section (and the `General` case with no section)
- [x] 1.3 Reduce `DefaultSectionBootstrap` to "create the default section when no section exists"; mark `ArticleEntity.sectionId` `@Column(nullable = false)`
- [x] 1.4 Rewrite `DefaultSectionBootstrapTest` for the remaining behaviour (fresh installation creates `General`; existing sections → nothing created)

## 2. Backend — delete endpoint

- [x] 2.1 `SectionService.delete(id)`: `404` unknown, `409` (field `null`, message with the article count) while articles exist, delete, compact positions; translate a foreign-key violation (`23503`) into the same `409`; INFO log with section and acting user
- [x] 2.2 `DELETE /api/sections/{id}` in `SectionResource` via `manager()` (`403` for others), answering `204`
- [x] 2.3 `SectionResourceTest`: delete empty section and positions compacted; `409` with a draft (section and draft unchanged); roles removed with the section; `403` for section editor and reader; `404` unknown id; deleting the only section

## 3. Admin

- [x] 3.1 `ApiClient.deleteSection(id)`
- [x] 3.2 Pure mapping of the delete result (success / `409` / other error) to the message shown, with `kotlin.test` tests
- [x] 3.3 Section list: "Delete" button for `canManage`, in-app confirmation naming the section, reload after every outcome, banner for errors
- [x] 3.4 German and English strings (`delete`, confirmation title/text, `section_not_empty`)

## 4. Contract / Docs

- [x] 4.1 `ai/primer/endpoints.md`: access table, new `DELETE /api/sections/{id}` entry, default-section paragraph (startup only creates it when no section exists)
- [x] 4.2 `docs/architecture.md`: REST sketch line for `DELETE /api/sections/{id}` (implemented), data model note (article section `NOT NULL`)
- [x] 4.3 `http/sections.http`: create a throw-away section and delete it (`204`), `409` for a section with an article, `403` for the reader, `404` for an unknown id

## 5. Verification

- [x] 5.1 Run the backend section, article and bootstrap tests and the admin unit tests
- [x] 5.2 Run `http/sections.http` against a live backend
- [x] 5.3 Drive the admin app headless (Playwright): delete an empty section, cancel a confirmation, get the `409` hint for a section with articles; stop every server started
