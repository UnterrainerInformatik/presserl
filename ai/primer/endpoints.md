# REST endpoints primer

Canonical description of the backend REST API as consumed by the frontend. Keep in sync with
every change to a JAX-RS resource or a DTO that crosses the wire.

Per endpoint: method + path, auth/role, query/path params, request body, response body
(with example), error codes, side effects.

Conventions:
- Base path `/api`, JSON (`application/json`). Same origin as reader and admin app in production.
- Auth: `Authorization: Bearer <access token>` from the realm's `presserl-admin` client. The token
  must carry the audience `presserl-backend` and be issued by `PRESSERL_OIDC_ISSUER`. Endpoints not
  marked public answer `401` with an empty body when the token is missing, expired, from a foreign
  issuer or lacks the audience.
- Newspaper roles come from the token's `groups` claim (group names without path): `publisher` →
  `PUBLISHER`, `editor-in-chief` → `EDITOR_IN_CHIEF`, `reader` → `READER`; other groups are ignored.
- Section roles (`SECTION_EDITOR`, `REPORTER`) are stored in the Presserl database per account and
  section, keyed by the token's `sub` (= Keycloak user id); they are not in the token.
- Every response carries a `Content-Security-Policy` header (reader policy).

---

## `GET /api/newspaper`

Effective newspaper settings (code default → environment → database override).

- **Auth:** public
- **Params / body:** none
- **Response `200`:**
  ```json
  {
    "name": "My Newspaper",
    "subtitle": "",
    "visibility": "public",
    "settings": {
      "retract.author-can-retract": true,
      "section.default": "General",
      "editor.level": "standard",
      "reader.text-size": "m",
      "media.max-size": "10M"
    }
  }
  ```
  - `subtitle`: empty string when unset.
  - `visibility`: `public` | `private`.
  - `settings` keys are the config keys without the `presserl.` prefix. `editor.level`:
    `starter` | `standard` | `profi`; `reader.text-size`: `s` | `m` | `l` | `xl`.
- **Errors:** none specific.
- **Side effects:** none.

## `GET /api/client-config`

OIDC settings the admin app needs to start the login.

- **Auth:** public
- **Params / body:** none
- **Response `200`:**
  ```json
  {
    "oidc": {
      "issuer": "https://auth.unterrainer.info/realms/presserl",
      "clientId": "presserl-admin",
      "scopes": ["openid", "profile"]
    }
  }
  ```
  `issuer` is `PRESSERL_OIDC_ISSUER`, `clientId` is `PRESSERL_OIDC_ADMIN_CLIENT_ID`.
- **Errors:** none specific.
- **Side effects:** none.

## `GET /api/me`

The logged-in user as seen by the backend.

- **Auth:** bearer token (any authenticated user)
- **Params / body:** none
- **Response `200`:**
  ```json
  { "username": "nogroups", "displayName": "No Groups", "roles": [],
    "sectionRoles": [ { "sectionId": 1, "sectionName": "Sport", "role": "SECTION_EDITOR" } ],
    "allowedActions": ["WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"] }
  ```
  - `username`: `preferred_username` claim, falling back to `sub`.
  - `displayName`: `name` claim, falling back to `username`.
  - `roles`: newspaper roles from the `groups` claim in the order `PUBLISHER`,
    `EDITOR_IN_CHIEF`, `READER`; empty array for a user without newspaper groups.
  - `sectionRoles`: the user's section roles, ordered by section position; `[]` for none. Read
    per call, so a changed section role shows at the next call; `roles` follow the token.
  - `allowedActions`: the newspaper-wide actions the user may perform now, always present (`[]`
    for none), in this order. Computed from the same rules that guard the endpoints; clients
    render from it instead of re-deriving it from `roles`/`sectionRoles`, and ignore values they
    do not know (M3 adds approval-chain actions). Per-section detail stays on
    `GET /api/sections` (`canWrite`, `assignableRoles`).

    | Action | Listed when the user holds | Stands for |
    |---|---|---|
    | `WRITE_ARTICLES` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or a section role in any section | the article endpoints |
    | `MANAGE_SECTIONS` | `PUBLISHER` or `EDITOR_IN_CHIEF` | creating, changing, reordering and deleting sections |
    | `ASSIGN_SECTION_ROLES` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or `SECTION_EDITOR` in any section | section members of at least one section |
    | `ADMINISTER_ACCOUNTS` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or `SECTION_EDITOR` in any section | listing and creating accounts |
- **Errors:** `401` (empty body) without a valid token.
- **Side effects:** none.

---

# Articles

All article endpoints require a bearer token of a **writer**: a user holding `PUBLISHER` or
`EDITOR_IN_CHIEF`, or a section role (`SECTION_EDITOR` or `REPORTER`) in at least one section.
Everyone else (e.g. only `READER`) gets `403` with an empty body; missing/invalid token → `401`.
Every other refusal carries the error body below. Publishers and editors-in-chief write in every
section; section editors and reporters only in the sections where they hold a section role.

**Visibility.** Publishers and editors-in-chief see every article. Every other writer sees the
articles they authored and, in each section where they are `SECTION_EDITOR`, all articles of that
section. Lists contain only visible articles; every `/api/articles/{id}…` endpoint (get,
revisions, reviews, save, delete, publish, offline, unlock, submit, approve, reject, withdraw) answers an
invisible article with `404`, exactly like an unknown id.

**Sections.** Every article belongs to one section (`section` in `ArticleDto` and
`ArticleSummaryDto`). The section belongs to the article, not to a revision: moving an article
(`sectionId` on `PUT`) creates no revision and applies to the published article too. **Default
section:** the section named by the setting `section.default` (ignoring case, code default
`General`). At startup the server creates it when no section exists at all; the database rejects
articles without a section. `POST` without `sectionId` files the article under the
default section when it exists and the user may write there, otherwise under the first section by
position the user may write in; when no section exists at all, the default section is created.

**Error body** (`400`, `403`, `404`, `409` raised by the article rules):
```json
{ "errors": [ { "field": "body.blocks[0].type", "message": "unknown block type 'html'" } ] }
```
`field` is a path (`headline`, `version`, `status`, `sectionId`, `note`, `body.blocks[2].content[0].text`,
…) for validation errors and `null` for `403`/`404`/`409`, except a `403` for a section the user may
not write in, which names `sectionId`. A `400` lists **every** violation found.

