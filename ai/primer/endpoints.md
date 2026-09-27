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
  { "username": "papa", "displayName": "Papa", "roles": ["PUBLISHER"],
    "sectionRoles": [ { "sectionId": 1, "sectionName": "Sport", "role": "SECTION_EDITOR" } ] }
  ```
  - `username`: `preferred_username` claim, falling back to `sub`.
  - `displayName`: `name` claim, falling back to `username`.
  - `roles`: newspaper roles from the `groups` claim in the order `PUBLISHER`,
    `EDITOR_IN_CHIEF`, `READER`; empty array for a user without newspaper groups.
  - `sectionRoles`: the user's section roles, ordered by section position; `[]` for none.
  - M2 extends this response additively (scopes, allowed actions).
- **Errors:** `401` (empty body) without a valid token.
- **Side effects:** none.

---

# Articles

All article endpoints require a bearer token **and** the role `PUBLISHER` or `EDITOR_IN_CHIEF`.
Users with neither role (e.g. only `READER`) get `403` with an empty body; missing/invalid token
→ `401`. Every other refusal carries the error body below.

**Error body** (`400`, `403`, `404`, `409` raised by the article rules):
```json
{ "errors": [ { "field": "body.blocks[0].type", "message": "unknown block type 'html'" } ] }
```
`field` is a path (`headline`, `version`, `status`, `body.blocks[2].content[0].text`, …) for
validation errors and `null` for `403`/`404`/`409`. A `400` lists **every** violation found.

**Revisions.** Revisions are numbered from `1`. `PUT` overwrites the latest revision while it has
never been published (working revision — autosave does not create revisions); once the latest
revision has been published, the next `PUT` creates revision `latest + 1`. Publishing makes the
latest revision the **live revision** (`liveRevision`); later saves do not touch it until the
next publish. `hasUnpublishedChanges` = `liveRevision != null && revision != liveRevision`.

**Versions.** `version` is the optimistic-lock counter. Every successful save, publish and
take-offline increases it. `PUT` must send the version last received; a different stored version
→ `409` and nothing changes (reload, then save again).

**`allowedActions`.** Every article representation lists what the requesting user may do now, in
the order `EDIT`, `PUBLISH`, `TAKE_OFFLINE`, `DELETE`. Render buttons only from this list: an
action is accepted exactly when it is listed (apart from content validation and `409` on a stale
version). Rules (M1, no sections yet):

| Action | Allowed when |
|---|---|
| `EDIT` | user is the author |
| `PUBLISH` | user is the author, holds `PUBLISHER`, and status ≠ `PUBLISHED` or unpublished changes exist |
| `TAKE_OFFLINE` | status = `PUBLISHED` and user is the author, an `EDITOR_IN_CHIEF` or a `PUBLISHER` |
| `DELETE` | user is the author and the article was never published (`liveRevision == null`) |

Endpoints check role → ownership → state: a missing role/ownership is `403`, a state that does
not permit the action is `409`. An editor-in-chief cannot publish their own article yet (needs
approval, arrives in M3).

**Status:** `DRAFT` | `PUBLISHED` | `OFFLINE` (`SUBMITTED` reserved for M3, never returned yet).

## Content fields and body format v1

`ArticleContent` (request body of create and save):
```json
{ "kicker": "", "headline": "", "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] } }
```
- All fields optional; missing or `null` text fields → `""`, missing `body` →
  `{"version": 1, "blocks": []}`. Every save is a **full replacement** (no PATCH).
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

Returned by get, create, save, publish and take-offline. Content fields are those of the latest
revision (`revision`).
```json
{
  "id": 42,
  "status": "PUBLISHED",
  "author": { "username": "papa", "displayName": "Papa" },
  "revision": 2,
  "liveRevision": 1,
  "hasUnpublishedChanges": true,
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
- `liveRevision`, `publishedAt` (first publication): `null` until the first publish.
- Timestamps: ISO-8601 UTC, millisecond precision.

## `GET /api/articles`

Summaries of all articles, newest change (`updatedAt`) first. No pagination yet.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Query:** `status` (optional, `DRAFT` | `SUBMITTED` | `PUBLISHED` | `OFFLINE`), `mine`
  (optional, `true` → only articles the requesting user authored)
- **Response `200`:** array of `ArticleSummaryDto`
  ```json
  [ { "id": 42, "status": "DRAFT", "author": { "username": "papa", "displayName": "Papa" },
      "headline": "Hello", "kicker": "", "revision": 1, "liveRevision": null,
      "hasUnpublishedChanges": false, "updatedAt": "2026-09-26T10:05:00Z", "publishedAt": null,
      "allowedActions": ["EDIT", "PUBLISH", "DELETE"] } ]
  ```
- **Errors:** `400` unknown `status` (field `status`).
- **Side effects:** none.

## `POST /api/articles`

Creates a `DRAFT` with revision `1`; the requesting user becomes the author.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Body:** `ArticleContent` (`{}` is fine)
- **Response `201`:** `ArticleDto`, header `Location: …/api/articles/{id}`
- **Errors:** `400` content validation.
- **Side effects:** article and revision `1` stored.

## `GET /api/articles/{id}`

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Response `200`:** `ArticleDto`
- **Errors:** `404` unknown id.

## `PUT /api/articles/{id}`

Saves the content (full replacement) following the working-revision rule.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`; only the author (`EDIT`)
- **Body:** `ArticleContent` plus `"version": 5` (required)
- **Response `200`:** `ArticleDto` (new `version`, `revision` possibly incremented)
- **Errors:** `400` validation (missing `version` names `version`), `403` not the author, `404`,
  `409` stale `version`.
- **Side effects:** latest revision overwritten, or new revision if the latest was published;
  `updatedAt` and `version` change.

## `DELETE /api/articles/{id}`

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`; only the author (`DELETE`)
- **Response `204`**
- **Errors:** `403` not the author, `404`, `409` the article was published at least once (take
  it offline instead).
- **Side effects:** article and all revisions removed.

## `POST /api/articles/{id}/publish`

Makes the latest revision live. No request body.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`; `PUBLISH` (author holding `PUBLISHER`)
- **Response `200`:** `ArticleDto` with `status` `PUBLISHED`, `liveRevision` = `revision`
- **Errors:** `400` empty headline (field `headline`), `403`, `404`, `409` already `PUBLISHED`
  without unpublished changes.
- **Side effects:** latest revision marked published (if not yet), `liveRevision` set, status
  `PUBLISHED`, `publishedAt` set on first publication; `updatedAt` and `version` change. Works
  from `DRAFT` and `OFFLINE` (an `OFFLINE` article whose latest revision is already live goes
  back online without a new revision).

## `POST /api/articles/{id}/offline`

Takes a published article offline without approval. No request body.

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF` (`TAKE_OFFLINE`: author, any editor-in-chief, any
  publisher)
- **Response `200`:** `ArticleDto` with `status` `OFFLINE`; `liveRevision` is kept
- **Errors:** `403`, `404`, `409` status is not `PUBLISHED`.
- **Side effects:** status `OFFLINE`; `updatedAt` and `version` change.

## `GET /api/articles/{id}/revisions`

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Response `200`:** array of `RevisionSummaryDto`, newest first
  ```json
  [ { "number": 2, "headline": "Second", "createdAt": "…", "updatedAt": "…",
      "publishedAt": "2026-09-26T10:07:00Z", "live": true },
    { "number": 1, "headline": "First", "createdAt": "…", "updatedAt": "…",
      "publishedAt": "2026-09-26T10:01:00Z", "live": false } ]
  ```
  `publishedAt`: when that revision became live, `null` for a working revision.
- **Errors:** `404` unknown article.

## `GET /api/articles/{id}/revisions/{number}`

- **Auth:** `PUBLISHER` or `EDITOR_IN_CHIEF`
- **Response `200`:** `RevisionDto` = `RevisionSummaryDto` fields plus `kicker`, `subheadline`,
  `lead`, `body`
- **Errors:** `404` unknown article or revision number.

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

**Keycloak unavailable:** when Keycloak cannot be reached or refuses the service account, every
account endpoint answers `503` with
`{"errors": [{"field": null, "message": "the account service is unavailable; try again later"}]}`.

## `AccountDto`

```json
{ "id": "5f0c…", "username": "chief", "firstName": "Chief", "lastName": "Editor",
  "roles": ["EDITOR_IN_CHIEF"], "sectionRoles": [ { "sectionId": 1, "role": "REPORTER" } ],
  "enabled": true }
```
`id` is the Keycloak user id. `firstName`/`lastName` are `""` when unset. `roles` are the newspaper
roles from the account's groups in the order `PUBLISHER`, `EDITOR_IN_CHIEF`, `READER` (`[]` for
none). `sectionRoles` are the account's section roles ordered by section position (`[]` for none).

## `GET /api/accounts`

Every account of the realm except service accounts, sorted by `username`. At most 1000 accounts
(no paging; far above a family newspaper).

- **Auth:** `PUBLISHER`, `EDITOR_IN_CHIEF` or `SECTION_EDITOR` in any section
- **Response `200`:**
  ```json
  { "assignableRoles": ["PUBLISHER", "EDITOR_IN_CHIEF", "READER"],
    "accounts": [ { "id": "5f0c…", "username": "chief", "firstName": "Chief", "lastName": "Editor",
                    "roles": ["EDITOR_IN_CHIEF"], "sectionRoles": [], "enabled": true } ] }
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
                 "enabled": true },
    "password": "tiger-wolke-apfel-leiter" }
  ```
  `password` is four words from a curated German word list (`a-z`, 3–8 letters each) joined by
  `-`, chosen with a secure random source. It is **not temporary** and appears **only in this
  response** — the server neither stores nor logs it. Show it on the account slip.
- **Errors:** `400` validation (every violation; also names Keycloak rejects, e.g. forbidden
  characters, at `firstName`/`lastName`/`username`; unknown section at `sectionRoles`), `403` a
  role that may not be assigned (field `roles`) or a section role outside the own scope (field
  `sectionRoles`) — nothing created, `409` username taken, ignoring case (field `username`), `503`.
- **Side effects:** Keycloak user created with the password credential and group memberships,
  section roles stored; all or nothing (a failure after the creation deletes the user again).
  Logged at INFO with username, creator, roles and section roles.

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
| `POST`, `PUT /api/sections/{id}`, `PUT /api/sections/order` | `PUBLISHER`, `EDITOR_IN_CHIEF` |
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
  "assignableRoles": ["SECTION_EDITOR", "REPORTER"] }
```
`assignableRoles`: the section roles the requesting user may assign in this section, in the order
`SECTION_EDITOR`, `REPORTER` (`[]` for none).

## `GET /api/sections`

All sections ordered by `position` (ties by `id`).

- **Auth:** any authenticated user
- **Response `200`:**
  ```json
  { "canManage": true,
    "sections": [ { "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0,
                    "assignableRoles": ["SECTION_EDITOR", "REPORTER"] } ] }
  ```
  `canManage`: whether the user may create, change and reorder sections (`PUBLISHER`,
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
