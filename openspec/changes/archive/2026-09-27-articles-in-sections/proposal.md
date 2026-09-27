## Why

Sections and section roles exist since `sections-and-section-roles`, but articles do not belong
to a section yet and only publishers and editors-in-chief may write. Section editors and
reporters therefore hold roles that do nothing. Filing every article under a section and letting
section members write in their sections completes M2 and is the base for the approval chain
(M3), which starts from the author's role *in the article's section*.

## What Changes

- Every article **belongs to exactly one section**. `POST /api/articles` and
  `PUT /api/articles/{id}` accept an optional `sectionId`; article and summary responses carry
  `section` (`id`, `name`, `slug`, `color`).
- **Writers are extended**: besides `PUBLISHER` and `EDITOR_IN_CHIEF`, every account holding a
  section role (`SECTION_EDITOR` or `REPORTER`) in at least one section may use the article
  endpoints. Publishers and editors-in-chief write in every section; section editors and reporters
  only in their own sections. The article endpoints no longer answer section members with `403`;
  existing clients keep working (all new request fields are optional, all response fields
  additive).
- **Moving an article**: the author may change the section to any section they may write in,
  also after publication (the section is not part of a revision). To make this possible without
  a spurious revision, a save whose content equals the latest revision no longer creates a new
  revision after a publish.
- **Visibility**: publishers and editors-in-chief see every article; a section editor sees their
  own articles and all articles of the sections they edit; a reporter sees only their own
  articles. Articles outside a user's visibility answer `404`.
- **Allowed actions** extended: `EDIT` and `DELETE` additionally require write access to the
  article's section; `TAKE_OFFLINE` is also allowed for section editors of the article's section.
  `PUBLISH` is unchanged (author holding `PUBLISHER`); reporters and section editors cannot
  publish until the approval chain (M3) exists.
- **Default section** from `section.default` (code default `General`): at startup the backend
  creates it when no section exists at all or when articles without a section exist, and files
  those articles under it. A create without `sectionId` uses the default section when the user
  may write there (creating it on demand for publishers and editors-in-chief), otherwise the
  first section by position the user may write in.
- `GET /api/sections` reports per section whether the requesting user may write articles there
  (`canWrite`, additive).
- Admin app: section chooser in the editor (only sections with `canWrite`), section shown in both
  article lists and read-only views; section members get the article lists.
- Contract, primer, `.http` files and docs updated.

## Non-goals

- Reader changes: no section label, section pages (`/sections/{slug}`) or section colours in the
  reader yet.
- Publishing by reporters or section editors, `SUBMITTED`, review queue, trust (M3).
- `retract.author-can-retract` (reporters taking own articles offline is moot while they cannot
  publish; M3).
- Deleting sections (the foreign key refuses deleting a section that still holds articles).
- Filtering the article list by section.
- Reader access for reporters and section editors without the `reader` group on a private
  newspaper.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `articles`: writer definition incl. section members; section field on create/save/responses;
  default section; visibility; section-aware `EDIT`/`DELETE`/`TAKE_OFFLINE`; listing and
  revisions restricted to visible articles; unchanged saves create no revision.
- `sections`: `GET /api/sections` returns `canWrite` per section.
- `admin-articles`: section chooser in the editor, section in lists and read-only views, lists
  available to section members.

## Impact

- **backend**: Flyway `V4__article_section.sql` (nullable `article.section_id` FK, index);
  `ArticleEntity.sectionId`; `ArticleResource` switches from `@RolesAllowed` to a `Newsroom`-based
  writer check; `ArticlePolicy` takes the `Newsroom`; `ArticleService` list/get with visibility,
  create/save with section; `ArticleContentValidator` accepts `sectionId`; DTOs with `section`;
  new startup `DefaultSectionBootstrap`; `SectionListDto`/`SectionDto` gain `canWrite`.
- **admin**: DTOs and `ApiClient`, editor model/screen (section chooser, autosave carries
  `sectionId`), article list entries, German/English strings.
- **contract/docs**: `ai/primer/endpoints.md` (articles and sections), `http/articles.http`,
  `http/sections.http`, `docs/architecture.md` (article data model, endpoint table),
  `ai/open-proposals.md`.
- **reader, deploy**: none (`PRESSERL_SECTION_DEFAULT` already exists).