**Revisions.** Revisions are numbered from `1`. `PUT` overwrites the latest revision while it has
never been published (working revision — autosave does not create revisions); once the latest
revision has been published, the next `PUT` creates revision `latest + 1` — unless the sent content
(`kicker`, `headline`, `subheadline`, `lead`, `body`) equals the latest revision's content: then no
revision is created or changed (`updatedAt` and `version` still change). Publishing makes the
latest revision the **live revision** (`liveRevision`); later saves do not touch it until the
next publish. `hasUnpublishedChanges` = `liveRevision != null && revision != liveRevision`.

**Versions.** `version` is the optimistic-lock counter. Every successful save, publish,
take-offline, unlock, submit, approve, reject and withdraw increases it. The chain actions carry no
version: of two concurrent decisions on the same article the second gets `409`. `PUT` must send the version last received; a different stored version
→ `409` and nothing changes (reload, then save again).

**Approval chain.** Levels, bottom to top: `SECTION_EDITOR` (of the article's section),
`EDITOR_IN_CHIEF`, `PUBLISHER`. A user's **level** for an article is the highest of these they
hold (`PUBLISHER` role, `EDITOR_IN_CHIEF` role, `SECTION_EDITOR` of that section); none = reporter.
The author's **chain** = the levels above the author's level that are **staffed**, i.e. held by at
least one account other than the author (locked accounts count), and do **not trust** the author;
unstaffed and trusting levels are skipped. A level trusts the author when a trust entry exists for
the author at that level (see Accounts, trust): `SECTION_EDITOR` for the article's section,
`EDITOR_IN_CHIEF` and `PUBLISHER` newspaper-wide — whoever set it and whether they still hold the
role. While the article is **locked** by the emergency brake (`locked: true`), `PUBLISHER` belongs
to the chain of every author below `PUBLISHER`, staffed or trusting or not — only a publisher brings
it back online.
An author whose chain is empty publishes directly (`PUBLISH`); everyone else submits (`SUBMIT`) and
the article waits for the lowest level of the chain (`pendingLevel`). Any user other than the
author whose level is at least the pending level may approve or reject; an approval settles every
level up to the approver's level, the article then waits for the next staffed, non-trusting level
above it or, when none remains, goes live. While a submission is pending, content and section are frozen
(`EDIT` not offered, `PUT` → `409`). The chain is computed per request from the current roles and
trust, so role and trust changes act at once — but a pending submission is **not moved**: its
`pendingLevel` changes only on submit and approve, so an article already waiting for a level that
starts to trust its author keeps waiting (approve, reject or withdraw as before). Requests that need it (an author without `PUBLISHER` among the returned
articles, every approve) read the role holders from Keycloak; when Keycloak is unavailable they
answer `503` with the error body.

**`allowedActions`.** Every article representation lists what the requesting user may do now, in
the order `EDIT`, `SUBMIT`, `PUBLISH`, `WITHDRAW`, `APPROVE`, `REJECT`, `TAKE_OFFLINE`, `UNLOCK`, `DELETE`.
Render buttons only from this list: an action is accepted exactly when it is listed (apart from
content validation, a missing headline or note, and `409` on concurrency). `SUBMIT` and `PUBLISH`
are never listed together. Rules ("pending" = `pendingLevel != null`):

| Action | Allowed when |
|---|---|
| `EDIT` | user is the author and may write in the article's section; nothing pending |
| `SUBMIT` | user is the author, may write in the article's section, their chain is not empty; nothing pending; status ≠ `PUBLISHED` or unpublished changes exist |
| `PUBLISH` | as `SUBMIT`, but the author's chain is empty (the author holds `PUBLISHER`, or every level above is unstaffed or trusts the author) |
| `WITHDRAW` | user is the author; pending |
| `APPROVE`, `REJECT` | user is not the author and their level ≥ `pendingLevel`; pending |
| `TAKE_OFFLINE` | status = `PUBLISHED` and user is the author, an `EDITOR_IN_CHIEF`, a `PUBLISHER` or `SECTION_EDITOR` of the article's section |
| `UNLOCK` | user holds `PUBLISHER`; the article is locked |
| `DELETE` | user is the author, may write in the article's section, and the article was never published (`liveRevision == null`); also while pending |

Endpoints check writer (`403`, empty body) → request body (`400`, reject only) → visibility (`404`)
→ role/ownership/section access (`403`) → state (`409`) → headline (`400`, publish/submit) → the
target `sectionId` (`400`/`403`). `APPROVE`/`REJECT` on an article with nothing pending: `409` for a
user who holds any level for the article's section, `403` for others. An author who lost their
section role keeps seeing the article, read-only, may still take it offline and withdraw a pending
submission.

**Status:** `DRAFT` | `SUBMITTED` | `PUBLISHED` | `OFFLINE`. `SUBMITTED` = never published and
waiting for approval. A `PUBLISHED` or `OFFLINE` article keeps its status while changes (or its way
back online) wait; the reader keeps showing its live revision. `pendingLevel`
(`SECTION_EDITOR` | `EDITOR_IN_CHIEF` | `PUBLISHER` | `null`) says what it waits for.

**Emergency brake.** An article a `PUBLISHER` takes offline is locked (`locked: true`; only
`OFFLINE` articles are locked). While locked, its chain ends with `PUBLISHER` (see above). The lock
ends when the article goes online (publish by a publisher-author, or the final approval) or when a
publisher unlocks it (`POST /api/articles/{id}/unlock`). Taking offline by anyone else does not lock.

## Content fields and body format v1

`ArticleContent` (request body of create and save):
```json
{ "sectionId": 3, "kicker": "", "headline": "", "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] } }
```
- All fields optional; missing or `null` text fields → `""`, missing `body` →
  `{"version": 1, "blocks": []}`. Every save is a **full replacement** (no PATCH) of the content.
- `sectionId` (positive integer): on create the article's section (missing → default-section
  rule), on save the section to move to (missing → the section stays). Not a positive integer →
  `400`, unknown section → `400`, a section the user may not write in → `403`, each naming
  `sectionId`; nothing is stored then.
