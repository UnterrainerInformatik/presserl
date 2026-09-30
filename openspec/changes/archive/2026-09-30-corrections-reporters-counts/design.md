## Context

See proposal.md for the motivation. The current state that shapes the approach:

- **Authorship and the chain.** Revisions (`article_revision`, PK `article_id, number`) carry no
  author. The approval chain is keyed to `article.authorSub` everywhere:
  - `ApprovalChain.next(above, sectionId, authorSub, staffing, locked)`;
  - `ArticlePolicy.chainIsEmpty`;
  - `StaffingService.load(authorSub)`, which loads the trust entries of one person;
  - the list query's `authorSub <> :viewer`.
- **Editing and concurrency.** `ArticlePolicy` allows `EDIT` to the author only (`CONFLICT` while
  pending). `ArticleService.save` overwrites the latest revision while it was never published.
  Saves already carry `version` (409 on mismatch); approve, reject and submit do not. The admin
  editor already has a conflict banner with "load current state" (`Autosaver` →
  `SaveState.Conflict`).
- **Media access.** Media endpoints are gated by `MediaService.requireWriter` (=
  `Newsroom.isWriter()`, the same predicate as `WRITE_ARTICLES`). The admin `Navigation` derives
  "Images" from `WRITE_ARTICLES`, and `App` starts with the "no writing role" notice without it.
- **Section roles.** They live in `section_role (section_id, account_id, role)` with a non-null
  section. Member removal and section deletion (cascade) never check what remains.
- **Settings.** Newspaper settings are a jsonb map on the singleton `NewspaperEntity`, resolved by
  `EffectiveSettings`. `WritableSettings` only parses textual enum values; the only boolean
  (`retract.author-can-retract`) is not writable.
- **Lists and counts.** `GET /api/articles` orders by `updatedAt desc, id desc` hard-coded, and the
  summary has no `createdAt`. `GET /api/sections` has no counts, and readers may call it.
- **Image-block captions.** The caption in `ImageBlockFields` is a plain `OutlinedTextField`, and
  `SetImageCaption` has no `ownStep`.
- **Migrations.** The latest Flyway migration is `V13__article_revision_media.sql`.

## Goals / Non-Goals

**Goals:**
- The server stays the only place that computes the chain and the actions. Clients get new
  behaviour only through `allowedActions`, `lastEditor` and new fields.
- The chain definition stays one rule ("contributors' chains, united"), so that today's behaviour
  is the special case of a single contributor.
- The sectionless reporter needs no Keycloak change and takes effect immediately.

**Non-Goals:**
- Diffing on the server, and three-way merges.
- Recording who started a submission.
- Counting historic section membership.

## Decisions

### D1 — Revision authorship in `article_revision`
`V14__revision_author.sql` adds `author_sub`, `author_username` and `author_display_name` (TEXT). It
backfills them from `article.author_*` and then sets them `NOT NULL`. The V13 trigger on
`article_revision` fires only `AFTER INSERT OR UPDATE OF lead_image_media_id, body`, so the backfill
does not touch it.

`ArticleRevisionEntity` gets the three fields. They are set on every new revision from the saving
user's token (snapshot, like `article.author_*`).

Save rule: overwrite the latest revision only if `publishedAt == null` **and**
`latest.authorSub == saver.sub`; otherwise append `number + 1`. Equal content still touches
nothing.

*Alternative:* a separate `article_contribution` table. Rejected, because the revision already is
the unit of change and the diff view needs the author per revision anyway.

### D2 — Chain over contributors
Contributors (spec approval-chain) are computed by one query:

```sql
select distinct r.author_sub from article_revision r
where r.article_id = :id and (:live is null or r.number > :live)
```

For lists this is batched as `where r.article_id in (:ids)`, grouped by article. If the result is
empty, the contributor is the article author.

`ApprovalChain` gets:

```java
static Optional<ApprovalLevel> next(Optional<ApprovalLevel> above, long sectionId,
        Set<String> contributors, Staffing staffing, boolean locked)
```

