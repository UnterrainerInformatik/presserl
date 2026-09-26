## Purpose

Lets newsroom members write articles with revisions, keeps the published state separate from
work in progress, validates article bodies against a structured allowlist and publishes or
takes articles offline according to the author's roles.

## ADDED Requirements

### Requirement: Articles are written by publishers and editors-in-chief
The system SHALL allow authenticated users holding `PUBLISHER` or `EDITOR_IN_CHIEF` to use the
article endpoints under `/api/articles`. Users holding neither role SHALL receive `403` on every
article endpoint. The creating user SHALL become the article's author, recorded with their
username and display name as returned by `GET /api/me`.

#### Scenario: Publisher creates an article
- **WHEN** the publisher calls `POST /api/articles` with a headline `Hello`
- **THEN** the response is `201` with a `Location` header, `status` `DRAFT`, `revision` `1`, the headline `Hello` and the publisher as `author`

#### Scenario: Reader is refused
- **WHEN** a user holding only `READER` calls `GET /api/articles`
- **THEN** the response is `403`

#### Scenario: No token
- **WHEN** a client calls `GET /api/articles` without a token
- **THEN** the response is `401`

### Requirement: Article content fields
An article revision SHALL consist of `kicker`, `headline`, `subheadline` and `lead` as plain
text and `body` as a structured document. Text fields SHALL default to an empty string, SHALL
be stored trimmed and SHALL NOT contain any control character (including line breaks);
`kicker`, `headline` and `subheadline` SHALL be at most 200 characters and `lead` at most 1000.
A missing `body` SHALL mean an empty document (`{"version":1,"blocks":[]}`). A draft MAY have an
empty headline.

#### Scenario: Create without content
- **WHEN** the publisher calls `POST /api/articles` with `{}`
- **THEN** the response is `201` with all text fields empty and an empty body

#### Scenario: Headline too long
- **WHEN** an article is saved with a headline of 201 characters
- **THEN** the response is `400` naming the field `headline`

#### Scenario: Line break in a headline
- **WHEN** an article is saved with the headline `Hello\nWorld`
- **THEN** the response is `400` naming the field `headline`

### Requirement: Body format version 1 with allowlist validation
The system SHALL accept an article body only in the structured format version 1:
an object `{"version": 1, "blocks": [...]}` whose blocks are
- `{"type": "paragraph", "content": [runs]}`,
- `{"type": "subhead", "text": "..."}`,
- `{"type": "quote", "content": [runs]}`,
- `{"type": "list", "items": [[runs], ...]}` (bullet list, at least one item),

where a run is `{"text": "...", "bold": true|false}` (`bold` optional, default `false`) with
non-empty `text`. Any other block type, any unknown field, a missing required field, a
`version` other than `1`, control characters other than line feed inside paragraph, quote or
list runs, more than 500 blocks or a body larger than 200 000 characters of text SHALL be
rejected with `400`. Raw HTML SHALL never be interpreted; markup characters are stored as text.

#### Scenario: Valid standard body
- **WHEN** an article is saved with a body containing a paragraph with a bold run, a subhead, a quote and a two-item list
- **THEN** the response is `200` and a subsequent `GET` returns the body unchanged

#### Scenario: Unknown block type
- **WHEN** an article is saved with a block `{"type": "html", "html": "<script>alert(1)</script>"}`
- **THEN** the response is `400` naming `body.blocks[0].type` and nothing is stored

#### Scenario: Unknown mark
- **WHEN** an article is saved with a run `{"text": "x", "italic": true}`
- **THEN** the response is `400` naming the offending field

#### Scenario: Markup in text is kept as text
- **WHEN** an article is saved with a paragraph run `<b>hi</b>`
- **THEN** the response is `200` and the run's `text` is returned as `<b>hi</b>` verbatim

### Requirement: Validation errors name the offending fields
A `400` caused by article content SHALL return a JSON body
`{"errors": [{"field": "<path>", "message": "<text>"}]}` listing every violation found, with
paths such as `headline` or `body.blocks[2].content[0].text`.

#### Scenario: Two violations
- **WHEN** an article is saved with a 201-character kicker and a block of unknown type at index 0
- **THEN** the response is `400` and `errors` contains entries for `kicker` and `body.blocks[0].type`

### Requirement: Working revision and live revision
Each article SHALL have numbered revisions starting at `1`. Saving an article (`PUT`) SHALL
overwrite its latest revision while that revision has never been published; if the latest
revision has been published, saving SHALL create a new revision numbered one higher. Publishing
SHALL make the latest revision the article's live revision and mark it as published. The live
revision SHALL remain unchanged by later saves until the next publish.

#### Scenario: Autosave on a draft
- **WHEN** the author saves a draft article three times
- **THEN** the article still has only revision `1`, containing the last saved content

#### Scenario: Editing a published article
- **WHEN** the author saves a published article whose live revision is `1`
- **THEN** a revision `2` with the new content exists, `liveRevision` stays `1` and `hasUnpublishedChanges` is `true`

#### Scenario: Republishing
- **WHEN** the publisher-author publishes that article again
- **THEN** `liveRevision` is `2` and `hasUnpublishedChanges` is `false`