- Text fields are plain text, stored trimmed, no control characters (no line breaks). Max length
  (code points): `kicker`, `headline`, `subheadline` 200; `lead` 1000.
- Unknown fields → `400` naming them.

`body`, format version 1:
```json
{
  "version": 1,
  "blocks": [
    { "type": "paragraph", "content": [ { "text": "It started " }, { "text": "in May", "bold": true } ] },
    { "type": "subhead", "text": "Watering" },
    { "type": "quote", "content": [ { "text": "Every day!" } ] },
    { "type": "list", "items": [ [ { "text": "Water" } ], [ { "text": "Sun" } ] ] }
  ]
}
```
- Block types: `paragraph` (`content`: runs), `subhead` (`text`), `quote` (`content`: runs),
  `list` (bullet list, `items`: array of run arrays, at least one item). `paragraph`/`quote`
  content and list items may be empty arrays.
- Run: `{ "text": "...", "bold": true|false }`; `text` required and non-empty, `bold` optional
  (default `false`). `\n` is allowed inside run text (line break); `subhead` text has no control
  characters at all.
- Rejected with `400`: unknown block type, unknown field anywhere, missing required field, wrong
  JSON type, `version` ≠ `1`, other control characters, more than 500 blocks, more than 200 000
  characters of text in total.
- Text is never interpreted as HTML: `<b>hi</b>` is stored and returned verbatim. Escaping is the
  renderer's job.
- The body is returned as sent (same JSON meaning; key order may differ).

## `ArticleDto`

Returned by get, create, save, publish, take-offline, unlock, submit, approve, reject and withdraw.
Content fields are those of the latest revision (`revision`).
```json
{
  "id": 42,
  "status": "PUBLISHED",
  "author": { "username": "papa", "displayName": "Papa" },
  "section": { "id": 4, "name": "Kultur", "slug": "kultur", "color": "blue" },
  "revision": 2,
  "liveRevision": 1,
  "hasUnpublishedChanges": true,
  "pendingLevel": null,
  "locked": false,
  "version": 5,
  "createdAt": "2026-09-26T10:00:00Z",
  "updatedAt": "2026-09-26T10:05:00Z",
  "publishedAt": "2026-09-26T10:01:00Z",
  "kicker": "Garden",
  "headline": "The pumpkin is huge",
  "subheadline": "",
  "lead": "Our pumpkin weighs 12 kilos.",
  "body": { "version": 1, "blocks": [ { "type": "subhead", "text": "Watering" } ] },
  "allowedActions": ["EDIT", "PUBLISH", "TAKE_OFFLINE"]
}
```
- `author`: username and display name as `GET /api/me` returned them when the article was created.
- `section`: the article's section with its current name and palette colour.
- `liveRevision`, `publishedAt` (first publication): `null` until the first publish.
- `pendingLevel`: the approval level the article waits for, `null` while no submission is pending.
- `locked`: `true` while the emergency-brake lock is set (see above), otherwise `false`.
- Timestamps: ISO-8601 UTC, millisecond precision.

## `GET /api/articles`

Summaries of the articles visible to the requesting user, newest change (`updatedAt`) first. No
pagination yet.