It returns the lowest level above `above` that belongs to the union of the per-contributor chains.
A level belongs to contributor *c*'s chain when it lies above `levelOf(c)`, is staffed by someone
other than *c*, and does not trust *c* (or the lock forces `PUBLISHER`). `levelOf(c)` comes from
`Staffing`, which already holds the publisher, editor-in-chief and section-editor sets: `PUBLISHER`
if *c* is in the publisher holders, else `EDITOR_IN_CHIEF` if *c* is in the editor-in-chief
holders, else `SECTION_EDITOR` if *c* is a section editor of the section, else `REPORTER`. This
replaces `authorLevel(Newsroom, …)` for submit, publish and approve, so a contributor's level no
longer depends on who is asking.

`Staffing`/`StaffingService.load` take a `Set<String>` of subjects and load the trust entries for
all of them (`TrustStore.scopesOf(Collection)`, one query). `forArticles` loads staffing when the
requester is the author or a contributor of any listed article and does not hold `PUBLISHER`, or
when `SUBMIT`/`PUBLISH` might be offered to a corrector. In practice the condition becomes "the
requester may write, or is a corrector, and the list is non-empty".

The list endpoint already needs staffing for `awaitingMe`. The extra cost is one trust query per
request, not per article.

*Alternative:* "chain of the latest revision's author only". Rejected: an author edit after a
correction would silently drop the corrector's levels.

*Alternative:* "a correction settles the corrector's levels". Rejected by the product decision that
a correction is no approval.

### D3 — Correction verdict in `ArticlePolicy`
`EDIT` becomes:

```
author:     writingAuthor ? (pending ? CONFLICT : ALLOWED) : FORBIDDEN
non-author: !corrections || approverLevel <= levelOf(author) ? FORBIDDEN
            : status == DRAFT ? CONFLICT
            : pending && approverLevel < pendingLevel ? FORBIDDEN
            : ALLOWED
```

`levelOf(author)` needs `Staffing` (see D2). The policy therefore receives staffing for articles
where the requester holds an approval level. For administrators this is every article, but the
load is one set of queries per request.

The switch value comes from `EffectiveSettings` (`article.corrections`). It is passed into the
policy through a small `ArticleRules` record (`corrections`), next to the existing retract override,
so that `ArticlePolicy` stays a pure function.

`SUBMIT`/`PUBLISH` allow `(writingAuthor || (isContributor && EDIT-as-corrector would be ALLOWED
ignoring pending))`. The existing state conditions (`goOnline`) apply, and the decision between
SUBMIT and PUBLISH uses the contributor chain. `WITHDRAW`, `DELETE` and `APPROVE`/`REJECT` stay as
they are: author-only, author-only, and non-author.

The save endpoint compares `sectionId` for correctors: a different section → `ArticleException`
403 with field `sectionId`.

### D4 — Versioned approve and reject
`approve` accepts an optional body `{"version": 7}`, and `reject` accepts `{"note": "...",
"version": 7}`. A present version that differs → 409, with the same message as save. Without it,
the behaviour is unchanged (backward compatible for `.http` files). The admin app always sends it,
after first flushing the autosave so that the version matches its own last save.

Exact shapes:

```
POST /api/articles/{id}/approve      body (optional): {"version": 7}
POST /api/articles/{id}/reject       body: {"note": "Too short", "version": 7}   (version optional)
→ 200 ArticleDto | 409 {"message": "article was changed in the meantime (version 8, sent 7); reload it"}
```

### D5 — Response shapes for authorship

```
ArticleDto        += "lastEditor": {"sub": "...", "username": "chief", "displayName": "Lena"}
ArticleSummaryDto += "createdAt": "2026-09-30T10:00:00Z"
RevisionSummaryDto = {"number": 2, "headline": "...", "author": {...AuthorDto}, "createdAt": ...,
                      "updatedAt": ..., "publishedAt": null, "live": false}
RevisionDto       += "author": {...AuthorDto}
```

`AuthorDto` is the existing shape of `ArticleDto.author`.

