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
    },
    "overrides": {}
  }
  ```
  - `subtitle`: empty string when unset.
  - `visibility`: `public` | `private`.
  - `settings` keys are the config keys without the `presserl.` prefix. `editor.level`:
    `starter` | `standard` | `profi`; `reader.text-size`: `s` | `m` | `l` | `xl`;
    `media.max-size`: largest accepted image upload (deployment-only, `PRESSERL_MEDIA_MAX_SIZE`).
  - `overrides`: the entries of `settings` whose value currently comes from a valid newspaper
    override (database), keyed like `settings`; `{}` when none. A key missing here uses the
    deployment value or the code default. Clients treat a missing `overrides` (older servers) as
    `{}`.
- **Errors:** none specific.
- **Side effects:** none.

## `PUT /api/newspaper/settings`

Sets or clears newspaper overrides. Writable in this version: `reader.text-size` only.

- **Auth:** bearer token; `PUBLISHER` or `EDITOR_IN_CHIEF` (`allowedActions` contains
  `CONFIGURE_NEWSPAPER`)
- **Body:** JSON object of setting name → value. A value stores the override; `null` removes it,
  so the deployment value (`PRESSERL_READER_TEXT_SIZE`) or the code default applies again. Keys not
  in the body stay unchanged; `{}` changes nothing. All or nothing: nothing is written when any key
  is refused.
  ```json
  { "reader.text-size": "l" }
  ```
  ```json
  { "reader.text-size": null }
  ```
- **Response `200`:** the same body as `GET /api/newspaper` after the change, e.g.
  ```json
  { "name": "My Newspaper", "subtitle": "", "visibility": "public",
    "settings": { "retract.author-can-retract": true, "section.default": "General",
                  "editor.level": "standard", "reader.text-size": "l", "media.max-size": "10M" },
    "overrides": { "reader.text-size": "l" } }
  ```
- **Errors:**
  - `400` `{"errors": [...]}` naming every offending key as `field`: a value outside the allowed
    set or not a string (`"must be one of s, m, l, xl"`), an unknown or non-writable key
    (`"is not a writable setting"`); `field` `null` when the body is not a JSON object.
  - `401` (empty body) without a valid token; `403` (empty body) without `PUBLISHER` or
    `EDITOR_IN_CHIEF`.
- **Side effects:** updates `newspaper.settings`; the reader uses the new `reader.text-size` for
  visitors without their own choice from the next page load.

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
  The bootstrapped publisher:
  ```json
  { "username": "publisher", "displayName": "publisher", "roles": ["PUBLISHER"], "sectionRoles": [],
    "allowedActions": ["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES",
                       "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"] }
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
    | `MANAGE_ISSUES` | `PUBLISHER` or `EDITOR_IN_CHIEF` | the issue endpoints (`/api/issues…`) |
    | `ADMINISTER_ACCOUNTS` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or `SECTION_EDITOR` in any section | listing and creating accounts |
    | `CONFIGURE_NEWSPAPER` | `PUBLISHER` or `EDITOR_IN_CHIEF` | changing the newspaper settings (`PUT /api/newspaper/settings`) |
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
{ "sectionId": 3, "kicker": "", "headline": "", "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] },
  "leadImage": { "mediaId": 17, "caption": "Our cat Minka" } }
```
- All fields optional; missing or `null` text fields → `""`, missing `body` →
  `{"version": 1, "blocks": []}`. Every save is a **full replacement** (no PATCH) of the content.
- `sectionId` (positive integer): on create the article's section (missing → default-section
  rule), on save the section to move to (missing → the section stays). Not a positive integer →
  `400`, unknown section → `400`, a section the user may not write in → `403`, each naming
  `sectionId`; nothing is stored then.
- Text fields are plain text, stored trimmed, no control characters (no line breaks). Max length
  (code points): `kicker`, `headline`, `subheadline` 200; `lead` 1000.
- `leadImage`: `null` or `{"mediaId", "caption"}`. **Missing or `null` means no lead image** — with
  full replacement a save without the field removes the image, so clients always send it.
  `mediaId` required, a positive integer naming an existing media (any media, whoever uploaded it);
  `caption` optional plain text (default `""`, stored trimmed, no control characters, at most 300
  code points). Errors (`400`, collected with all other field errors): not an object → field
  `leadImage`; `mediaId` missing, not a positive integer or no such media (`media 17 does not
  exist`, checked after the syntax, nothing is stored) → `leadImage.mediaId`; bad caption →
  `leadImage.caption`; other keys → `leadImage.<key>` ("unknown field"). The lead image is revision
  content: it is revisioned, counts for "unchanged" saves and goes live with the next publication.
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
  "issue": { "id": 2, "number": 2 },
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
  "leadImage": { "mediaId": 17, "caption": "Our cat Minka", "width": 4096, "height": 2731 },
  "allowedActions": ["EDIT", "PUBLISH", "TAKE_OFFLINE"]
}
```
- `author`: username and display name as `GET /api/me` returned them when the article was created.
- `section`: the article's section with its current name and palette colour.
- `issue`: the issue the article belongs to (`IssueRefDto`: `id`, `number`), `null` for none. Also
  part of `ArticleSummaryDto`.