- **Auth:** writer
- **Query:** `status` (optional, `DRAFT` | `SUBMITTED` | `PUBLISHED` | `OFFLINE`), `mine`
  (optional, `true` → only articles the requesting user authored), `pending` (optional, `true` →
  only articles with a pending submission), `awaitingMe` (optional, `true` → only articles whose
  `allowedActions` for the requesting user contain `APPROVE`, i.e. the review queue: never the
  user's own articles, empty for reporters and a solo newspaper); filters combine
  (`mine=true&awaitingMe=true` is always empty)
- **Response `200`:** array of `ArticleSummaryDto`
  ```json
  [ { "id": 42, "status": "DRAFT", "author": { "username": "papa", "displayName": "Papa" },
      "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" },
      "headline": "Hello", "kicker": "", "revision": 1, "liveRevision": null,
      "hasUnpublishedChanges": false, "pendingLevel": null, "locked": false, "updatedAt": "2026-09-26T10:05:00Z", "publishedAt": null,
      "allowedActions": ["EDIT", "PUBLISH", "DELETE"] } ]
  ```
- **Errors:** `400` unknown `status` (field `status`).
- **Side effects:** none.

## `POST /api/articles`

Creates a `DRAFT` with revision `1` in `sectionId` or by the default-section rule; the requesting
user becomes the author.

- **Auth:** writer; may write in the target section
- **Body:** `ArticleContent` (`{}` is fine)
- **Response `201`:** `ArticleDto`, header `Location: …/api/articles/{id}`
- **Errors:** `400` content validation or unknown section (field `sectionId`), `403` section not
  writable (field `sectionId`).
- **Side effects:** article and revision `1` stored; the default section is created when no
  section exists at all.

## `GET /api/articles/{id}`

- **Auth:** writer
- **Response `200`:** `ArticleDto`
- **Errors:** `404` unknown id or not visible.

## `PUT /api/articles/{id}`

Saves the content (full replacement) following the working-revision rule and moves the article
to `sectionId` if given.

- **Auth:** writer; `EDIT` (the author, while they may write in the article's section); the target
  section must be writable too
- **Body:** `ArticleContent` plus `"version": 5` (required)
- **Response `200`:** `ArticleDto` (new `version`, `revision` possibly incremented)
- **Errors:** `400` validation (missing `version` names `version`; bad or unknown `sectionId`
  names `sectionId`), `403` not the author or no write access to the article's section, `403`
  target section not writable (field `sectionId`), `404`, `409` stale `version` or a submission is
  pending.
- **Side effects:** latest revision overwritten, or new revision if the latest was published,
  or none if the content is unchanged; section changed if `sectionId` is given; `updatedAt` and
  `version` change.

## `DELETE /api/articles/{id}`

- **Auth:** writer; `DELETE` (the author, while they may write in the article's section)
- **Response `204`**
- **Errors:** `403` not the author or no write access to the section, `404`, `409` the article was published at least once (take
  it offline instead).
- **Side effects:** article with all revisions and reviews removed (also while a submission is
  pending).

## `POST /api/articles/{id}/publish`

Makes the latest revision live without approval. No request body.

- **Auth:** writer; `PUBLISH` (the author, while they may write in the section and their chain is
  empty)
- **Response `200`:** `ArticleDto` with `status` `PUBLISHED`, `liveRevision` = `revision`
- **Errors:** `400` empty headline (field `headline`), `403` not the author, no write access, or a
  level applies (submit instead), `404`, `409` already `PUBLISHED` without unpublished changes, or
  a submission is pending.
- **Side effects:** latest revision marked published (if not yet), `liveRevision` set, status
  `PUBLISHED`, `publishedAt` set on first publication; `updatedAt` and `version` change. Works
  from `DRAFT` and `OFFLINE` (an `OFFLINE` article whose latest revision is already live goes
  back online without a new revision).

## `POST /api/articles/{id}/offline`

Takes a published article offline without approval. No request body. A pending submission stays
pending; approving it later puts the article back online with its latest revision. Taken offline by
a `PUBLISHER`, the article is locked (emergency brake).

- **Auth:** writer; `TAKE_OFFLINE` (author, any editor-in-chief, any publisher, section editors
  of the article's section)
- **Response `200`:** `ArticleDto` with `status` `OFFLINE` and `locked` `true` when the user holds
  `PUBLISHER`, otherwise `false`; `liveRevision` is kept
- **Errors:** `403`, `404`, `409` status is not `PUBLISHED`.
- **Side effects:** status `OFFLINE`, `locked` set for a publisher; `updatedAt` and `version` change.

## `POST /api/articles/{id}/unlock`

Lifts the emergency-brake lock; the ordinary chain applies again. No request body.

- **Auth:** writer; `UNLOCK` (any `PUBLISHER`)
- **Response `200`:** `ArticleDto` with `locked` `false`; status (`OFFLINE`), `liveRevision` and
  `pendingLevel` unchanged
- **Errors:** `403` not a publisher, `404`, `409` the article is not locked.
- **Side effects:** `locked` cleared; `updatedAt` and `version` change.

## `POST /api/articles/{id}/submit`

Starts a submission. No request body.

- **Auth:** writer; `SUBMIT` (the author, while they may write in the section and their chain is
  not empty)
- **Response `200`:** `ArticleDto` with `pendingLevel` = lowest level of the chain; `status`
  `SUBMITTED` for a never-published article, otherwise unchanged (`PUBLISHED`/`OFFLINE`, live
  revision kept)
- **Errors:** `400` empty headline (field `headline`), `403` not the author, no write access, or
  the chain is empty (publish instead), `404`, `409` already pending, or `PUBLISHED` without
  unpublished changes.
- **Side effects:** `pendingLevel` set, status possibly `SUBMITTED`; `updatedAt` and `version`
  change. The latest revision is the one under review; it cannot change while pending.

## `POST /api/articles/{id}/approve`

Approves the pending submission up to the approver's level. No request body.

- **Auth:** writer; `APPROVE` (not the author; level ≥ `pendingLevel`)
- **Response `200`:** `ArticleDto` waiting for the next staffed level above the approver's level
  that does not trust the author (`pendingLevel`; `PUBLISHER` is always next for a locked
  article), or, when none remains,
  `PUBLISHED` with `pendingLevel` `null`, `locked` `false` and the latest revision live
- **Errors:** `403` the author or level too low, `404`, `409` nothing pending or a concurrent
  decision.
- **Side effects:** review `APPROVED` recorded with the level the article waited for; when
  published: as for publish (latest revision marked published, `liveRevision`, status, first
  `publishedAt`). `updatedAt` and `version` change.

## `POST /api/articles/{id}/reject`

Ends the pending submission with a note.

- **Auth:** writer; `REJECT` (same users as approve)
- **Body:** `{ "note": "Please add who scored." }` — exactly this field; stored trimmed, 1–1000
  code points, `\n` allowed, no other control characters. Validated before anything else, so a
  malformed body is `400` for everyone.
- **Response `200`:** `ArticleDto` with `pendingLevel` `null`; `SUBMITTED` → `DRAFT`, a
  `PUBLISHED`/`OFFLINE` article keeps its status and live revision
- **Errors:** `400` note missing, not a string, blank, too long or with control characters (field
  `note`), unknown fields (named), `403`, `404`, `409` nothing pending.
- **Side effects:** review `REJECTED` recorded with the note; revisions unchanged; `updatedAt` and
  `version` change.

## `POST /api/articles/{id}/withdraw`

The author ends their pending submission. No request body.

- **Auth:** writer; `WITHDRAW` (the author, also after losing the section role)
- **Response `200`:** `ArticleDto` with `pendingLevel` `null`; `SUBMITTED` → `DRAFT`, otherwise the
  status is kept
- **Errors:** `403` not the author, `404`, `409` nothing pending.
- **Side effects:** no review recorded; `updatedAt` and `version` change.

## `GET /api/articles/{id}/reviews`

- **Auth:** writer; article visible
- **Response `200`:** array of `ReviewDto`, newest first
  ```json
  [ { "decision": "REJECTED", "level": "EDITOR_IN_CHIEF", "revision": 2,
      "reviewer": { "username": "chief", "displayName": "Chief" },
      "note": "Too short", "createdAt": "2026-09-27T10:05:00Z" },
    { "decision": "APPROVED", "level": "SECTION_EDITOR", "revision": 2,
      "reviewer": { "username": "nogroups", "displayName": "No Groups" },
      "note": null, "createdAt": "2026-09-27T10:01:00Z" } ]
  ```
  `level`: the level the article waited for; `revision`: the revision reviewed; `reviewer`: snapshot
  at review time; `note`: set for rejections only. Withdrawals are not listed.
- **Errors:** `404` unknown or invisible article.

## `GET /api/articles/{id}/revisions`

- **Auth:** writer; article visible
- **Response `200`:** array of `RevisionSummaryDto`, newest first
  ```json
  [ { "number": 2, "headline": "Second", "createdAt": "…", "updatedAt": "…",
      "publishedAt": "2026-09-26T10:07:00Z", "live": true },
    { "number": 1, "headline": "First", "createdAt": "…", "updatedAt": "…",
      "publishedAt": "2026-09-26T10:01:00Z", "live": false } ]
  ```
  `publishedAt`: when that revision became live, `null` for a working revision.
- **Errors:** `404` unknown or invisible article.

## `GET /api/articles/{id}/revisions/{number}`

- **Auth:** writer; article visible
- **Response `200`:** `RevisionDto` = `RevisionSummaryDto` fields plus `kicker`, `subheadline`,
  `lead`, `body`
- **Errors:** `404` unknown or invisible article, unknown revision number.

---

# Accounts

Accounts are the users of the operator's Keycloak realm; their newspaper-wide roles are the groups
`publisher`, `editor-in-chief`, `reader`. The backend manages them through the `presserl-backend`
service account. Their section roles live in the Presserl database (see Sections).

All account endpoints require a bearer token **and** the role `PUBLISHER` or `EDITOR_IN_CHIEF`, or
`SECTION_EDITOR` in at least one section. Other users (readers, reporters) get `403` with an empty
body; missing/invalid token → `401`. Every other refusal carries the error body of the articles
(`{"errors": [{"field": …, "message": …}]}`); a `400` lists every violation.

**Delegation** (newspaper-wide roles are assigned at or below the own level; the server decides,
`assignableRoles` reports it):

| Requesting user holds | May assign |
|---|---|
| `PUBLISHER` | `PUBLISHER`, `EDITOR_IN_CHIEF`, `READER` |
| `EDITOR_IN_CHIEF` (not `PUBLISHER`) | `EDITOR_IN_CHIEF`, `READER` |
| `SECTION_EDITOR` only | — |

Section roles given on creation follow the section-role delegation (see Sections).

**Account actions** (`allowedActions`; the server decides, clients render buttons from it). Nobody
acts on their own account or on an account holding `PUBLISHER`:

| Action | Requesting user holds | Target account |
|---|---|---|
| `EDIT_ROLES`, `RESET_PASSWORD` | `PUBLISHER` | any other |
| `EDIT_ROLES`, `RESET_PASSWORD` | `EDITOR_IN_CHIEF` | not holding `EDITOR_IN_CHIEF` |
| `EDIT_ROLES`, `RESET_PASSWORD` | `SECTION_EDITOR` | newspaper roles ⊆ {`READER`}, at least one section role, every section role `REPORTER` in a section the requesting user is `SECTION_EDITOR` of |
| `LOCK` | `PUBLISHER` | enabled |
| `UNLOCK` | `PUBLISHER` | disabled |

**Trust** (`trusts`, `trustScopes`, `PUT /api/accounts/{id}/trust`; the server decides). A trust
entry = approval level × trusted account (× section for `SECTION_EDITOR`; `sectionId` `null` for the
newspaper-wide levels), at most one per account, level and section, recording who set it and when.
It makes that level skip the account's articles in the approval chain (see Articles). A user acts
only for their **own highest level**: `PUBLISHER` if they hold it, else `EDITOR_IN_CHIEF`, else
`SECTION_EDITOR` in each section they edit; never on their own account. Locked accounts are treated
like enabled ones.

| Requesting user's level | May set on accounts that | May clear |
|---|---|---|
| `PUBLISHER` | do not hold `PUBLISHER` and hold `EDITOR_IN_CHIEF` or any section role | any existing `PUBLISHER` entry |
| `EDITOR_IN_CHIEF` (not `PUBLISHER`) | hold neither `PUBLISHER` nor `EDITOR_IN_CHIEF` and any section role | any existing `EDITOR_IN_CHIEF` entry |
| `SECTION_EDITOR` in *S* (neither of the above) | hold neither `PUBLISHER` nor `EDITOR_IN_CHIEF` and are `REPORTER` in *S* | any existing `SECTION_EDITOR` entry of *S* |

Clearing does not depend on who set the entry or whether the account is still below (stale entries
after role changes stay and can be cleared). Deleting a section deletes its trust entries; role
changes delete none. An editor-in-chief who is also a section editor acts as editor-in-chief only.

**Keycloak unavailable:** when Keycloak cannot be reached or refuses the service account, every
account endpoint answers `503` with
`{"errors": [{"field": null, "message": "the account service is unavailable; try again later"}]}`.

## `AccountDto`

```json
{ "id": "5f0c…", "username": "chief", "firstName": "Chief", "lastName": "Editor",
  "roles": ["EDITOR_IN_CHIEF"], "sectionRoles": [ { "sectionId": 1, "role": "REPORTER" } ],
  "enabled": true,
  "trusts": [ { "level": "PUBLISHER", "sectionId": null } ],
  "trustScopes": [ { "level": "PUBLISHER", "sectionId": null } ],
  "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "LOCK"] }
```
`id` is the Keycloak user id. `firstName`/`lastName` are `""` when unset. `roles` are the newspaper
roles from the account's groups in the order `PUBLISHER`, `EDITOR_IN_CHIEF`, `READER` (`[]` for
none). `sectionRoles` are the account's section roles ordered by section position (`[]` for none).
`enabled` is `false` for a locked account. `trusts` are the account's trust entries (`level`
`SECTION_EDITOR` | `EDITOR_IN_CHIEF` | `PUBLISHER`, `sectionId` for `SECTION_EDITOR`, else `null`),
ordered `PUBLISHER`, `EDITOR_IN_CHIEF`, then `SECTION_EDITOR` by section position; `trustScopes`
are the entries the requesting user may set or clear on it (same shape and order, see the trust
table; render one switch each, on when the entry is in `trusts`). `allowedActions` are the actions the requesting user may
perform on the account now, in the order `EDIT_ROLES`, `RESET_PASSWORD`, `LOCK`, `UNLOCK` (see the
account actions table; `LOCK` only for enabled, `UNLOCK` only for disabled accounts). Clients ignore
values they do not know.

## `GET /api/accounts`

Every account of the realm except service accounts, sorted by `username`. At most 1000 accounts
(no paging; far above a family newspaper).

- **Auth:** `PUBLISHER`, `EDITOR_IN_CHIEF` or `SECTION_EDITOR` in any section
- **Response `200`:**
  ```json
  { "assignableRoles": ["PUBLISHER", "EDITOR_IN_CHIEF", "READER"],
    "accounts": [ { "id": "5f0c…", "username": "chief", "firstName": "Chief", "lastName": "Editor",
                    "roles": ["EDITOR_IN_CHIEF"], "sectionRoles": [], "enabled": true,
                    "trusts": [], "trustScopes": [ { "level": "PUBLISHER", "sectionId": null } ],
                    "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "LOCK"] } ] }
  ```
  `assignableRoles` are the newspaper roles only (`[]` for a section editor); the section roles a
  user may assign are reported per section by `GET /api/sections`.
- **Errors:** `503` Keycloak unavailable.
- **Side effects:** none.

## `GET /api/accounts/username-suggestion`

A free username derived from a first name: lower case; `ä`→`ae`, `ö`→`oe`, `ü`→`ue`, `ß`→`ss`;
other diacritics dropped; every run outside `a-z0-9` → one `-`; leading/trailing `-` removed; at
most 32 characters; `user` when nothing is left. If taken, `-2`, `-3`, … is appended (base
shortened to stay within 32). A base shorter than 3 characters is never returned as is but
numbered from `-1` (`li` → `li-1`, then `li-2`, …), so every suggestion is a valid username.

- **Auth:** `PUBLISHER`, `EDITOR_IN_CHIEF` or `SECTION_EDITOR` in any section
- **Query:** `firstName` (required, not blank)
- **Response `200`:** `{ "username": "juergen-maria" }`
- **Errors:** `400` missing/blank `firstName` (field `firstName`), `503`.
- **Side effects:** none.

## `POST /api/accounts`

Creates an enabled account with a generated default password, joins the groups of its roles and
stores its section roles.

- **Auth:** `PUBLISHER`, `EDITOR_IN_CHIEF` or `SECTION_EDITOR` in any section; every role and
  section role must be assignable (see the delegation tables)
- **Body:**
  ```json
  { "firstName": "Lena", "lastName": "", "username": "lena", "roles": ["EDITOR_IN_CHIEF"],
    "sectionRoles": [ { "sectionId": 1, "role": "REPORTER" } ] }
  ```
  `firstName` required, not blank, ≤ 100 characters; `lastName` optional, ≤ 100; names must not
  contain control characters and are stored trimmed. `username` must match
  `^[a-z0-9]+(-[a-z0-9]+)*$`, 3–32 characters, not start with `service-account-`. `roles`
  (required): any of `PUBLISHER`, `EDITOR_IN_CHIEF`, `READER`, may be `[]`; duplicates are
  collapsed. `sectionRoles` (optional): a list of `{sectionId, role}` with `role`
  `SECTION_EDITOR` | `REPORTER`, each section at most once, every section must exist. At least one
  role of either kind is required (field `roles`). Unknown fields are rejected.
- **Response `201`:** header `Location: /api/accounts/{id}`
  ```json
  { "account": { "id": "9a1e…", "username": "lena", "firstName": "Lena", "lastName": "",
                 "roles": ["EDITOR_IN_CHIEF"], "sectionRoles": [ { "sectionId": 1, "role": "REPORTER" } ],
                 "enabled": true, "trusts": [], "trustScopes": [ { "level": "PUBLISHER", "sectionId": null } ],
                 "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "LOCK"] },
    "password": "tiger-wolke-apfel-leiter" }
  ```
  `trustScopes` and `allowedActions` are computed for the creator. `password` is four words from a curated German word list (`a-z`, 3–8 letters each) joined by
  `-`, chosen with a secure random source. It is **not temporary** and appears **only in this
  response** — the server neither stores nor logs it. Show it on the account slip.
- **Errors:** `400` validation (every violation; also names Keycloak rejects, e.g. forbidden
  characters, at `firstName`/`lastName`/`username`; unknown section at `sectionRoles`), `403` a
  role that may not be assigned (field `roles`) or a section role outside the own scope (field
  `sectionRoles`) — nothing created, `409` username taken, ignoring case (field `username`), `503`.
- **Side effects:** Keycloak user created with the password credential and group memberships,
  section roles stored; all or nothing (a failure after the creation deletes the user again).
  Logged at INFO with username, creator, roles and section roles.

## `PUT /api/accounts/{id}/roles`

Replaces the account's newspaper roles (Keycloak groups) and section roles with the given ones,
all or nothing. Only the difference to the current roles is checked and written.

- **Auth:** account access rule above, and `EDIT_ROLES` permitted by the account actions table
  (locked accounts included); every newspaper role the request adds or removes must be assignable
  (delegation table), every section whose role is added, changed or removed must follow the
  section-role delegation for both the old and the new role (see Sections). Roles left unchanged
  need no permission — a section editor may promote their reporter who also holds `READER`.
- **Body:** the complete roles; both fields are required
  ```json
  { "roles": ["EDITOR_IN_CHIEF", "READER"],
    "sectionRoles": [ { "sectionId": 2, "role": "REPORTER" } ] }
  ```
  Same rules as `POST /api/accounts`: known roles (duplicates collapsed), `sectionRoles` a list of
  `{sectionId, role}` naming existing sections at most once each, at least one role of either kind
  (field `roles`), unknown fields rejected. `sectionRoles` is required so that a client forgetting
  it cannot remove every section role.
- **Response `200`:** the `AccountDto` with its new roles (trust entries unchanged),
  `trustScopes` and `allowedActions` computed for the
  requesting user (may be `[]` afterwards, e.g. after an editor-in-chief made someone
  editor-in-chief)
  ```json
  { "id": "5f0c…", "username": "reader", "firstName": "Reader", "lastName": "",
    "roles": ["EDITOR_IN_CHIEF", "READER"], "sectionRoles": [ { "sectionId": 2, "role": "REPORTER" } ],
    "enabled": true, "trusts": [], "trustScopes": [ { "level": "PUBLISHER", "sectionId": null } ],
    "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "LOCK"] }
  ```
  A request that changes nothing answers `200` without writing.
- **Errors:** `400` validation (every violation; unknown section at `sectionRoles`), `403` empty
  body (no account access), `403` error body when the rule refuses (field `null`, e.g.
  `you may not edit the roles of account 'chief'`), `403` an added/removed newspaper role that may
  not be assigned (field `roles`) or a changed section role outside the own scope (field
  `sectionRoles`), `404` error body for an unknown id or a service account, `503` Keycloak
  unavailable or a failure after the change was reverted. On every error the account keeps its
  previous roles of both kinds.
- **Side effects:** Keycloak group memberships changed first, then the section roles (rows of
  changed sections get new `assignedBy`/`assignedAt`, unchanged rows stay); when storing the
  section roles fails, the group change is reverted. **No session is ended:** section-role changes
  apply at once, newspaper-role changes with the person's next token refresh (access token
  lifespan, Keycloak default a few minutes) — a removed newspaper role lingers that long, as with a
  lock. Logged at INFO only when something changed:
  `Roles of account '<username>' changed by '<user>': roles [...] -> [...], section roles [...] -> [...]`.
  Concurrent edits: the last save wins.

## `POST /api/accounts/{id}/password-reset`

Sets a newly generated default password (same pass-phrase rule as `POST /api/accounts`, not
temporary) and ends every Keycloak session of the account. A locked account stays locked.

- **Auth:** account access rule above, and `RESET_PASSWORD` permitted by the account actions table
- **Body:** none
- **Response `200`:** the creation shape, `CreatedAccountDto`
  ```json
  { "account": { "id": "…", "username": "reader", "firstName": "Reader", "lastName": "",
                 "roles": ["READER"], "sectionRoles": [], "enabled": true, "trusts": [], "trustScopes": [],
                 "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "LOCK"] },
    "password": "tiger-wolke-apfel-leiter" }
  ```
  `password` appears **only in this response**; show it on the account slip.
- **Errors:** `403` empty body (no account access), `403` error body when the rule refuses (field
  `null`, e.g. `you may not reset the password of account 'chief'`), `404` error body for an unknown
  id or a service account, `503`. If ending the sessions fails after the password was set, the
  answer is `503`; a retry sets another new password.
- **Side effects:** Keycloak password credential replaced, all sessions of the account ended (its
  refresh tokens are rejected at once; access tokens already issued stay valid until they expire).
  Logged at INFO `Password of account '<username>' reset by '<user>'` — never the password.

## `POST /api/accounts/{id}/lock`, `POST /api/accounts/{id}/unlock`

Lock disables the account in Keycloak and ends every session of it; unlock enables it again.
Idempotent: locking a locked account (or unlocking an enabled one) answers `200` without change.

- **Auth:** `PUBLISHER`; never an account holding `PUBLISHER`, never the own account
- **Body:** none
- **Response `200`:** the `AccountDto` with its new state, e.g. after a lock
  `{ "id": "…", "username": "reader", …, "enabled": false, "trusts": [], "trustScopes": [], "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "UNLOCK"] }`
- **Errors:** `403` empty body (no account access), `403` error body when refused (field `null`,
  e.g. `you may not lock account 'reader'`), `404` error body for an unknown id or a service
  account, `503`.
- **Side effects:** Keycloak user `enabled` flag changed; on lock all sessions ended (refresh tokens
  rejected at once; **access tokens issued before the lock stay valid until they expire**, Keycloak
  default a few minutes). Logged at INFO `Account '<username>' locked|unlocked by '<user>'` when the
  state changed.

## `PUT /api/accounts/{id}/trust`

Sets or clears one trust entry of the account. Idempotent.

- **Auth:** account access rule above, and the entry must be among the requesting user's
  `trustScopes` for the account (see the trust table)
- **Body:**
  ```json
  { "level": "SECTION_EDITOR", "sectionId": 1, "trusted": true }
  ```
  `level` (required) `SECTION_EDITOR` | `EDITOR_IN_CHIEF` | `PUBLISHER`; `sectionId` an existing
  section for `SECTION_EDITOR` (required), `null` or absent otherwise; `trusted` (required) boolean
  — `true` sets, `false` clears. Unknown fields are rejected.
- **Response `200`:** the `AccountDto` as listed, computed for the requesting user
  ```json
  { "id": "5f0c…", "username": "reader", "firstName": "Reader", "lastName": "",
    "roles": ["READER"], "sectionRoles": [ { "sectionId": 1, "role": "REPORTER" } ], "enabled": true,
    "trusts": [ { "level": "SECTION_EDITOR", "sectionId": 1 } ],
    "trustScopes": [ { "level": "SECTION_EDITOR", "sectionId": 1 } ],
    "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD"] }
  ```
  Setting an existing entry keeps its original setter and time; clearing a missing one changes
  nothing.
- **Errors:** `400` validation (every violation: `level`, `sectionId` — e.g. `is required for
  SECTION_EDITOR`, `must be null for PUBLISHER`, `unknown section 7` —, `trusted`, unknown fields
  at their name), `403` empty body (no account access), `403` error body when the entry is not in
  the requesting user's `trustScopes` (field `null`, e.g.
  `you may not change trust of account 'reader' at EDITOR_IN_CHIEF`), `404` error body for an
  unknown id or a service account, `503` Keycloak unavailable.
