# articles Specification

## Purpose

Lets newsroom members write articles with revisions, keeps the published state separate from
work in progress, validates article bodies against a structured allowlist and publishes or
takes articles offline according to the author's roles.

## Requirements

### Requirement: Articles are written by newsroom members
The system SHALL allow authenticated users holding `PUBLISHER` or `EDITOR_IN_CHIEF`, or a section
role (`SECTION_EDITOR` or `REPORTER`) in at least one section, to use the article endpoints under
`/api/articles`. Users holding none of these SHALL receive `403` on every article endpoint.
Publishers and editors-in-chief SHALL be allowed to write in every section; section editors and
reporters SHALL be allowed to write only in the sections where they hold a section role. The
creating user SHALL become the article's author, recorded with their username and display name
as returned by `GET /api/me`.

#### Scenario: Publisher creates an article
- **WHEN** the publisher calls `POST /api/articles` with a headline `Hello`
- **THEN** the response is `201` with a `Location` header, `status` `DRAFT`, `revision` `1`, the headline `Hello` and the publisher as `author`

#### Scenario: Reporter creates an article in their section
- **WHEN** `reader` is `REPORTER` in `Sport` and calls `POST /api/articles` with the `sectionId` of `Sport`
- **THEN** the response is `201` with `section.name` `Sport` and `reader` as `author`

#### Scenario: Reader is refused
- **WHEN** a user holding only `READER` and no section role calls `GET /api/articles`
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
revision has been published, saving SHALL create a new revision numbered one higher — unless the
saved content (kicker, headline, subheadline, lead and body) equals the latest revision's
content, in which case no revision SHALL be created or changed. Publishing SHALL make the latest
revision the article's live revision and mark it as published. The live revision SHALL remain
unchanged by later saves until the next publish.

#### Scenario: Autosave on a draft
- **WHEN** the author saves a draft article three times
- **THEN** the article still has only revision `1`, containing the last saved content

#### Scenario: Editing a published article
- **WHEN** the author saves a published article whose live revision is `1`
- **THEN** a revision `2` with the new content exists, `liveRevision` stays `1` and `hasUnpublishedChanges` is `true`

#### Scenario: Saving a published article unchanged
- **WHEN** the author saves a published article whose live revision is `1` with exactly its current content
- **THEN** the article still has only revision `1` and `hasUnpublishedChanges` is `false`

#### Scenario: Republishing
- **WHEN** the publisher-author publishes that article again
- **THEN** `liveRevision` is `2` and `hasUnpublishedChanges` is `false`

### Requirement: Optimistic concurrency on save
A save SHALL carry the article `version` the client last received. If it differs from the
stored version, the save SHALL be rejected with `409` and change nothing. Every successful save,
publish, take-offline, unlock, submit, approve, reject and withdraw SHALL increase the version.

#### Scenario: Stale save from a second tab
- **WHEN** two clients load an article with version `3`, the first saves successfully and the second then saves with version `3`
- **THEN** the second save is answered with `409` and the first client's content is kept

#### Scenario: Submit increases the version
- **WHEN** the author submits an article with version `3`
- **THEN** the response carries a version greater than `3`

### Requirement: Only the author edits and deletes
Only the article's author SHALL save (`PUT`) or delete it, and only while they may write in the
article's section. Saving SHALL additionally require that no submission is pending. An article
SHALL be deletable only while it has never been published; deleting SHALL remove the article, its
revisions and its reviews, also while a submission is pending. Violations SHALL be answered with
`403` (not the author, or no write access to the article's section) or `409` (already published
once, or a submission is pending on save).

#### Scenario: Editor-in-chief edits the publisher's article
- **WHEN** an editor-in-chief saves an article authored by the publisher
- **THEN** the response is `403`

#### Scenario: Author lost the section role
- **WHEN** `reader` authored a draft in `Sport` while `REPORTER` there, the role was removed, and `reader` is still `REPORTER` in `Kultur` and saves the draft
- **THEN** the response is `403` and the draft is unchanged

#### Scenario: Delete a draft
- **WHEN** the author deletes an article that was never published
- **THEN** the response is `204` and `GET` on it returns `404`

#### Scenario: Delete a submitted article
- **WHEN** the author deletes their never-published article that waits for `SECTION_EDITOR`
- **THEN** the response is `204` and the article is gone

#### Scenario: Delete an offline article
- **WHEN** the author deletes an article that was published and is now offline
- **THEN** the response is `409` and the article still exists

