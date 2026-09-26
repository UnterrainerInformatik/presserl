## Context

The backend (Quarkus REST, Hibernate Reactive with Panache, Flyway `V1__newspaper.sql`) knows the
logged-in user only from the bearer token: `sub`, `preferred_username`, `name` and the `groups`
claim mapped to `NewspaperRole` (`PUBLISHER`, `EDITOR_IN_CHIEF`, `READER`). There are no
sections and no per-section roles yet (M2), and no approval chain (M3). The admin app has a Ktor
`ApiClient` with hand-written DTOs. Motivation and scope: see proposal.md; required behaviour:
see `specs/articles/spec.md`.

## Goals / Non-Goals

**Goals:**
- A data model that M2 (sections), M3 (`SUBMITTED`, pending level, lock) and M5 (lead image,
  image blocks) extend with additive migrations only.
- One place that decides permissions and produces `allowedActions`, so M3 replaces rules, not
  call sites.
- A body format the `article-editor` change can map onto either a rich-text component or a
  block-based editor.

**Non-Goals:**
- Per-level validation (a `starter` user sending a quote is accepted); full-text search;
  pagination; revision restore.

## Decisions

### D1 — Tables

```sql
CREATE TABLE article (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status               TEXT        NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','PUBLISHED','OFFLINE')),
    author_sub           TEXT        NOT NULL,
    author_username      TEXT        NOT NULL,
    author_display_name  TEXT        NOT NULL,
    live_revision        INT,                    -- revision number, NULL until first publish
    published_at         TIMESTAMPTZ,            -- first publication
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX article_updated_at_idx ON article (updated_at DESC);
CREATE INDEX article_author_sub_idx ON article (author_sub);

CREATE TABLE article_revision (
    article_id   BIGINT      NOT NULL REFERENCES article (id) ON DELETE CASCADE,
    number       INT         NOT NULL,
    kicker       TEXT        NOT NULL DEFAULT '',
    headline     TEXT        NOT NULL DEFAULT '',
    subheadline  TEXT        NOT NULL DEFAULT '',
    lead         TEXT        NOT NULL DEFAULT '',
    body         JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,                    -- set when this revision became live
    PRIMARY KEY (article_id, number)
);
```

- `SUBMITTED` is allowed by the check constraint now so M3 does not need to alter it; nothing in
  this change writes it.
- The live revision is referenced by number, not by a surrogate id: the composite key is natural
  (`/revisions/{number}`) and the article's latest revision is `max(number)`.
- Author identity is the token `sub` (stable); username and display name are snapshots for the
  byline. When M2 lets people rename, it can refresh the snapshot.
- `version` is the JPA `@Version` column; Hibernate Reactive raises `OptimisticLockException`
  which maps to `409`. The client-supplied version is compared explicitly before the update so a
  stale save fails even without a concurrent transaction.
- Alternatives: one table with JSONB history (rejected — revision listing and live lookup become
  JSON queries); `current_revision_id` pointer (unnecessary with `max(number)`).

### D2 — Working-revision rule

`save(article, content)`:
1. latest = revision with the highest number.
2. If `latest.published_at IS NULL` → overwrite latest's fields, bump `updated_at`.
3. Else → insert revision `latest.number + 1` with the content.
4. article `updated_at = now`, `version++`.

`publish`: latest gets `published_at = now` (if not already), `article.live_revision =
latest.number`, `status = PUBLISHED`, `article.published_at` set on the first publication.
`hasUnpublishedChanges = live_revision IS NOT NULL AND latest.number > live_revision`, or for an
`OFFLINE` article simply whether latest ≠ live. Taking offline keeps `live_revision` so the
reader change can tell "was published" from "never published".

A publish of an `OFFLINE` article whose latest revision is already live re-uses it (no new
revision). Every PUT is a full replacement of the content fields; there is no PATCH.

### D3 — Body model and validation

Body JSON is parsed into a `JsonNode` and validated by a hand-written `ArticleBodyValidator`
that walks the tree and collects `FieldError(path, message)` entries — not by Jackson
polymorphic binding. Reasons: we need *all* violations with exact paths (spec), and unknown
fields/types must be reported, not thrown on first failure. The validated tree is stored as-is
(JSONB), so the format round-trips byte-for-byte in meaning. The same validator handles the four
text fields.

Limits (constants in one class, referenced by tests): text fields 200 / lead 1000 characters,
500 blocks, 200 000 characters of run text in total, list items ≥ 1, run text non-empty,
control characters: none except `\n` inside runs. Text is not HTML-escaped on input; escaping
is the reader's job (Qute escapes by default).

Format rationale: a flat block list with inline runs maps 1:1 onto a block-based editor and onto
the paragraph/mark model of rich-text components; `version` lets later milestones add block
types (`image` in M5, `infobox`, links at `profi`) as format `1` extensions or bump to `2` if a
change is incompatible. Only `bold` exists as a mark because the design guidelines prescribe bold
instead of italics.

### D4 — Permissions and `allowedActions`

A single `ArticlePolicy` computes the allowed actions from `(user roles, user sub, article)`;
every endpoint asks the policy before acting (`403` if the action is absent, `409` for state
conflicts, checked in that order: role → ownership → state).

| Action | Rule in M1 |
|---|---|
| `EDIT` | user is author |
| `DELETE` | user is author and `live_revision IS NULL` |
| `PUBLISH` | user is author, holds `PUBLISHER`, and (status ≠ `PUBLISHED` or unpublished changes exist) |
| `TAKE_OFFLINE` | status = `PUBLISHED` and (user is author or holds `EDITOR_IN_CHIEF` or `PUBLISHER`) |