- **Side effects:** the trust row inserted (with the requesting user and time) or deleted. **No
  article changes:** pending submissions keep their `pendingLevel`; trust applies the next time a
  chain is computed (fetch/publish check, submit, approve). Logged at INFO only when something
  changed: `Trust of account '<username>' at <LEVEL>[ in section <id>] set|cleared by '<user>'`.

---

# Sections

Sections structure the newspaper; each has a name, a colour from the palette and a stable `slug`
(later reader URLs). Section roles are held per account and section, at most one per section:
`SECTION_EDITOR` or `REPORTER` (cumulative: a section editor may do everything a reporter may).

All section endpoints require a bearer token (missing/invalid → `401`). Access refusals answer
`403` with an empty body, unknown ids `404` with an empty body; every other refusal carries the
error body (`{"errors": [{"field": …, "message": …}]}`), a `400` lists every violation.

**Access:**

| Endpoint | Who |
|---|---|
| `GET /api/sections` | every authenticated user |
| `POST`, `PUT`/`DELETE /api/sections/{id}`, `PUT /api/sections/order` | `PUBLISHER`, `EDITOR_IN_CHIEF` |
| `GET /api/sections/{id}/members`, `PUT`/`DELETE …/members/{accountId}` | whoever may assign section roles in that section |

