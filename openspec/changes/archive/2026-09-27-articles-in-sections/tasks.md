## 1. Backend — data and access model

- [x] 1.1 `V4__article_section.sql`: nullable `article.section_id` FK `ON DELETE RESTRICT`, index (D1); `ArticleEntity.sectionId`
- [x] 1.2 `Newsroom.isWriter()`, `mayWriteIn(sectionId)`, `isSectionEditorOf(sectionId)` (D2) with unit tests in `NewsroomTest`
- [x] 1.3 `ArticlePolicy` on `Newsroom` per the D3 table plus `visible(Newsroom, ArticleEntity)`; `ArticlePolicyTest` extended: reporter own draft `[EDIT, DELETE]`, author without section role (no `EDIT`/`DELETE`, still `TAKE_OFFLINE` when published), section editor on foreign published article in own section `[TAKE_OFFLINE]` and in another section `[]`, visibility matrix (administrator, author, section editor of the section, reporter of the section)
- [x] 1.4 `SectionService.findByNameIgnoreCase` and `ensureSection(name)` reusing the create rules; concurrent-create race answered by finding again (D5)

## 2. Backend — article endpoints

- [x] 2.1 `ArticleResource` → `@Authenticated`; load `Newsroom` per request; non-writers `403` with empty body (D2)
- [x] 2.2 Visibility: list query with section join and visibility predicate (D6); every `/{id}` endpoint (get, revisions, revision, save, delete, publish, offline) answers `404` for invisible articles
- [x] 2.3 `ArticleContentValidator` accepts optional integral `sectionId` on create and save; type errors reported with the other `400` errors
- [x] 2.4 `ArticleService.create`: explicit `sectionId` (unknown → `400 sectionId`, not writable → `403 sectionId`), otherwise the D5 fallback (default if exists and writable → first writable by position → create default when no section exists)
- [x] 2.5 `ArticleService.save`: optional section change with the same checks; unchanged content creates/changes no revision (D4); `updatedAt`/`version` still advance
- [x] 2.6 `SectionRefDto` (`id`, `name`, `slug`, `color`); `ArticleView` carries the section; `ArticleDto.section` and `ArticleSummaryDto.section`
- [x] 2.7 `DefaultSectionBootstrap` on `StartupEvent` via `VertxContextSupport.subscribeAndAwait`: ensure the default section when no section exists or section-less articles exist, file those articles in one transaction, INFO log with the count (D5)

## 3. Backend — sections

- [x] 3.1 `SectionDto.canWrite` from `newsroom.mayWriteIn(id)` (D7)

## 4. Backend — tests

- [x] 4.1 Test cleanup: `TestSupport` deletes articles before sections (FK `RESTRICT`); `ReaderFixtures` inserts keep working with and without `section_id`
- [x] 4.2 `ArticleResourceTest` writers: reporter creates in own section (`201`, `section` in body); reader without section role `403`; section editor creates; `POST {}` for publisher lands in `General`, for a reporter of `Kultur` only in `Kultur`; renamed default section is not recreated; no section at all → `General` created
- [x] 4.3 `ArticleResourceTest` sections: move draft (`200`, revision unchanged); move published article (no new revision, `hasUnpublishedChanges` false); save without `sectionId` keeps the section; reporter to foreign section `403 sectionId`; unknown section `400 sectionId`; non-numeric `sectionId` `400`; unchanged save of a published article creates no revision
- [x] 4.4 `ArticleResourceTest` visibility and actions: section editor lists reporter's draft of own section but not of another; reporter lists only own; reporter `GET`/revisions on foreign article `404`; author who lost the section role gets `403` on save and sees the article read-only; section editor takes an article of their section offline (`200`) and is `404` outside their visibility
- [x] 4.5 `DefaultSectionBootstrap` test: with section-less articles and no `General`, running the bootstrap creates `General` and files them; with sections and no section-less articles it creates nothing
- [x] 4.6 `SectionResourceTest`: `canWrite` for publisher (all true), reader (all false), reporter of `Sport` (only `Sport`)

## 5. Admin app

- [x] 5.1 DTOs: `SectionRefDto`, `ArticleDto.section`, `ArticleSummaryDto.section`, `SectionDto.canWrite`, save request `sectionId`; `ApiClient` sends `sectionId` on save; `DtoTest`/`ApiClientTest` cases
- [x] 5.2 `EditorModel`: `sectionId` as part of the undoable document state; dirty/autosave/undo/redo cover it; unchanged reopen sends an identical request incl. `sectionId`; field error `sectionId`; unit tests (change section → save with new id; undo restores it)
- [x] 5.3 `EditorScreen`: section line above the kicker; editable → dropdown with colour dots of `canWrite` sections (loaded once via `GET /api/sections`); read-only → name with colour dot; error text below the chooser
- [x] 5.4 `ArticleListScreen`: colour dot plus section name per entry
- [x] 5.5 German and English strings (Ressort/Section, chooser label); `StringsTest` extended

## 6. Contract / Docs

- [x] 6.1 `ai/primer/endpoints.md`: article access (writers incl. section members, visibility, `404` rule), `sectionId` in create/save, `section` in `ArticleDto`/`ArticleSummaryDto`, default-section rule, updated `allowedActions` table, unchanged-save rule; `canWrite` in `GET /api/sections`
- [x] 6.2 `http/articles.http` (create with/without `sectionId`, move, refusals) and `http/sections.http` (`canWrite`); run against `quarkus dev`
- [x] 6.3 `docs/architecture.md`: article data model (section no longer "later"), endpoint table (article access incl. section members)
- [x] 6.4 `ai/open-proposals.md`: remove `articles-in-sections` from M2 (done at propose time); add follow-up "`article.section_id NOT NULL` once all installations ran the default-section bootstrap"

## 7. Verification

- [x] 7.1 Run the backend suite and the admin suite (`reference_build_and_test.md`); both green
- [x] 7.2 Manual check in dev mode: `nogroups` (section editor of `Sport`) and `reader` (reporter of `Sport`) write drafts; `nogroups` sees `reader`'s draft read-only, `reader` does not see `nogroups`' draft; `chief` moves an article from `General` to `Sport` in the editor
- [x] 7.3 `openspec validate articles-in-sections --strict`