### Requirement: Publishing without an approval level
An article SHALL be published directly when the requesting user is its author, may write in its
section, no submission is pending, and the article's chain (see approval-chain) is empty: the
status becomes `PUBLISHED` and the latest revision becomes live. Publishing SHALL require a
non-empty headline (`400` naming `headline` otherwise) and SHALL be refused with `409` when the
article is `PUBLISHED` without unpublished changes or a submission is pending. Any other user —
including an author whose chain is not empty, who has to submit instead — SHALL receive `403`.

#### Scenario: Solo publisher publishes
- **WHEN** the publisher publishes their own draft with headline `Hello`
- **THEN** the response is `200` with `status` `PUBLISHED`, `liveRevision` `1` and a `publishedAt` timestamp

#### Scenario: Editor-in-chief cannot publish yet
- **WHEN** an editor-in-chief who is not a publisher publishes their own draft while another account holds `PUBLISHER`
- **THEN** the response is `403` and the article stays `DRAFT`

#### Scenario: Publish without headline
- **WHEN** the publisher publishes a draft with an empty headline
- **THEN** the response is `400` naming `headline`

#### Scenario: Back online
- **WHEN** the publisher-author publishes their article that is `OFFLINE`
- **THEN** the status becomes `PUBLISHED` with the latest revision live

### Requirement: Taking an article offline
The author, the section editors of the article's section, any `EDITOR_IN_CHIEF` and any
`PUBLISHER` SHALL be able to take a `PUBLISHED` article offline without approval; its status
becomes `OFFLINE` and it keeps its live revision reference. When the user holds `PUBLISHER`, the
article SHALL additionally become locked (see emergency-brake lock). A pending submission SHALL stay
pending; approving it later puts the article back online with its latest revision. Taking offline
an article that is not `PUBLISHED` SHALL be answered with `409`; any other user SHALL receive `403`.

#### Scenario: Editor-in-chief takes the publisher's article offline
- **WHEN** an editor-in-chief takes a published article of the publisher offline
- **THEN** the response is `200` with `status` `OFFLINE`

#### Scenario: Section editor takes an article of their section offline
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and takes the publisher's published article in `Sport` offline
- **THEN** the response is `200` with `status` `OFFLINE`

#### Scenario: Draft cannot go offline
- **WHEN** the author takes a `DRAFT` offline
- **THEN** the response is `409`

#### Scenario: Offline while changes wait
- **WHEN** a published article whose changes wait for `PUBLISHER` is taken offline
- **THEN** `status` is `OFFLINE` and `pendingLevel` stays `PUBLISHER`

### Requirement: Emergency-brake lock
An article taken offline by a user holding `PUBLISHER` SHALL be **locked**. Article and article
summary representations SHALL carry `locked` (`true` for a locked article, otherwise `false`). Only
an `OFFLINE` article SHALL be locked. The lock SHALL end when the article goes online (published
directly or by a final approval) and when a publisher unlocks it. While an article is locked, its
chain SHALL end with the `PUBLISHER` level (see approval-chain), so only a publisher can put it back
online.

`POST /api/articles/{id}/unlock` SHALL let a user holding `PUBLISHER` lift the lock without any
other change: the article stays `OFFLINE`, keeps its live revision and a pending submission keeps
its `pendingLevel`. Any other user SHALL receive `403`; an article that is not locked SHALL be
answered with `409`.

#### Scenario: Publisher pulls the brake
- **WHEN** the publisher takes `chief`'s published article offline
- **THEN** the response is `200` with `status` `OFFLINE` and `locked` `true`

#### Scenario: Taking offline by others does not lock
- **WHEN** `chief`, who does not hold `PUBLISHER`, takes a reporter's published article offline
- **THEN** the response is `200` with `status` `OFFLINE` and `locked` `false`

#### Scenario: Unlocked article
- **WHEN** a writer fetches a draft
- **THEN** `locked` is `false`

#### Scenario: Publisher unlocks
- **WHEN** the publisher unlocks a locked article of `chief`
- **THEN** the response is `200` with `status` `OFFLINE`, `locked` `false` and the live revision unchanged

#### Scenario: Only publishers unlock
- **WHEN** `chief` unlocks a locked article
- **THEN** the response is `403` and the article stays locked

#### Scenario: Nothing to unlock
- **WHEN** the publisher unlocks an `OFFLINE` article that is not locked
- **THEN** the response is `409`

#### Scenario: Publisher-author puts their locked article back online
- **WHEN** the publisher took their own published article offline and publishes it again
- **THEN** the response is `200` with `status` `PUBLISHED` and `locked` `false`

