## Why

Five backlog items have piled up around the newsroom's daily work: approvers can only reject an
article with a note when a quick correction would do; a child who only takes photos needs a
section to get at the image view; editors cannot see how many articles a section holds or sort
the article list; and the caption of a body image is the only editor text field without spell
check. They share the same screens and contracts, so they ship as one change.

## What Changes

- **Corrections by higher levels.** A user whose approval level for the article's section lies
  above the author's level (section editor of the section, editor-in-chief, publisher) may edit
  someone else's article while a submission is pending (if their level reaches the pending level)
  and on published or offline articles — never on drafts. The newspaper setting
  `article.corrections` (default `true`, writable by publishers only, new action
  `CONFIGURE_CORRECTIONS`) switches this off again.
  - Every revision records its author. A correction always starts a new revision; the byline
    stays the article's author.
  - The chain of an article becomes the union of the chains of everyone who wrote the unpublished
    revisions ("contributors"), so a correction brings in the levels above the corrector, and
    trust in the original author does not skip them. A correction is not an approval: the
    corrector approves explicitly afterwards. A corrector may submit or publish their own
    correction of a published or offline article.
  - A correction keeps the article's section. Approve and reject accept the article `version` the
    approver saw and refuse a stale one with `409`, like saves already do.
  - Revisions carry their author, articles carry the author of the latest revision (`lastEditor`),
    and the admin editor shows who corrected the article and a word-level diff between a revision
    and its predecessor.
- **Reporter without a section.** A new newspaper-wide marker `sectionlessReporter` (own table in
  the Presserl database, label "Redakteur (ohne Ressort)") lets an account upload and edit its own
  images without writing articles. It is assigned by editors-in-chief and publishers. An account
  that loses its last section role and holds neither `PUBLISHER` nor `EDITOR_IN_CHIEF` gets it
  automatically. The new action `USE_MEDIA` (writers and sectionless reporters) replaces
  `WRITE_ARTICLES` as the gate of the media endpoints and of the "Images" header entry. A
  sectionless reporter starts the admin app in "Images".
- **Article counts and list sorting.** `GET /api/sections` reports for writers per section the
  number of live articles, the total number of articles in it now and the count per issue.
  `GET /api/articles` takes `sort=changed|newest|section`, and summaries gain `createdAt`. The
  admin section list shows the counts and the article lists offer the sort order.
- **Spell check for image-block captions.** The caption of a body image block becomes a
  spell-checked field with its own check key; choosing a suggestion is its own undo step.

## Capabilities

### New Capabilities

None. Every item extends an existing capability.

### Modified Capabilities

- `approval-chain`: the chain spans every contributor since the live revision; correctors may
  submit, approve and reject; approve and reject accept a version; frozen content admits
  corrections.
- `articles`: corrections by higher levels, revision authorship and new-revision rule, `lastEditor`,
  revision author in the history, section kept on correction, list sorting and `createdAt`.
- `accounts`: `sectionlessReporter` in list, create and role edit; who assigns it; it counts as a
  role.
- `api-authentication`: `GET /api/me` reports `sectionlessReporter` and the new actions `USE_MEDIA`
  and `CONFIGURE_CORRECTIONS`.
- `media`: media endpoints are gated by `USE_MEDIA` instead of `WRITE_ARTICLES`.
- `sections`: article counts in the section list; losing the last section role (member removal,
  section deletion) makes a sectionless reporter.
- `newspaper-settings`: new setting `article.corrections`.
- `admin-articles`: correction banner, revision authors and diff, sort chooser, spell-checked
  image-block captions.
- `admin-spell-check`: image-block captions are checked fields.
- `admin-accounts`: "Redakteur (ohne Ressort)" in list, create and role-edit forms.
- `admin-sections`: counts in the section list.
- `admin-shell`: "Images" follows `USE_MEDIA`; start screen for sectionless reporters.
- `admin-newspaper`: corrections switch.

## Non-goals

- A correction does not count as an approval, and it does not notify the author (there are no
  notifications yet).
- There is no merge of concurrent edits. The second save is refused and the editor offers a reload.
- Sections have no history. "Total" counts articles in the section now, so moved articles count
  only in their current section.
- There is no separate photographer role, no Keycloak group and no realm change for the
  sectionless reporter.
- Correctors do not withdraw submissions (they reject instead), and a correction never changes the
  byline.
- There is no paging or free-text search in the article list.

## Impact

- **Backend:**
  - Code: `ArticlePolicy`, `ApprovalChain`, `StaffingService`/`Staffing`, `ArticleService`
    (save/submit/approve/reject/list), revision entity and DTOs, `ArticleSummaryDto`/`ArticleDto`,
    `Newsroom`/`NewspaperAction`/`MeDto`, account creation/role edit/validator/policy/DTOs,
    `SectionMembers`/`SectionService`/`SectionDto`, `MediaService`, `EffectiveSettings`/
    `WritableSettings`/`NewspaperConfig`.
  - Flyway `V14__revision_author.sql` and `V15__sectionless_reporter.sql`.
- **Reader:** unchanged. The byline still shows the article's author.
- **Admin:** editor (corrections banner, image-caption spell check), revision list and diff view,
  article list sorting, section list counts, account list and forms, navigation/start screen,
  newspaper settings screen, API client and DTOs, localized strings (de/en).
- **Deploy:** `.env.example` and `INSTALL.md` document `PRESSERL_ARTICLE_CORRECTIONS`. The realm is
  unchanged.
- **Contract/Docs:** `ai/primer/endpoints.md`, `http/articles.http`, `http/sections.http`,
  `http/accounts.http`, `http/newspaper.http`, `http/media.http`, and
  `docs/roles-and-workflow.md` (corrections, sectionless reporter).
- **Backlog:** the five entries leave `ai/open-proposals.md`.
