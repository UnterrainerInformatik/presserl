## 1. Backend — revision authorship (D1)

- [x] 1.1 Add `V14__revision_author.sql`: `author_sub`, `author_username` and `author_display_name` on `article_revision`, backfilled from `article`, then `NOT NULL`. Verify with a new `migration/RevisionAuthorMigrationTest` that existing revisions carry their article's author.
- [x] 1.2 Add the author fields to `ArticleRevisionEntity` and set them on every new revision from the saving user. Change the save rule so it overwrites only an unpublished latest revision written by the saver, and otherwise appends a revision. Verify with `ArticleResourceTest`: author autosave still produces one revision; a correction of an unpublished revision creates revision `2` by the corrector, and saving twice overwrites it.
- [x] 1.3 Add `lastEditor` to `ArticleDto`, `author` to `RevisionSummaryDto` and `RevisionDto`, and `createdAt` to `ArticleSummaryDto`. Verify with `ArticleResourceTest` assertions on the JSON of get, revisions list and revision.

## 2. Backend — chain over contributors (D2)

- [x] 2.1 Add the contributor query to `ArticleService`: single article and batched for lists, falling back to the author when empty. Verify with `ArticleResourceTest` cases for draft, correction, revisions above live, and offline without changes.
- [x] 2.2 Extend `Staffing`/`StaffingService` to a set of subjects (`TrustStore.scopesOf(Collection)`), with `levelOf(sub, sectionId)` derived from the holder sets. Verify with a unit test of `levelOf` for publisher, editor-in-chief, section editor and reporter.
- [x] 2.3 Rewrite `ApprovalChain.next` to take `Set<String> contributors` (union of the per-contributor chains, lock rule), and use it in submit, publish, approve and `chainIsEmpty`. Verify that `ApprovalChainTest` still passes unchanged for single contributors, and add table-driven cases: trust in the author does not skip the corrector's levels, lock with a corrector, and a publisher-only contributor gives an empty chain.

## 3. Backend — corrections (D3, D4)

- [x] 3.1 Add setting `article.corrections` (default `true`, `PRESSERL_ARTICLE_CORRECTIONS`, startup validation `true|false`) to `NewspaperConfig`/`EffectiveSettings`. Make it writable as a JSON boolean by publishers only in `WritableSettings`. Verify with newspaper settings tests: GET default, publisher PUT `false`, editor-in-chief `403`, `"no"` → `400`, invalid env value fails startup.
- [x] 3.2 Add the correction branch of `EDIT` to `ArticlePolicy`: switch on, approver level above the author's level, not a draft, pending level reachable. Extend `SUBMIT`/`PUBLISH` to contributors who may correct. Pass the switch via an `ArticleRules` record. Verify with `ArticlePolicyTest` covering every `allowedActions` scenario of the articles delta, including switch off, equal level and draft.
- [x] 3.3 In `ArticleService.save`, accept corrections, and refuse a different `sectionId` from a corrector with `403` naming `sectionId`. Keep the author's pending save at `409`. Verify with `ArticleResourceTest`: correction during review keeps `pendingLevel`, a correction of a draft gives `409`, the section move gives `403`, and a corrector cannot delete.
- [x] 3.4 Add an optional `version` to approve (optional body) and reject (with the note), returning `409` on mismatch, and extend `RejectRequestValidator`. Verify with `ApprovalChainResourceTest`: stale approve and stale reject give `409` with nothing changed, and no body behaves as before.
- [x] 3.5 Add end-to-end flows in `ApprovalChainResourceTest`:
  - a section editor corrects and approves, and the article waits for `EDITOR_IN_CHIEF` despite that level trusting the reporter;
  - the publisher corrects a published article and publishes directly;
  - a section editor submits their correction, and the article waits for `EDITOR_IN_CHIEF`;
  - a non-contributing corrector gets `403` on submit.

## 4. Backend — sectionless reporter (D7)

- [x] 4.1 Add `V15__sectionless_reporter.sql` and `SectionlessReporterStore`. Verify with a migration test that the table exists and is empty.
- [x] 4.2 Add `sectionlessReporter` to `Newsroom`, `mayUseMedia()`, and `NewspaperAction.USE_MEDIA` (after `WRITE_ARTICLES`) and `CONFIGURE_CORRECTIONS` (last). Add `sectionlessReporter` to `MeDto`. Verify with `AuthenticationTest` updated for every `/api/me` scenario, plus the "sectionless reporter uses media only" scenario.
- [x] 4.3 Switch `MediaService`/`MediaResource` from `requireWriter` to `requireMediaUser`. Verify with `MediaResourceTest`: a sectionless reporter uploads, lists and sees usage (`mayEdit` true for their unused image), and a reader still gets `403`. Verify with `MediaEditResourceTest`: a sectionless reporter edits their own unused upload.
- [x] 4.4 Accounts: add `sectionlessReporter` to `AccountDto`, `CreateAccountRequest` and `EditRolesRequest`, and `mayAssignSectionlessReporter` to `AccountListDto`. Update the validator (boolean type; the marker counts as a role). Only administrators may change the marker (`403` naming the field). Write it in the account-creation and role-edit transactions, and log it. Verify with `AccountRequestValidatorTest`, `AccountResourceTest` (editor-in-chief creates a photographer, section editor refused, marker-only account) and `AccountRoleEditTest` (explicit `false`, field absent).
- [x] 4.5 Add `LastSectionRule` for member removal, section deletion (collect member ids before the delete) and role edits without the field: set the marker unless the account holds `PUBLISHER`/`EDITOR_IN_CHIEF`, and log at INFO. Verify with the section tests (remove the last section vs. one of two, delete a section) and `AccountRoleEditTest` (section editor removes the last section → marker `true`).