### D6 — Diff in the admin app (commonMain, no dependency)
The server delivers two `RevisionDto`s (`n`, `n-1`), and the client computes the diff.

- A new `ui/diff/WordDiff.kt` implements an LCS over word tokens (whitespace kept as separate
  tokens), with a Myers-style O(ND) algorithm. Texts are at most a few thousand words, so this is
  cheap.
- Body blocks are aligned by an LCS over block keys `(type, plain text)`. Unmatched neighbouring
  blocks of the same type are paired as "changed" and word-diffed. The remaining blocks are
  "added" or "removed". Image blocks compare `mediaId` ("image replaced") and word-diff the caption.
- `RevisionDiffScreen` renders removed words with strikethrough plus a subtle error-container
  background, and added words with underline plus a primary-container background. This satisfies
  "not colour alone". It reuses the read-only article rendering for block chrome.

*Alternative:* a server-side diff endpoint. Rejected: it would add contract surface with no gain,
since the data is already available.

### D7 — Sectionless reporter storage and effects

```sql
-- V15__sectionless_reporter.sql
CREATE TABLE sectionless_reporter (
    account_id  TEXT        PRIMARY KEY,   -- Keycloak user id (token sub)
    assigned_by TEXT        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL
);
```

- **Storage and actions.** A `SectionlessReporterStore` provides `isMarked(sub)`, `marked()`,
  `set(sub, by)` and `clear(sub)`. `Newsroom` gains `sectionlessReporter` (loaded with the section
  roles in `NewsroomService.of`). It adds `mayUseMedia() = isWriter() || sectionlessReporter`,
  `NewspaperAction.USE_MEDIA` (after `WRITE_ARTICLES`) and `CONFIGURE_CORRECTIONS` (last, publisher
  only).
- **Media.** `MediaService.requireWriter` → `requireMediaUser` (`mayUseMedia`), also in
  `MediaResource`. The uploader rule in `mayEdit` is unchanged.
- **Accounts.** `AccountDto`, `CreateAccountRequest` and `EditRolesRequest` gain
  `sectionlessReporter` (`Boolean`, nullable in requests); `AccountListDto` gains
  `mayAssignSectionlessReporter` (= `isAdministrator()`). `AccountRequestValidator` treats a `true`
  marker as a role for the "at least one role" check. `AccountCreation` and `AccountRoleEdit`
  check `mayAssign` only when the value changes, and write the marker in the same DB transaction as
  the section roles. That transaction already runs after the Keycloak write, with the Keycloak
  revert on failure.
- **Automatic marker.** A helper `LastSectionRule.apply(accountIds, actor)` runs after section
  roles are removed:
  - `SectionMembers.remove` and `SectionService.delete`, for the affected accounts;
  - `AccountRoleEdit`, when the request has no marker field.

  For each account without remaining section roles, it reads the newspaper roles via
  `RoleHolders`. Section delete collects the member ids before the cascade. If the account has
  neither `PUBLISHER` nor `EDITOR_IN_CHIEF`, the helper sets the marker and logs at INFO.
  Keycloak being unavailable fails the request, like other role operations.
- **Policies.** `AccountPolicy` is unchanged. Section editors keep their rule (≥1 section role, all
  `REPORTER` in their sections), so a pure sectionless reporter is managed by editors-in-chief and
  publishers only.
- **Label.** "Redakteur (ohne Ressort)" is the German spelling, matching "Ressortleiter".

*Alternative:* Keycloak group `reporter`. Rejected: the user decided on DB storage, and Keycloak
would make the change wait for a token refresh.

### D8 — Section counts
`SectionService.list` runs, only when the requester `isWriter()`, one grouped query:

```sql
select a.sectionId, a.status, a.issueId, i.number, count(a)
from ArticleEntity a left join IssueEntity i on i.id = a.issueId
group by a.sectionId, a.status, a.issueId, i.number
```

The result is folded in Java into `ArticleCountsDto(live, total, issues)`, and
`SectionDto.articleCounts` is `null` for non-writers.

