## Context

- `ArticleResource` is guarded by `@RolesAllowed({"PUBLISHER", "EDITOR_IN_CHIEF"})`; section roles
  are not in the token, so section members cannot pass it. `SectionResource`/`AccountResource`
  already use `@Authenticated` plus a per-request `Newsroom` (`NewsroomService.of(user)`, one
  indexed query on `section_role`).
- `ArticlePolicy.verdict(action, CurrentUser, ArticleEntity, latestRevision)` is a pure function
  checked in the order role → ownership → state (`FORBIDDEN` → `403`, `CONFLICT` → `409`);
  `allowedActions` is derived from it. The reader does not use the policy.
- `section.default` is a deployment-only setting (`NewspaperConfig.section().defaultName()`,
  env `PRESSERL_SECTION_DEFAULT`, default `General`). Section creation logic (last position,
  colour `palette[count % 8]`, slug with suffix, case-insensitive unique name) lives in
  `SectionService.create`.
- `article` rows exist in real databases (dev, the fork is not live yet); Flyway runs at start
  (`migrate-at-start`), before any application code.

## Goals / Non-Goals

**Goals:**
- One access model for articles built on `Newsroom`, reusable by the approval chain (M3).
- Keep the change additive for existing clients: every new request field optional, every new
  response field additive.

**Non-Goals:**
- A `NOT NULL` constraint on `article.section_id` in this change (see D1).
- Per-section settings, section filters on the article list.

## Decisions

### D1 — Data model (`V4__article_section.sql`)

```sql
ALTER TABLE article ADD COLUMN section_id BIGINT REFERENCES section (id) ON DELETE RESTRICT;
CREATE INDEX article_section_id_idx ON article (section_id);
```

The column stays nullable in the database; the application guarantees a section for every
article (create always sets one, save never clears it, the startup bootstrap D5 backfills old
rows). A SQL-only backfill would need the configured default name and the Java slug rules
(umlauts, suffixes) inside the migration — a Flyway placeholder plus a re-implementation of
`Slugs` in SQL. Duplicating the slug rules is worse than a nullable column. A later migration can
add `NOT NULL` once every installation has run D5. `ON DELETE RESTRICT` protects articles until
"deleting sections" defines what happens to them.

`ArticleEntity.sectionId` is a plain `Long` column (no `@ManyToOne`), like `authorSub`; section
data for responses is loaded per request (D6).

### D2 — Writer check via `Newsroom`

`ArticleResource` becomes `@Authenticated`. Each request loads the `Newsroom` and refuses with
`403` (empty body, as `@RolesAllowed` did) unless `newsroom.isWriter()`:

```java
public boolean isWriter()               { return isAdministrator() || !sectionRoles.isEmpty(); }
public boolean mayWriteIn(long section) { return isAdministrator() || sectionRoles.containsKey(section); }
public boolean isSectionEditorOf(long section) { return roleIn(section).orElse(null) == SectionRole.SECTION_EDITOR; }
```

`isAdministrator()` (publisher or editor-in-chief) already exists. Alternatives: putting section
roles into the token (rejected in `sections-and-section-roles`), a JAX-RS filter (would load the
newsroom twice; the resource needs it anyway).

### D3 — `ArticlePolicy` on `Newsroom`

Signature becomes `verdict(action, Newsroom, ArticleEntity, latestRevision)`; still pure and
unit-tested.

| Action | FORBIDDEN unless | CONFLICT when |
|---|---|---|
| `EDIT` | author ∧ `mayWriteIn(article.section)` | — |
| `DELETE` | author ∧ `mayWriteIn(article.section)` | ever published |
| `PUBLISH` | author ∧ `PUBLISHER` | `PUBLISHED` without unpublished changes |
| `TAKE_OFFLINE` | author ∨ `EDITOR_IN_CHIEF` ∨ `PUBLISHER` ∨ `isSectionEditorOf(article.section)` | not `PUBLISHED` |

`TAKE_OFFLINE` for the author does not require section access: withdrawing is the safe direction
(`docs/roles-and-workflow.md`). A new pure `ArticlePolicy.visible(Newsroom, ArticleEntity)`:
administrator ∨ author ∨ `isSectionEditorOf(article.section)`.

Order of checks per endpoint: writer (`403` empty) → load article → not visible → `404` → policy
(`403`/`409`) → content validation of `sectionId` target (D4).

### D4 — `sectionId` in create and save

`ArticleContentValidator` accepts `sectionId` (integral, positive) on both create and save and
returns it in `Request` as `Long` (`null` = not given). Type errors join the other `400` errors.
Existence and permission are checked in `ArticleService` inside the transaction:
unknown section → `400 sectionId`; `!mayWriteIn(target)` → `403` with field `sectionId`
(`ArticleException.forbidden(field, message)`, same error body as accounts' `403 roles`).
A save whose content equals the latest revision's content (text fields after trimming, body by
Jackson `JsonNode.equals`, which ignores key order) touches no revision. So changing only the
section of a published article updates `article.section_id`, `updated_at` and `version` without
a new revision and without `hasUnpublishedChanges`. Alternative: always follow the old rule
(a published article would get an identical revision `n+1` and show "unpublished changes" just
because it was moved) — rejected. Side benefit: an autosave without edits no longer creates an
empty revision after a publish.