### Requirement: Server-computed allowed actions
Every article representation SHALL carry `allowedActions`, the subset of `EDIT`, `SUBMIT`,
`PUBLISH`, `WITHDRAW`, `APPROVE`, `REJECT`, `TAKE_OFFLINE`, `UNLOCK` and `DELETE` (in this order)
that the requesting user may perform on the article in its current state under the rules above and
those of the approval chain. Clients SHALL be able to rely on an action being accepted (apart from
content validation, a missing headline, a missing note and concurrency) exactly when it is listed.

#### Scenario: Solo publisher on own draft
- **WHEN** the publisher fetches their own draft
- **THEN** `allowedActions` is `["EDIT", "PUBLISH", "DELETE"]`

#### Scenario: Editor-in-chief on the publisher's published article
- **WHEN** an editor-in-chief fetches a published article of the publisher
- **THEN** `allowedActions` is `["TAKE_OFFLINE"]`

#### Scenario: Published without changes
- **WHEN** the publisher fetches their own published article without unpublished changes
- **THEN** `allowedActions` is `["EDIT", "TAKE_OFFLINE"]`

#### Scenario: Reporter on own draft
- **WHEN** a reporter of `Sport` fetches their own draft in `Sport`
- **THEN** `allowedActions` is `["EDIT", "SUBMIT", "DELETE"]`

#### Scenario: Reporter on own submitted draft
- **WHEN** a reporter of `Sport` fetches their own draft in `Sport` that waits for `SECTION_EDITOR`
- **THEN** `allowedActions` is `["WITHDRAW", "DELETE"]`

#### Scenario: Section editor on a submitted article of their section
- **WHEN** a section editor of `Sport` fetches a reporter's article in `Sport` that waits for `SECTION_EDITOR`
- **THEN** `allowedActions` is `["APPROVE", "REJECT"]`

#### Scenario: Section editor on a published article of their section
- **WHEN** a section editor of `Sport` fetches the publisher's published article in `Sport`
- **THEN** `allowedActions` is `["TAKE_OFFLINE"]`

#### Scenario: Publisher on a locked article
- **WHEN** the publisher fetches a locked article of `chief` without a pending submission
- **THEN** `allowedActions` is `["UNLOCK"]`

#### Scenario: Author on their locked article
- **WHEN** `chief` fetches their locked article without a pending submission
- **THEN** `allowedActions` is `["EDIT", "SUBMIT"]`

### Requirement: Listing and reading articles
`GET /api/articles` SHALL return summaries of the articles visible to the requesting user, newest
change first, optionally filtered by `status`, by `mine=true` (only articles the requesting
user authored) and by `pending=true` (only articles with a pending submission); filters combine.
An unknown `status` value SHALL be answered with `400`. `GET /api/articles/{id}` SHALL return the
article with the content of its latest revision; an unknown id or an article not visible to the
requesting user SHALL be answered with `404`.

#### Scenario: Filter own drafts
- **WHEN** the publisher and an editor-in-chief each have a draft and the editor-in-chief calls `GET /api/articles?status=DRAFT&mine=true`
- **THEN** only the editor-in-chief's draft is returned

#### Scenario: Filter pending articles
- **WHEN** one draft waits for `SECTION_EDITOR`, a published article has changes waiting for `PUBLISHER`, another draft is not submitted, and the publisher calls `GET /api/articles?pending=true`
- **THEN** exactly the two waiting articles are returned

#### Scenario: Unknown article
- **WHEN** a writer calls `GET /api/articles/999999`
- **THEN** the response is `404`

#### Scenario: Reporter reads another reporter's article
- **WHEN** `reader` and `nogroups` are both `REPORTER` in `Sport` and `reader` calls `GET /api/articles/{id}` on a draft of `nogroups`
- **THEN** the response is `404`

### Requirement: Revision history
`GET /api/articles/{id}/revisions` SHALL list the article's revisions (number, created and
updated timestamps, publication timestamp or `null`, whether it is live, headline), newest
first. `GET /api/articles/{id}/revisions/{number}` SHALL return the content of that revision;
an unknown number SHALL be answered with `404`.

#### Scenario: History after republishing
- **WHEN** an article was published, edited and published again
- **THEN** the history lists revisions `2` (live) and `1` (published, not live)

### Requirement: Article visibility
Publishers and editors-in-chief SHALL see every article. Any other writer SHALL see the articles
they authored and, in every section where they are `SECTION_EDITOR`, all articles of that section.
Every endpoint under `/api/articles/{id}` (reading, revisions, reviews, saving, deleting,
publishing, taking offline, unlocking, submitting, approving, rejecting, withdrawing) SHALL answer
`404` for an article the requesting user does not see, without revealing whether it exists.