- `liveRevision`, `publishedAt` (first publication): `null` until the first publish.
- `pendingLevel`: the approval level the article waits for, `null` while no submission is pending.
- `locked`: `true` while the emergency-brake lock is set (see above), otherwise `false`.
- `leadImage`: the latest revision's lead image or `null`; `width`/`height` are those of the stored
  image (`MediaDto`), for the editor's layout. Not part of `ArticleSummaryDto`.
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
      "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" }, "issue": null,
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
  back online without a new revision). On the **first** publication an article without issue is
  appended to the end of the issue with the highest number, live or not, in the same transaction
  (see Issues); later publications never change `issue`.

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
  `publishedAt`, appended to the newest issue on the first publication). `updatedAt` and
  `version` change.

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
  `lead`, `body`, `leadImage` (that revision's lead image, shaped as in `ArticleDto`, or `null`)
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

---

# Issues

Issues group articles: a `number` (assigned on creation as highest + 1, `1` when none exists,
never changed), an optional `publicationDate` (a calendar date, display only), a `published` switch
(live for readers or not) and an ordered article list whose first article is the **lead story**.
An article belongs to at most one issue; articles of any status may belong to one, readers only
see the `PUBLISHED` ones.

- **Newest issue collects new articles:** when an article is published for the first time
  (`POST /api/articles/{id}/publish` or the approval that publishes it) and belongs to no issue, it
  is appended to the issue with the highest number, whether or not that issue is live. No issue at
  all → it stays without issue. Later publications never move it. Blog mode = one issue that is
  live from the start; planned issues = the highest issue is not live yet and collects.
- **Initial issue:** the migration creates issue `1`, not live, without date; on an existing
  installation it holds every article published before, in order of first publication. Switch it
  live (blog mode) or date and publish it after upgrading.
- **Number reuse:** only unpublished issues can be deleted; deleting the highest one lets its number
  be issued again by the next creation.

All issue endpoints require `MANAGE_ISSUES` (`PUBLISHER`, `EDITOR_IN_CHIEF`): missing/invalid token
→ `401`, other users → `403` with an empty body. Unknown or malformed ids → `404` with the error
body (field `null`). Refusals carry `{"errors": [{"field": …, "message": …}]}`; a `400` lists every
violation; unknown fields are rejected. Articles in issue responses are not filtered by
visibility (issue managers see every article anyway).

## `IssueDto`

```json
{ "id": 4, "number": 4, "publicationDate": "2026-10-12", "published": false,
  "publishedAt": null, "articleCount": 2, "newest": true }
```
- `publicationDate`: ISO date or `null`.
- `publishedAt`: time of the latest switch to published (ISO-8601 UTC), `null` while not
  published.
- `articleCount`: all articles of the issue, any status.
- `newest`: the issue has the highest number, i.e. it collects newly published articles.

`IssueDetailDto` = `IssueDto` + `articles`: `ArticleSummaryDto`s (as in `GET /api/articles`, with
`allowedActions` for the requesting user) in issue order (position, ties by id); each carries
`"issue": { "id": …, "number": … }` of this issue.

## `GET /api/issues`

- **Auth:** `MANAGE_ISSUES`
- **Response `200`:** `{ "issues": [ IssueDto, … ] }`, highest number first.
- **Side effects:** none.

## `POST /api/issues`

Creates the next issue: not published, no articles, number = highest + 1.

- **Auth:** `MANAGE_ISSUES`
- **Body:** `{ "publicationDate": "2026-10-12" }`, `{ "publicationDate": null }` or `{}`.
- **Response `201`:** header `Location: /api/issues/{id}`, body `IssueDetailDto` (`newest: true`).
- **Errors:** `400` invalid date (field `publicationDate`, must be `yyyy-mm-dd` or `null`) or
  unknown field, `409` a concurrent creation took the number (nothing created; try again).
- **Side effects:** issue stored; from now on it collects newly published articles. Logged at INFO
  with number and acting user.

## `GET /api/issues/{id}`

- **Auth:** `MANAGE_ISSUES`
- **Response `200`:** `IssueDetailDto`
  ```json
  { "id": 4, "number": 4, "publicationDate": null, "published": false, "publishedAt": null,
    "articleCount": 2, "newest": true,
    "articles": [ { "id": 9, "status": "PUBLISHED", "headline": "…", "issue": { "id": 4, "number": 4 }, … },
                  { "id": 6, "status": "DRAFT", … } ] }
  ```
- **Errors:** `404`.

## `PUT /api/issues/{id}`

Sets or clears the publication date, for published issues too; the number never changes.

- **Auth:** `MANAGE_ISSUES`
- **Body:** `{ "publicationDate": "2026-10-12" }` or `{ "publicationDate": null }` — the field is
  required.
- **Response `200`:** `IssueDetailDto`.
- **Errors:** `400` missing/invalid `publicationDate` or unknown field, `404`.

## `POST /api/issues/{id}/publish`, `POST /api/issues/{id}/unpublish`

Switches the issue live for readers or hides it again. No request body, no approval.

- **Auth:** `MANAGE_ISSUES`
- **Response `200`:** `IssueDetailDto` with `published` `true`/`false`.
- **Errors:** `404`.
- **Side effects:** `publish` sets `publishedAt` to now (publishing a published issue keeps it,
  idempotent); `unpublish` clears it (idempotent). Article statuses are never touched; any number
  of issues may be live. Logged at INFO with number and acting user.

## `PUT /api/issues/{id}/articles`

Makes exactly the listed articles the issue's articles, in this order, in one transaction.

- **Auth:** `MANAGE_ISSUES`
- **Body:** `{ "articleIds": [9, 4, 6] }` (`[]` empties the issue).
- **Response `200`:** `IssueDetailDto`.
- **Errors:** `400` field `articleIds` for an unknown or repeated id (e.g. `"unknown article
  999999"`) or a non-array; nothing is changed. `404` unknown issue.
- **Side effects:** positions `0..n-1`; a listed article of another issue moves here; an article of
  this issue that is not listed belongs to no issue afterwards. The articles themselves are
  otherwise unchanged (`version`, `updatedAt` stay). Last write wins between concurrent editors.

## `DELETE /api/issues/{id}`

- **Auth:** `MANAGE_ISSUES`
- **Response `204`**, empty body.
- **Errors:** `404`, `409` (field `null`) while the issue is published — unpublish it first; nothing
  is changed.
- **Side effects:** the issue's articles belong to no issue afterwards and are otherwise unchanged;
  the issue is deleted; logged at INFO with number and acting user. A new issue gets the number
  after the highest remaining one.

---

# Media

Uploaded images. Every upload is re-encoded on the server from its pixels: the stored image is a
JPEG (quality 0.85) or, when the source has visible transparency, a PNG, in sRGB, rotated upright
by its EXIF orientation and scaled down to at most 4096 px on the longer side (never enlarged). No
metadata survives (EXIF incl. GPS/camera/date/orientation, XMP, IPTC, ICC, comments, thumbnails).
Every media also has three **renditions** derived from the stored image — `thumbnail` (longer side
≤ 480 px), `web` (≤ 1600 px) and `print` (≤ 3000 px) — keeping the aspect ratio, never enlarged
(a small image keeps its size), in the stored image's format (JPEG, or PNG with transparency) and
without metadata. They are produced during the upload; media uploaded before renditions existed get
theirs from a background backfill shortly after the backend starts.
Images live in an S3-compatible object store that browsers never reach. Readers only get
renditions of published lead images through the reader route `GET /media/{id}/{kind}` (below),
never the stored image.

All media endpoints require a bearer token (missing/invalid → `401`, empty body) and
`WRITE_ARTICLES` in `allowedActions` (publisher, editor-in-chief or any section role); everyone
else gets `403`. Refusals carry the error body (`{"errors": [{"field": …, "message": …}]}`) with
field `file` for problems of the uploaded file and `null` otherwise.

**Body limits:** the HTTP layer accepts request bodies up to 64M for `POST /api/media` only; every
other path keeps the former 10M limit (`413`, error body with field `null`, message
`request body larger than 10M`). A body above 64M is refused by Quarkus itself with `413` and an
**empty body**.

## `MediaDto`

```json
{ "id": 17, "contentType": "image/jpeg", "width": 4096, "height": 2731, "size": 1834211,
  "uploadedBy": { "username": "papa", "displayName": "Papa" },
  "uploadedAt": "2026-09-27T14:03:11.402Z",
  "renditions": {
    "thumbnail": { "width": 480,  "height": 320,  "size": 31877 },
    "web":       { "width": 1600, "height": 1067, "size": 298114 },
    "print":     { "width": 3000, "height": 2000, "size": 861022 } } }
```
`contentType`: `image/jpeg` | `image/png` — the type of the stored (re-encoded) image, not of the
upload; the renditions have the same type. `width`, `height`: pixels of the stored image. `size`:
bytes of the stored image. `uploadedBy`: username and display name at upload time (like article
bylines). `renditions`: per kind width, height and bytes; always all three once produced, `{}`
while the backfill has not reached an older media yet.

## `POST /api/media`

Uploads one image.

- **Auth:** `WRITE_ARTICLES`
- **Body:** `multipart/form-data` with exactly one file part named `file` (the part needs a
  `filename`). The declared part content type and the file name are ignored; the type is detected
  from the bytes: JPEG, PNG and WebP (lossy, lossless, with or without transparency, not animated).
- **Response `201`:** header `Location: /api/media/{id}`, body a `MediaDto`.
- **Errors:**

  | Status | When |
  |---|---|
  | `400` | no `file` part, more than one file, empty file (field `file`); image larger than 50 megapixels or a side longer than 20000 px (checked before decoding); damaged or undecodable image, e.g. truncated (field `file`) |
  | `403` | no `WRITE_ARTICLES` (checked before the body) |
  | `413` | file larger than the effective setting `media.max-size` (default `10M`, at most `60M`; field `file`); above 64M Quarkus answers `413` with an empty body |
  | `415` | not JPEG, PNG or still WebP — e.g. GIF, HEIC, SVG, PDF, animated WebP, HTML named `.jpg` (field `file`) |
  | `503` | object store unreachable, also while writing a rendition (field `null`); no media record and no object is left behind |

- **Side effects:** the re-encoded image and its three renditions are written to the object store
  under random keys, then the media record and its rendition records are stored in one transaction;
  on any failure the objects already written are deleted (best effort). Nothing is stored for a
  refused upload.

## `GET /api/media/{id}`

- **Auth:** `WRITE_ARTICLES`
- **Response `200`:** a `MediaDto`.
- **Errors:** `403`, `404` unknown id (field `null`).
- **Side effects:** none.

## `GET /api/media/{id}/content`

The stored image bytes.

- **Auth:** `WRITE_ARTICLES`
- **Response `200`:** body = the stored image; headers `Content-Type` (`image/jpeg` |
  `image/png`), `Content-Length`, `Content-Disposition: inline`,
  `X-Content-Type-Options: nosniff`, `Cache-Control: private, max-age=31536000, immutable` (a
  stored image never changes).
- **Errors:** `403`, `404` unknown id (JSON error body), `503` object store unreachable.
- **Side effects:** none.

## `GET /api/media/{id}/renditions/{kind}`

The bytes of one rendition; `kind` is `thumbnail`, `web` or `print` (lower case). Used by the
editor's preview.

- **Auth:** `WRITE_ARTICLES`
- **Response `200`:** body = the rendition; headers as `/content` (`Content-Type`,
  `Content-Length`, `Content-Disposition: inline`, `X-Content-Type-Options: nosniff`,
  `Cache-Control: private, max-age=31536000, immutable`).
- **Errors:** `403`, `404` unknown id, unknown kind or a rendition not produced yet (JSON error
  body, field `null`), `503` object store unreachable.
- **Side effects:** none.

---

# Reader routes (not part of `/api`)

Server-rendered reader pages and their helpers; no bearer token, noted here because the admin
app and forks rely on them.

## `POST /text-size`

The reader's text-size switch (a plain HTML form, no JavaScript).

- **Auth:** none (also for anonymous visitors of a private newspaper)
- **Body:** `application/x-www-form-urlencoded`: `size` (`s` | `m` | `l` | `xl`, case ignored),
  `next` (path of the page to return to)
- **Response:** `303 See Other` to `next` when it is a same-origin path (starts with a single
  `/`), to `/` otherwise; `Cache-Control: no-store`. For an allowed size it sets the cookie
  `presserl_text_size=<size>; Path=/; Max-Age=31536000; HttpOnly; SameSite=Lax` (+ `Secure` in
  production, `presserl.reader.cookie-secure`); an unknown or missing size sets no cookie.
- **Effect:** every reader page renders `<html data-text-size>` from a valid cookie, otherwise
  from the newspaper's effective `reader.text-size`.

## `GET /media/{id}/{kind}`

A rendition (`kind` `thumbnail` | `web` | `print`) of a lead image for readers; the reader pages
link `web` (article page, lead story, with `thumbnail` in `srcset`) and `thumbnail` (cards).

- **Auth:** none for a public newspaper. For a private one (effective `visibility` `private`) the
  reader session (`q_session_reader`) of an entitled reader (`READER`, `EDITOR_IN_CHIEF`,
  `PUBLISHER`) is needed; `/media/*` belongs to the reader OIDC tenant. No login redirect.
- **Response `200`:** only while media `{id}` is the lead image of the **live revision of at least
  one `PUBLISHED` article**; body = the rendition, headers `Content-Type`, `Content-Length`,
  `X-Content-Type-Options: nosniff`, `Cache-Control: public, max-age=3600` (public newspaper) or
  `private, max-age=3600` (private newspaper) — an image taken offline disappears from caches within
  an hour.
- **Errors:** `404` with an **empty body** for everything else: malformed id, unknown kind (also
  `original`/`content`: the stored image is never served), unknown media, media only used in drafts,
  in working revisions or in `OFFLINE` articles, and — for a private newspaper — anonymous visitors
  and visitors without a newspaper role. `503` (empty body) when the object store is unreachable.

## Issue pages and print views

Server-rendered HTML with the same access rules as the article page (`/articles/{id}`): in a
private newspaper an anonymous visitor gets `303` to `/login?next=<path>`, a logged-in visitor
without a newspaper role the `404` page; `Cache-Control: private, no-store` for a private newspaper
or a logged-in visitor. Paths belong to the reader OIDC tenant (`/issues`, `/issues/*`,
`/print/*`). Malformed or unknown ids and unpublished issues/articles get the reader's `404` page.

- `GET /issues` — archive of the live issues, highest number first, each with date and the live
  headline of its first published article (`data-view="issues"`).
- `GET /issues/{id}` — a live issue as a newspaper page: its `PUBLISHED` articles in issue order,
  the first as lead story, and a link to its print view (`data-view="issue"`). The admin app opens
  it in a new tab.
- `GET /print/article/{id}` — a `PUBLISHED` article on A4 with its `print` rendition
  (`data-view="print-article"`); linked from the article page.
- `GET /print/issue/{id}` — a live issue on A4: first page with masthead and lead story, then the
  other articles in columns (`data-view="print-issue"`). The admin app opens it in a new tab.
- The front page masthead names the newest live issue (link to `/issues/{id}`) and links `/issues`
  when more than one issue is live.
- Print views load `/reader/print.js` (static, same origin) for the screen-only "Print" button; the
  reader CSP stays `default-src 'self'`.

## `GET /theme/<path>`

Read-only files of the fork's theme directory (`presserl.theme.dir`, default
`/deployments/theme`).

- **Auth:** none
- **Response `200`:** the file with its content type; only `css`, `woff2`, `woff`, `ttf`, `otf`,
  `png`, `jpg`, `jpeg`, `gif`, `webp`, `svg`, `ico`. `Cache-Control: no-cache`,
  `Last-Modified`; `304` for a matching `If-Modified-Since`.
- **Errors:** `404` (empty body) for every other type, a missing file, a directory and any path
  resolving outside the theme directory (also via symlinks).
- **Effect:** when `custom.css` exists, every reader page links `/theme/custom.css` after
  `/reader/reader.css`.