```json
"articleCounts": {"live": 2, "total": 4,
                  "issues": [{"issueId": 12, "number": 2, "count": 2}, {"issueId": 11, "number": 1, "count": 1}]}
```

### D9 — Article sorting
`GET /api/articles?sort=changed|newest|section` is validated in `ArticleResource` (400 naming
`sort`). `ArticleService.list` maps it to:
- `changed`: `a.updatedAt desc, a.id desc`;
- `newest`: `a.createdAt desc, a.id desc`;
- `section`: `s.position asc, s.id asc, a.updatedAt desc, a.id desc`.

The section is already joined. In the admin app, `ArticleListScreen` keeps a `sort` state in the
list model (per session) with a small segmented chooser. With `section`, it inserts sticky headers
when `section.id` changes.

### D10 — Writable boolean setting
`EffectiveSettings` gains `ARTICLE_CORRECTIONS = "article.corrections"`, with the default
`NewspaperConfig.article().corrections()` (`presserl.article.corrections`, env
`PRESSERL_ARTICLE_CORRECTIONS`). Startup validation accepts `true` and `false` only, and fails with
the variable name and the allowed values otherwise.

`WritableSettings` gets a typed value parser per key: the enum keys keep their string parsing, and
`article.corrections` accepts a JSON boolean or `null`. It stores a JSON Boolean, which
`booleanOverride` already reads. The `mayWrite` predicate is `Newsroom::mayConfigureCorrections`
(publisher).

The admin client changes `updateNewspaperSettings` from `Map<String, String?>` to
`Map<String, JsonElement>` (`JsonPrimitive`/`JsonNull`), and existing callers are adapted.

### D11 — Image-block caption spell check
`ImageBlockFields` receives `checker`, uses `SpellCheckedTextField` with `key = "caption:${block.id}"`
(distinct from the subhead's `"block:<id>"` and the lead caption's `"caption"`), and sends
`onReplace = { SetImageCaption(block.id, it, ownStep = true) }`. `SetImageCaption` gains
`ownStep`, reducing with `(CAPTION_KEY to blockId).unless(ownStep)`. The help level needs no client
code, because the server trims messages and suggestions.

## Risks / Trade-offs

- [The chain now depends on revision authors; a bug could let content skip a level] → Table-driven
  `ApprovalChain` unit tests for multi-contributor cases (trust in the author only, lock, corrector
  equals approver), plus `@QuarkusTest` flows for correct → approve → publish.
- [Autosave while correcting a pending article bumps the version often; the approver's own
  Approve could race their autosave] → The admin app flushes the autosave and uses the returned
  version before approving or rejecting (spec scenario "Correct and approve").
- [The author's editor open on a published article while a corrector saves] → The existing 409 plus
  reload banner covers it. No merge.
- [`levelOf` from `Staffing` needs Keycloak role holders on more requests (every request by a
  possible corrector)] → The role holders are loaded once per request. When Keycloak is
  unavailable, these requests answer `503`, as chain requests already do (`RoleHolders`). The
  primer names the widened set of affected requests.
- [Sections of readers leak counts] → Counts are `null` for non-writers.
- [An automatic marker surprises an admin who wanted the account gone] → It is logged, and it is
  visible in the account list as "Redakteur (ohne Ressort)". An editor-in-chief can clear it with an
  explicit role edit.
- [Stale markers after deleting a Keycloak user outside Presserl] → They are harmless (no login).
  Not cleaned up in this change.

## Migration Plan

1. `V14__revision_author.sql`: add the columns, backfill from `article`, set `NOT NULL`. This is
   forward-only, and existing rows keep their article author.
2. `V15__sectionless_reporter.sql`: create the empty table.
3. Deploy the backend and admin together (same image). There is no realm change. The
   `.env.example` entry for `PRESSERL_ARTICLE_CORRECTIONS` is optional (default `true`).
4. Rollback: the image can be rolled back. The added columns and table are ignored by the old code.
   The migrations are never edited, and a correction migration is added if needed.