Request/response shapes:

```json
POST /api/articles        { "sectionId": 3, "headline": "Hello" }        // sectionId optional
PUT  /api/articles/42     { "version": 5, "sectionId": 4, "kicker": "", ... } // sectionId optional
ArticleDto / ArticleSummaryDto:
  { "id": 42, "section": { "id": 4, "name": "Kultur", "slug": "kultur", "color": "blue" }, ... }
403 { "errors": [ { "field": "sectionId", "message": "you may not write in this section" } ] }
400 { "errors": [ { "field": "sectionId", "message": "unknown section" } ] }
```

### D5 — Default section

`SectionService.findByNameIgnoreCase(name)` and `ensureSection(name)` (find, else create with the
normal rules; a concurrent creation hitting the unique name index is answered by finding again).

- **Create without `sectionId`**: default section if it exists ∧ `mayWriteIn`; else the first
  section by position with `mayWriteIn`; else, only when no section exists at all,
  `ensureSection(default)`. A writer without any writable section cannot exist (writer ⇒
  administrator or section role), so no further fallback.
- **Startup** (`DefaultSectionBootstrap`, `@Observes StartupEvent`, runs once, after Flyway):
  if `section` is empty or `article` has rows with `section_id IS NULL`, `ensureSection(default)`
  and `UPDATE article SET section_id = :id WHERE section_id IS NULL` in one transaction; log the
  number of filed articles at INFO. Runs through `VertxContextSupport.subscribeAndAwait` so the
  reactive session has a Vert.x context; a failure fails startup (database problems are fatal
  anyway, unlike the Keycloak-dependent publisher bootstrap which retries).

Not recreating a renamed default section on every create keeps renaming meaningful; startup
recreation only happens when there is nothing to fall back to.

### D6 — Loading sections for responses

List: the existing single HQL query gains `section s` in the join
(`… from ArticleEntity a, ArticleRevisionEntity r, SectionEntity s where s.id = a.sectionId …`)
and the visibility predicate for non-administrators:
`(a.authorSub = :sub or a.sectionId in :editedSections)` (the second part omitted when empty).
Single article: one extra `SectionEntity.findById`. `ArticleView` carries the `SectionEntity`;
`SectionRefDto(id, name, slug, color)` is embedded in both DTOs.

### D7 — `canWrite` in `GET /api/sections`

`SectionDto` gains `canWrite = newsroom.mayWriteIn(section.id)`; the `Newsroom` is already
loaded there for `assignableRoles`.

### D8 — Admin app

- DTOs: `SectionRefDto`, `ArticleDto.section`, `ArticleSummaryDto.section`, `SectionDto.canWrite`;
  save request carries `sectionId`.
- `EditorModel` holds `sectionId` as part of the undoable document state, so autosave, undo/redo
  and the "unchanged → identical request" property cover it without special cases. The editor
  loads `GET /api/sections` once when opened; the chooser (exposed dropdown with colour dots) lists
  `canWrite` sections; if the article's current section is not among them the article is
  read-only anyway (`EDIT` missing).
- Field error `sectionId` is shown below the chooser.
- List entries show a colour dot plus section name next to the status. Section colours reuse
  `SectionPalette`.
- The article list is already the start screen for everyone; reporters no longer get `403`
  there, so no navigation change is needed.

## Risks / Trade-offs

- [Nullable `section_id` could be violated by a future code path] → invariant covered by a
  test that every create/save path leaves a non-null section, plus the startup backfill; `NOT
  NULL` migration listed as follow-up in `ai/open-proposals.md`.
- [Test fixtures that delete sections now fail on `ON DELETE RESTRICT`] → test cleanup deletes
  articles before sections.
- [The startup bootstrap creates `General` in the test database for every `@QuarkusTest` start]
  → section tests that assert exact section lists clean sections in `@BeforeEach` already; the
  default-section tests call the bootstrap method directly after cleaning.
- [Section editors see drafts of reporters in their sections] → intended (preparation for M3
  review); reporters see only their own articles.
- [An author removed from a section can no longer edit or delete their draft there] → the article
  stays visible to them (read-only) and can still be taken offline by them; to continue editing,
  someone must re-grant the section role (moving it requires `EDIT` as well).

## Migration Plan

1. Deploy: Flyway adds the column; the startup bootstrap files existing articles under the
   default section (creating it if needed).
2. Rollback: the previous version ignores `section_id` (Hibernate maps only known columns); the
   column and the default section stay and are reused on the next upgrade.
