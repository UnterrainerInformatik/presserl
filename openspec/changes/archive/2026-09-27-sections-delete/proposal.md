## Why

Sections can be created, renamed and reordered, but never removed — a section created by mistake
or no longer used stays in the newspaper forever. This closes M2. At the same time the last
M2 leftover, `article.section_id` still being nullable, can be closed: no real installation is
live yet, every installation has run the default-section bootstrap, and the application always
sets the column.

## What Changes

- New `DELETE /api/sections/{id}` for `PUBLISHER` and `EDITOR_IN_CHIEF`: `204` on success, `403`
  for others, `404` for an unknown id, `409` while the section still contains articles (any
  status). Articles have to be moved first (`PUT /api/articles/{id}` with `sectionId`). The
  section's roles are removed with it; the remaining sections' positions are compacted to
  `0, 1, 2, …`.
- New correction migration: files any article still without a section under the first section
  by position (creating a section `General` when articles without a section exist but no section
  does), then makes `article.section_id` `NOT NULL`. Applied migrations stay untouched.
- The startup default-section bootstrap only creates the default section when no section exists;
  filing articles without a section is left to the database constraint and the migration.
- Admin app: "Delete" on the section list for users with `canManage`, after an in-app
  confirmation; a `409` is shown as a localized hint that the section still contains articles.
- `ai/primer/endpoints.md`, `docs/architecture.md` (REST sketch, data model), `http/sections.http`
  and `ai/open-proposals.md` (M2 finished) are updated.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `sections`: new requirements "Delete a section" and "Every article has a section in the
  database" (upgrade filing and `NOT NULL`).
- `articles`: requirement "Default section" — the startup no longer files articles without a
  section (the database no longer allows them); it only creates the default section when no
  section exists.
- `admin-sections`: requirement "Section list" offers "Delete"; new requirement for deleting a
  section in the admin app.

## Non-goals

- Moving a section's articles as part of the delete (e.g. `?moveTo=`) — explicitly decided
  against; articles are moved one by one beforehand.
- Protecting the default section or the last section from deletion — the existing "All sections
  gone" rule already recreates the default section on the next article creation.
- Bulk moving of articles in the admin app.
- Soft delete / restoring deleted sections.

## Impact

- **backend**: `SectionResource`, `SectionService` (delete, position compaction, article check),
  new Flyway migration `V5__article_section_not_null.sql`, `DefaultSectionBootstrap`,
  `ArticleEntity` (non-null mapping), tests.
- **admin**: `ApiClient.deleteSection`, section list screen (delete action, confirmation, `409`
  message), German and English strings, tests.
- **reader**: none (the reader does not show sections yet).
- **deploy**: none; the migration runs automatically at startup.
- **docs/contract**: `ai/primer/endpoints.md`, `docs/architecture.md`, `http/sections.http`,
  `ai/open-proposals.md`.