**Delegation of section roles** (the server decides, `assignableRoles` reports it):

| Requesting user | In section S may assign and remove |
|---|---|
| `PUBLISHER` or `EDITOR_IN_CHIEF` | `SECTION_EDITOR`, `REPORTER` |
| `SECTION_EDITOR` in S | `SECTION_EDITOR`, `REPORTER` |
| anyone else (incl. `REPORTER` in S) | — |

Replacing a role needs both the old and the new role to be assignable; removing needs the current
one.

**Palette** (palette order): `red`, `orange`, `yellow`, `green`, `teal`, `blue`, `purple`, `pink`.
Colours are keys, not colour values; the reader theme maps them to `--presserl-section-<key>`.

## `SectionDto`

```json
{ "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0,
  "assignableRoles": ["SECTION_EDITOR", "REPORTER"], "canWrite": true }
```
`assignableRoles`: the section roles the requesting user may assign in this section, in the order
`SECTION_EDITOR`, `REPORTER` (`[]` for none). `canWrite`: whether the requesting user may write
articles in this section (`PUBLISHER`/`EDITOR_IN_CHIEF` everywhere, section-role holders in their
sections).

## `GET /api/sections`

All sections ordered by `position` (ties by `id`).

- **Auth:** any authenticated user
- **Response `200`:**
  ```json
  { "canManage": true,
    "sections": [ { "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0,
                    "assignableRoles": ["SECTION_EDITOR", "REPORTER"], "canWrite": true } ] }
  ```
  `canManage`: whether the user may create, change, reorder and delete sections (`PUBLISHER`,
  `EDITOR_IN_CHIEF`).
