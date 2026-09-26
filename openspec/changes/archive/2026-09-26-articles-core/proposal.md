## Why

M0 left Presserl with a login and an empty front page; nobody can write anything yet. M1
("solo newspaper") needs articles, and both the editor in the admin app and the reader's
article pages depend on the same foundation: an article model with revisions, a validated
structured body format and the REST contract to create, edit, publish and take articles offline.
This change delivers that foundation so `article-editor` and `reader-articles` can build on a
fixed contract.

## What Changes

- New tables `article` and `article_revision` (Flyway `V2`). An article has a status
  (`DRAFT`, `PUBLISHED`, `OFFLINE`; `SUBMITTED` stays reserved for M3), an author (Keycloak
  subject, username and display name), a pointer to its **live revision** and an optimistic-lock
  version.
- **Working-revision model:** saving overwrites the article's latest revision while that revision
  has never been published; once it has been published, the next save starts a new revision.
  Autosave therefore does not create a revision per keystroke, and the published state stays
  untouched until the author publishes again.
- **Structured body format v1** (JSON, blocks + inline runs) covering the `standard` editor level:
  paragraph, subhead, quote and bullet list, with bold as the only inline mark. The server
  validates every body against an allowlist (unknown block types, fields or marks, oversized
  content and control characters are rejected). Kicker, headline, subheadline and lead are plain
  text fields next to the body.
- REST endpoints under `/api/articles`: list (filter by status and "mine"), get, create, update,
  delete (never-published articles only), publish, take offline, list revisions, get a revision.
- **Permissions for M1** (no sections yet): writers are users holding `PUBLISHER` or
  `EDITOR_IN_CHIEF`; only the author edits their article; **only a publisher-author publishes
  directly** (an editor-in-chief's article would need a publisher's approval, which arrives in
  M3); editors-in-chief, publishers and the author take articles offline. `READER` and users
  without newspaper roles get `403`.
- Every article response carries **`allowedActions`**, computed by the server (pulled forward
  from M3), so clients render buttons only from the server's verdict.
- Dev realm gains an editor-in-chief and a reader test user for the permission tests.
- `ai/primer/endpoints.md`, `.http` files and admin API client DTOs/methods for all new endpoints.
- `docs/architecture.md` REST sketch and data model updated to the implemented contract;
  `ai/open-proposals.md` M1 entry reduced to the remaining parts, M3 entry notes that
  `allowedActions` already exists.

## Non-goals

- Editor UI in the admin app (`article-editor`) and reader front page / article page,
  `visibility=private` enforcement (`reader-articles`).
- Sections, section roles, reporters (M2); approval chain, `SUBMITTED`, trust, review notes,
  emergency-brake lock (M3). Taking offline by a publisher does not lock in M1.
- Images and lead images (M5), issues (M6), info boxes, links and galleries (`profi` level).
- Restoring an old revision, per-user editor level, per-level server validation (the server
  validates the format; the level only limits the editor's tools).
- Pagination of the article list.

## Capabilities

### New Capabilities
- `articles`: article model with revisions and live revision, body format v1 with allowlist
  validation, article REST endpoints, M1 permission rules and `allowedActions`.

### Modified Capabilities
<!-- none: /api/articles is covered by the existing "non-public endpoints need a bearer token" rule -->

## Impact

- **backend:** new package `info.unterrainer.presserl.article` (entities, body model + validator,
  service, resource, DTOs), `V2__articles.sql`, dev realm users, tests (`@QuarkusTest` +
  unit tests for the body validator).
- **admin:** DTOs and `ApiClient` methods for the article endpoints, DTO tests. No UI.
- **reader:** none (reads the live revision in `reader-articles`).
- **deploy:** none (realm template unchanged — test users live only in the dev realm).
- **docs / ai:** `docs/architecture.md`, `ai/primer/endpoints.md`, `ai/open-proposals.md`,
  `http/articles.http`.