## 5. Backend — counts and sorting (D8, D9)

- [x] 5.1 Add `ArticleCountsDto` and `SectionDto.articleCounts` (writers only; one grouped query). Verify with a section resource test: counts scenario, empty section, and reader gets `null`.
- [x] 5.2 Add the `sort` parameter `changed|newest|section` to `GET /api/articles` (unknown → `400` naming `sort`). Verify with `ArticleResourceTest`: newest-first and by-section order, plus unknown sort.

## 6. Admin — API client and models

- [x] 6.1 Update `Dtos.kt`/`ApiClient.kt`:
  - `lastEditor`, the revision `author` and `createdAt`;
  - `articleCounts`, `sectionlessReporter` and `mayAssignSectionlessReporter`;
  - `articles(sort=…)`, and approve/reject with `version`;
  - `updateNewspaperSettings` with `JsonElement` values.

  Verify with `DtoTest`/`ApiClientTest` decoding and request-body tests.
- [x] 6.2 Navigation/App: "Images" follows `USE_MEDIA`, and a user with `USE_MEDIA` but without `WRITE_ARTICLES` starts in "Images". Verify with `NavigationTest` for the new scenarios and a start-screen unit test.

## 7. Admin — editor, revisions and diff (D6, D11)

- [x] 7.1 Add the correction banner (the corrector sees whose article it is), a read-only section chooser for correctors, and the "last changed by … / Show changes" notice when `lastEditor ≠ author`. Verify with `EditorModelTest` state tests and localized strings (de/en).
- [x] 7.2 Before Approve/Reject, flush the autosave and send the returned `version`. A `409` shows the conflict banner with reload. Verify with `ReviewQueueTest`/`AutosaverTest` covering "correct and approve" and "stale approve".
- [x] 7.3 Add `ui/diff/WordDiff.kt` (word LCS) and block alignment. Verify with a new `WordDiffTest`: headline word change, added paragraph, removed block, replaced image, changed caption, identical texts.
- [x] 7.4 In the revision history, show the author per revision, "Changes" on revisions > 1, and `RevisionDiffScreen` (strikethrough/underline plus background, the newer revision's author named). Verify with a model test for loading `n` and `n-1`, and with the manual Playwright check in 9.3.
- [x] 7.5 Spell-check image-block captions: pass `checker` to `ImageBlockFields`, use `SpellCheckedTextField` with key `caption:<blockId>`, and add `SetImageCaption.ownStep`. Verify with `ImageBlockEditorTest`: a suggestion is its own undo step, and typing still coalesces. Verify with `SpellViewsTest`/`SpellCheckerTest`: the caption key is checked independently.

## 8. Admin — lists, sections, accounts, newspaper

- [x] 8.1 Article list sort chooser ("Last changed", "Newest first", "By section") with section headings, kept per session. Verify with a list model test for the requested `sort` and the heading grouping.
- [x] 8.2 Section list counts line ("2 online · 4 gesamt", "Ausgabe 2: 2 · Ausgabe 1: 1"). Verify with a formatting unit test (de/en) and the empty-section case.
- [x] 8.3 Account list label, the "Redakteur (ohne Ressort)" choice in the new-account and edit-roles forms (read-only without `mayAssignSectionlessReporter`), and auto-select when the last section is removed. Verify with `NewAccountModelTest`, `EditRolesModelTest`, `LabelsTest` and `AccountListModelTest`.
- [x] 8.4 Corrections switch on the newspaper screen (installation default / allowed / not allowed; disabled without `CONFIGURE_CORRECTIONS`). Verify with `NewspaperSettingsModelTest`: sends JSON `false`/`null`, and the read-only case.

## 9. Contract, docs, deploy and verification

- [x] 9.1 Update `ai/primer/endpoints.md`:
  - `ArticleDto.lastEditor`, `ArticleSummaryDto.createdAt`, revision `author`;
  - `sort`, approve/reject `version`, correction rules and `allowedActions`, contributors chain, the widened `503` set;
  - `/api/me` `sectionlessReporter` + `USE_MEDIA`/`CONFIGURE_CORRECTIONS`;
  - accounts fields, section `articleCounts`, media gate, `article.corrections`.

  Verify by reviewing the diff against the delta specs.
- [x] 9.2 Update the `.http` files: `articles.http` (correction, stale approve, sort), `sections.http` (counts, last-section removal), `accounts.http` (photographer create/edit), `newspaper.http` (`article.corrections`), `media.http` (sectionless reporter), `me.http`. Update `docs/roles-and-workflow.md` (corrections, sectionless reporter, overrides table) and `deploy/.env.example`/`INSTALL.md` (`PRESSERL_ARTICLE_CORRECTIONS`). Verify by running the `.http` files against a live dev backend.
- [x] 9.3 Manual check with headless Playwright against the dev stack:
  - section editor corrects and approves a reporter article; the reporter sees "Show changes" with the word diff;
  - sectionless reporter logs in and lands in "Images";
  - sort chooser and section counts;
  - caption spell check in an image block;
  - newspaper corrections switch.

  Stop every server started.
- [x] 9.4 Run the relevant suites: backend `ApprovalChain*`, `ArticlePolicy*`, `ArticleResource*`, `Account*`, `Media*`, section/newspaper/auth tests, and the migration tests; admin `commonTest`. Verify that all pass.
- [x] 9.5 Delete the five entries (image-caption spell check, analytics package, reporter without a section, higher levels edit articles, publishers may edit other authors' articles) from `ai/open-proposals.md`. Verify that the file no longer lists them.