Roles are read from the current token, not stored with the article: the author's current
`PUBLISHER` role is the requesting user's role because only the author may publish.
`presserl.retract.author-can-retract` only concerns reporters (M2) and is not consulted here.

Access to `/api/articles/**` requires `PUBLISHER` or `EDITOR_IN_CHIEF`
(`@RolesAllowed` via a `SecurityIdentityAugmentor` that adds the newspaper roles from the groups
claim, reusing `NewspaperRole.fromGroups`). The global `authenticated` policy on `/api/*` still
yields `401` for missing tokens.

### D5 — REST contract

All bodies JSON. `ArticleDto` (returned by get, create, update, publish, offline):

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
  "body": {
    "version": 1,
    "blocks": [
      { "type": "paragraph", "content": [ { "text": "It started " }, { "text": "in May", "bold": true }, { "text": "." } ] },
      { "type": "subhead", "text": "Watering" },
      { "type": "quote", "content": [ { "text": "Every day!" } ] },
      { "type": "list", "items": [ [ { "text": "Water" } ], [ { "text": "Sun" } ] ] }
    ]
  },
  "allowedActions": ["EDIT", "PUBLISH", "TAKE_OFFLINE"]
}
```
`revision` = latest revision number; content fields are the latest revision's. `liveRevision`
and `publishedAt` are `null` until the first publish.

| Method + path | Request | Success | Errors |
|---|---|---|---|
| `GET /api/articles?status=&mine=` | — | `200` `[ArticleSummaryDto]` | `400` unknown status |
| `POST /api/articles` | `ArticleContent` (all fields optional) | `201` `ArticleDto`, `Location: /api/articles/{id}` | `400` |
| `GET /api/articles/{id}` | — | `200` `ArticleDto` | `404` |
| `PUT /api/articles/{id}` | `ArticleContent` + `"version": 5` (required) | `200` `ArticleDto` | `400`, `403`, `404`, `409` |
| `DELETE /api/articles/{id}` | — | `204` | `403`, `404`, `409` |
| `POST /api/articles/{id}/publish` | — | `200` `ArticleDto` | `400` (headline), `403`, `404`, `409` |
| `POST /api/articles/{id}/offline` | — | `200` `ArticleDto` | `403`, `404`, `409` |
| `GET /api/articles/{id}/revisions` | — | `200` `[RevisionSummaryDto]` | `404` |
| `GET /api/articles/{id}/revisions/{number}` | — | `200` `RevisionDto` | `404` |

`ArticleContent`: `{ "kicker", "headline", "subheadline", "lead", "body" }`.
`ArticleSummaryDto`: `{ id, status, author, headline, kicker, revision, liveRevision,
hasUnpublishedChanges, updatedAt, publishedAt, allowedActions }`.
`RevisionSummaryDto`: `{ number, headline, createdAt, updatedAt, publishedAt, live }`.
`RevisionDto`: `RevisionSummaryDto` + `kicker, subheadline, lead, body`.
Validation error (`400`): `{ "errors": [ { "field": "body.blocks[0].type", "message": "unknown block type 'html'" } ] }`.
`403`/`404`/`409` carry `{ "errors": [ { "field": null, "message": "..." } ] }` for uniformity.
Publish/offline take no body, so the admin app does not need to send `version`; they are
idempotence-guarded by the `409` state rules instead.

`PUT` without `version` → `400` naming `version`. Unknown top-level fields in `ArticleContent`
→ `400` (same strictness as the body).

### D6 — Reactive service layer

`ArticleService` methods run in `Panache.withTransaction`; entity classes `ArticleEntity` and
`ArticleRevisionEntity` (`@IdClass` for the composite key). Latest revision per article for the
list comes from one query (`DISTINCT ON (article_id) … ORDER BY article_id, number DESC` as a
native query, or a `max(number)` subquery in HQL) to avoid N+1.

### D7 — Test users

The dev realm gains `chief` (group `editor-in-chief`) and `reader` (group `reader`), password =
username, in line with `nogroups`. The realm template is untouched; `RealmTemplateDriftTest`
compares clients, groups and flags, not users (verify during apply).

### D8 — Admin client

`Dtos.kt` gains `ArticleDto`, `ArticleSummaryDto`, `ArticleContent`, `AuthorDto`,
`RevisionSummaryDto`, `RevisionDto`, `ApiErrorDto`; `body` stays a `JsonObject` — the typed
body model belongs to `article-editor`, which knows its editor component. `ApiClient` gains one
method per endpoint. Timestamps stay ISO strings (no datetime dependency yet).

## Risks / Trade-offs

- [Overwriting the working revision loses intermediate draft states] → accepted by decision;
  undo lives in the editor. A later change can add periodic snapshots without touching the API.
- [Body format v1 may not fit the rich-text component picked in `article-editor`] → the format
  is versioned; `article-editor` can add a mapping layer or propose v2 while no production data
  exists.
- [Roles come from the token, so revoking `PUBLISHER` takes effect only after token expiry] →
  short-lived access tokens (realm setting) already bound this; same as for `/api/me`.
- [List without pagination] → fine for a family newspaper in M1; add `limit/offset` when the
  archive view needs it.

## Migration Plan

`V2__articles.sql` only creates tables; no data migration. Rollback = redeploy the previous image
(empty article tables are ignored by it); Flyway history keeps `V2`, so a re-rollout is a no-op.