- **Errors:** none specific.
- **Side effects:** none.

## `POST /api/sections`

Creates a section at the last position.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Body:** `{ "name": "Sport & Spiel", "color": "green" }` — `name` required, trimmed, 1–40
  characters, no control characters, unique ignoring case; `color` optional, a palette key.
  Without `color` the server picks the palette colour at index (number of sections mod 8).
  Unknown fields are rejected.
- **Response `201`:** header `Location: /api/sections/{id}`, body a `SectionDto`. The `slug` is
  derived from the name like a username suggestion (umlauts spelled out, runs outside `a-z0-9` →
  `-`, at most 40 characters, `section` when empty; `-2`, `-3`, … when taken) and never changes.
  `"Sport & Spiel"` → `sport-spiel`.
- **Errors:** `400` validation, `403`, `409` name taken (field `name`).
- **Side effects:** section stored.

## `PUT /api/sections/{id}`

Replaces name and colour; `slug` and `position` stay.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Body:** `{ "name": "Sportnews", "color": "blue" }` — both required, rules as for creation.
- **Response `200`:** a `SectionDto`.
- **Errors:** `400`, `403`, `404` unknown section, `409` name taken by another section (field
  `name`).
- **Side effects:** section updated.

## `PUT /api/sections/order`

Sets the positions `0, 1, 2, …` in the given order.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Body:** `{ "ids": [2, 1] }` — every existing section id exactly once.
- **Response `200`:** as `GET /api/sections`.
- **Errors:** `400` field `ids` when an id is missing, unknown or repeated (nothing changed),
  `403`.