### Requirement: Optimistic concurrency on save
A save SHALL carry the article `version` the client last received. If it differs from the
stored version, the save SHALL be rejected with `409` and change nothing. Every successful save,
publish or take-offline SHALL increase the version.

#### Scenario: Stale save from a second tab
- **WHEN** two clients load an article with version `3`, the first saves successfully and the second then saves with version `3`
- **THEN** the second save is answered with `409` and the first client's content is kept

### Requirement: Only the author edits and deletes
Only the article's author SHALL save (`PUT`) or delete it. An article SHALL be deletable only
while it has never been published; deleting SHALL remove the article and its revisions.
Violations SHALL be answered with `403` (not the author) or `409` (already published once).

#### Scenario: Editor-in-chief edits the publisher's article
- **WHEN** an editor-in-chief saves an article authored by the publisher
- **THEN** the response is `403`

#### Scenario: Delete a draft
- **WHEN** the author deletes an article that was never published
- **THEN** the response is `204` and `GET` on it returns `404`

#### Scenario: Delete an offline article
- **WHEN** the author deletes an article that was published and is now offline
- **THEN** the response is `409` and the article still exists

### Requirement: Publishing without an approval level
An article SHALL be published directly when its author holds `PUBLISHER` and the requesting
user is the author: the status becomes `PUBLISHED` and the latest revision becomes live.
Publishing SHALL require a non-empty headline (`400` naming `headline` otherwise) and SHALL be
refused with `409` when the article is `PUBLISHED` without unpublished changes. Any other user
— including an editor-in-chief publishing their own article, which needs approval that is not
yet available — SHALL receive `403`.

#### Scenario: Solo publisher publishes
- **WHEN** the publisher publishes their own draft with headline `Hello`
- **THEN** the response is `200` with `status` `PUBLISHED`, `liveRevision` `1` and a `publishedAt` timestamp

#### Scenario: Editor-in-chief cannot publish yet
- **WHEN** an editor-in-chief who is not a publisher publishes their own draft
- **THEN** the response is `403` and the article stays `DRAFT`

#### Scenario: Publish without headline
- **WHEN** the publisher publishes a draft with an empty headline
- **THEN** the response is `400` naming `headline`

#### Scenario: Back online
- **WHEN** the publisher-author publishes their article that is `OFFLINE`
- **THEN** the status becomes `PUBLISHED` with the latest revision live

### Requirement: Taking an article offline
The author, any `EDITOR_IN_CHIEF` and any `PUBLISHER` SHALL be able to take a `PUBLISHED`
article offline without approval; its status becomes `OFFLINE` and it keeps its live revision
reference. Taking offline an article that is not `PUBLISHED` SHALL be answered with `409`.

#### Scenario: Editor-in-chief takes the publisher's article offline
- **WHEN** an editor-in-chief takes a published article of the publisher offline
- **THEN** the response is `200` with `status` `OFFLINE`

#### Scenario: Draft cannot go offline
- **WHEN** the author takes a `DRAFT` offline
- **THEN** the response is `409`

### Requirement: Server-computed allowed actions
Every article representation SHALL carry `allowedActions`, the subset of `EDIT`, `PUBLISH`,
`TAKE_OFFLINE` and `DELETE` that the requesting user may perform on the article in its current
state under the rules above. Clients SHALL be able to rely on an action being accepted (apart
from content validation and concurrency) exactly when it is listed.

#### Scenario: Solo publisher on own draft
- **WHEN** the publisher fetches their own draft
- **THEN** `allowedActions` is `["EDIT", "PUBLISH", "DELETE"]`

#### Scenario: Editor-in-chief on the publisher's published article
- **WHEN** an editor-in-chief fetches a published article of the publisher
- **THEN** `allowedActions` is `["TAKE_OFFLINE"]`

#### Scenario: Published without changes
- **WHEN** the publisher fetches their own published article without unpublished changes
- **THEN** `allowedActions` is `["EDIT", "TAKE_OFFLINE"]`

### Requirement: Listing and reading articles
`GET /api/articles` SHALL return summaries of all articles, newest change first, optionally
filtered by `status` and by `mine=true` (only articles the requesting user authored). An
unknown `status` value SHALL be answered with `400`. `GET /api/articles/{id}` SHALL return the
article with the content of its latest revision; an unknown id SHALL be answered with `404`.

#### Scenario: Filter own drafts
- **WHEN** the publisher and an editor-in-chief each have a draft and the editor-in-chief calls `GET /api/articles?status=DRAFT&mine=true`
- **THEN** only the editor-in-chief's draft is returned

#### Scenario: Unknown article
- **WHEN** a writer calls `GET /api/articles/999999`
- **THEN** the response is `404`

### Requirement: Revision history
`GET /api/articles/{id}/revisions` SHALL list the article's revisions (number, created and
updated timestamps, publication timestamp or `null`, whether it is live, headline), newest
first. `GET /api/articles/{id}/revisions/{number}` SHALL return the content of that revision;
an unknown number SHALL be answered with `404`.

#### Scenario: History after republishing
- **WHEN** an article was published, edited and published again
- **THEN** the history lists revisions `2` (live) and `1` (published, not live)
