## Context

- `section_role.section_id` already references `section` with `ON DELETE CASCADE`;
  `article.section_id` references it with `ON DELETE RESTRICT` and is still nullable (V4).
- Positions carry no unique constraint and are rewritten as a whole by `reorder`.
- `DefaultSectionBootstrap` runs after Flyway and creates the default section when no section
  exists **or** articles without a section exist, then files those articles. The default name
  (`section.default`) is configuration/i18n resolved in Java, so plain SQL cannot know it.
- Section refusals already map through `SectionException` → `ApiErrorDto`
  (`{"errors": [{"field": …, "message": …}]}`); access refusals are `403`/`404` with empty bodies.

## Goals / Non-Goals

**Goals:**
- Delete an empty section atomically with its roles and compact the remaining positions.
- Never lose an article: the check and the delete cannot be raced past.
- `article.section_id NOT NULL` without editing V4.

**Non-Goals:**
- Moving articles on delete, protecting the default section, soft delete (see proposal).

## Decisions

### REST contract

```
DELETE /api/sections/{id}
Authorization: Bearer …

204  (empty body)
403  (empty body)                        not PUBLISHER / EDITOR_IN_CHIEF
404  (empty body)                        unknown id
409  { "errors": [ { "field": null,
                     "message": "section still contains 3 article(s); move them to another section first" } ] }
```

`field` is `null` like the article `409`s — there is no request field to point at. The admin app
does not parse the message; it shows its own localized text for any `409`.

### Service: one transaction, database as the final guard

`SectionService.delete(id)` (`@WithTransaction`): find (→ `404`), count articles with
`sectionId = id` (→ `409` when > 0), delete the section (roles go by `ON DELETE CASCADE`), then
load the remaining sections by `position, id` and set `0, 1, 2, …`.

A concurrent `POST`/`PUT` of an article into the section between the count and the delete is
caught by the existing `ON DELETE RESTRICT`: the foreign-key violation (SQLState `23503`) is
translated into the same `409`. The count exists for the normal case and the message; the
constraint makes it race-free. *Alternative:* `SELECT … FOR UPDATE` on the section row — rejected,
article writes do not lock the section, so it would not help.

Deleting the roles explicitly in Java instead of relying on the cascade was considered; the
cascade is already in the schema and runs in the same transaction, so Java stays smaller. The
log line (INFO: section id/name, acting user) mirrors the member removal.

### Migration V5: fill, then constrain

```sql
-- Files articles still without a section (installations that skipped the startup bootstrap)
-- under the first section by position; creates 'General' only when no section exists at all.
INSERT INTO section (name, slug, color, position, created_at)
SELECT 'General', 'general', 'red', 0, now()
WHERE EXISTS (SELECT 1 FROM article WHERE section_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM section);
UPDATE article SET section_id = (SELECT id FROM section ORDER BY position, id LIMIT 1)
WHERE section_id IS NULL;
ALTER TABLE article ALTER COLUMN section_id SET NOT NULL;
```

The literal `General` equals the code default of `section.default`; this path is practically
unreachable (the bootstrap has filed articles at every start since V4) and only exists so the
`ALTER` can never fail. *Alternative:* a Java Flyway migration resolving the configured default
name — rejected as heavy machinery for a dead path. *Alternative:* failing the migration with a
message — rejected, a failing startup is worse than filing under the first section.

### Bootstrap shrinks to "no section at all"

With `NOT NULL`, "articles without a section" cannot exist, so `DefaultSectionBootstrap` only
checks `SectionEntity.count() == 0`. The "All sections gone" rule on `POST /api/articles` stays
as is, so deleting the last section is harmless. `ArticleEntity.sectionId` gets
`@Column(nullable = false)` for documentation; validation of the schema is not enabled, so this
is cosmetic.

### Admin app

`ApiClient.deleteSection(id)` (`DELETE`, expects `204`; errors raise the existing API exception
carrying the status). `SectionRow` gets a "Delete" button next to "Edit" when `canManage`.
`SectionListScreen` holds `confirmDelete: SectionDto?` and shows an `AlertDialog` like the member
removal. After the call: success → reload; `409` → banner `section_not_empty` (localized, naming
the section); other error → banner with the error text; the list is reloaded in every case. The
message mapping lives in a small pure function (status → string key) so it is unit-testable
without Compose.

## Risks / Trade-offs

- [An installation with articles without a section gets them filed under the first section, not
  the configured default section] → only possible if the bootstrap never ran since V4; no such
  installation exists (no fork live). Articles can be moved afterwards.
- [Tests that insert articles with `section_id NULL` (bootstrap tests) break] → rewrite them to
  cover the remaining behaviour (create when no section exists, leave existing sections alone);
  add a migration test for the fill step.
- [Deleting the last section makes the section list empty until the next article creation or
  restart recreates `General`] → accepted; matches the existing "All sections gone" rule.

## Migration Plan

V5 runs automatically at startup (`quarkus.flyway.migrate-at-start`). Rollback: the constraint
can be dropped by a later correction migration; V5 itself is never edited once applied.