- **Side effects:** all positions rewritten in one transaction.

## `DELETE /api/sections/{id}`

Deletes an empty section together with every section role and trust entry held in it.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Response `204`**, empty body.
- **Errors:** `403`, `404` unknown section, `409` while at least one article (any status) belongs
  to the section — field `null`, e.g. `"section still contains 3 article(s); move them to another
  section first"`; nothing is changed. Move the articles first (`PUT /api/articles/{id}` with
  `sectionId`).
- **Side effects:** section, its section roles and its `SECTION_EDITOR` trust entries deleted; the
  remaining sections' positions set
  to `0, 1, 2, …` in their previous order; logged at INFO with the section and the acting user.
  Deleting the only section is allowed; the next `POST /api/articles` (or restart) recreates the
  default section.

## `GET /api/sections/{id}/members`

The accounts holding a role in the section, section editors first, then by username. Members whose
Keycloak user no longer exists are left out.

- **Auth:** users who may assign section roles in this section
- **Response `200`:**
  ```json
  { "assignableRoles": ["SECTION_EDITOR", "REPORTER"],
    "members": [ { "accountId": "5f0c…", "username": "nogroups", "firstName": "No",
                   "lastName": "Groups", "role": "SECTION_EDITOR" } ] }
  ```
  `lastName` is `""` when unset.
- **Errors:** `403`, `404` unknown section, `503` Keycloak unavailable.
- **Side effects:** none.

## `PUT /api/sections/{id}/members/{accountId}`

Gives the account the role in the section, replacing its current one.

- **Auth:** users who may assign section roles in this section; old and new role must be
  assignable
- **Body:** `{ "role": "REPORTER" }` — `SECTION_EDITOR` | `REPORTER`.
- **Response `200`:** one member (as in the members list).
- **Errors:** `400` unknown role (field `role`), `403` (empty body without access to the section's
  members; field `role` when the change itself is not allowed), `404` unknown section or account
  (service accounts count as unknown), `503`.
- **Side effects:** section role stored; logged at INFO with section, account, old and new role
  and the acting user.

## `DELETE /api/sections/{id}/members/{accountId}`

Removes the account's role in the section.

- **Auth:** users who may assign section roles in this section; the current role must be
  assignable
- **Response `204`**, empty body.
- **Errors:** `403`, `404` unknown section, account without a role in the section or unknown
  account, `503`.
- **Side effects:** section role deleted; logged at INFO with section, account, role and the
  acting user.