#### Scenario: Section editor lists their section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `REPORTER` in `Sport` and `Kultur`, `reader` has one draft in `Sport` and one in `Kultur`, and `nogroups` calls `GET /api/articles`
- **THEN** the response contains `reader`'s `Sport` draft and not the `Kultur` draft

#### Scenario: Reporter lists only own articles
- **WHEN** `reader` is `REPORTER` in `Sport` where the publisher has a published article, and `reader` calls `GET /api/articles`
- **THEN** the response contains only articles authored by `reader`

#### Scenario: Revisions of an invisible article
- **WHEN** a reporter calls `GET /api/articles/{id}/revisions` on the publisher's article
- **THEN** the response is `404`

#### Scenario: Approving an invisible article
- **WHEN** a reporter calls `POST /api/articles/{id}/approve` on another reporter's submitted article
- **THEN** the response is `404`

#### Scenario: Unlocking an invisible article
- **WHEN** a reporter calls `POST /api/articles/{id}/unlock` on another reporter's locked article
- **THEN** the response is `404`

### Requirement: Every article belongs to a section
Every article SHALL belong to exactly one section. Article and article summary representations
SHALL carry `section` with the section's `id`, `name`, `slug` and `color`. `POST /api/articles`
and `PUT /api/articles/{id}` SHALL accept an optional numeric `sectionId`; on `PUT` a missing
`sectionId` SHALL keep the current section. The section SHALL belong to the article, not to a
revision: changing it SHALL NOT create a revision and SHALL apply to the published article too.
A `sectionId` that is not a number or names no existing section SHALL be answered with `400`
naming `sectionId`; a section the requesting user may not write in SHALL be answered with `403`
naming `sectionId`. In both cases nothing SHALL be stored.

#### Scenario: Move a draft to another section
- **WHEN** an editor-in-chief saves their draft in `Sport` with the `sectionId` of `Kultur`
- **THEN** the response is `200` with `section.name` `Kultur` and the revision number unchanged

#### Scenario: Move a published article
- **WHEN** the publisher saves their published article without unpublished changes, changing only `sectionId` to `Kultur`
- **THEN** the response is `200` with `section.name` `Kultur`, no new revision and `hasUnpublishedChanges` `false`

#### Scenario: Save without sectionId
- **WHEN** the author saves an article in `Sport` with a body that has no `sectionId`
- **THEN** the article stays in `Sport`

#### Scenario: Reporter moves into a foreign section
- **WHEN** `reader` is `REPORTER` in `Sport` only and saves their draft with the `sectionId` of `Kultur`
- **THEN** the response is `403` naming `sectionId` and the draft stays in `Sport`

#### Scenario: Unknown section
- **WHEN** the publisher creates an article with a `sectionId` that does not exist
- **THEN** the response is `400` naming `sectionId` and no article is created

### Requirement: Default section
The section named by the setting `section.default` (compared ignoring case) SHALL be the default
section. At startup the system SHALL create it (at the last position, with the default palette
colour) when no section exists at all. `POST /api/articles` without `sectionId` SHALL file the
article under the default section when it exists and the requesting user may write there,
otherwise under the first section by position the user may write in. When no section exists at
all, the default section SHALL be created and used.

#### Scenario: Fresh installation
- **WHEN** the backend starts with no section and `section.default` resolving to `General`
- **THEN** a section `General` exists afterwards

#### Scenario: Existing articles are filed
- **WHEN** articles without a section exist from an earlier version, sections `Sport` and `Kultur` exist in this order, and the backend starts
- **THEN** every such article belongs to `Sport` (the database upgrade files them, see sections "Every article has a section in the database")

#### Scenario: Sections exist at startup
- **WHEN** sections `Sport` and `Kultur` exist, none is named like the default section, and the backend starts
- **THEN** no section is created

#### Scenario: Publisher creates without section
- **WHEN** sections `General` and `Sport` exist and the publisher posts `{}` to `/api/articles`
- **THEN** the article belongs to `General`

#### Scenario: Reporter creates without section
- **WHEN** `reader` is `REPORTER` in `Kultur` only, sections `General`, `Sport` and `Kultur` exist, and `reader` posts `{}` to `/api/articles`
- **THEN** the article belongs to `Kultur`

#### Scenario: Default section was renamed
- **WHEN** the section `General` was renamed to `Allerlei`, sections `Allerlei` and `Sport` exist in this order and the publisher posts `{}` to `/api/articles`
- **THEN** no section is created and the article belongs to `Allerlei`

#### Scenario: All sections gone
- **WHEN** no section exists and the publisher posts `{}` to `/api/articles`
- **THEN** a section `General` is created and the article belongs to it
