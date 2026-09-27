## RENAMED Requirements

- FROM: `### Requirement: Articles are written by publishers and editors-in-chief`
- TO: `### Requirement: Articles are written by newsroom members`

## MODIFIED Requirements

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

### Requirement: Only the author edits and deletes
Only the article's author SHALL save (`PUT`) or delete it, and only while they may write in the
article's section. An article SHALL be deletable only while it has never been published; deleting
SHALL remove the article and its revisions. Violations SHALL be answered with `403` (not the
author, or no write access to the article's section) or `409` (already published once).

#### Scenario: Editor-in-chief edits the publisher's article
- **WHEN** an editor-in-chief saves an article authored by the publisher
- **THEN** the response is `403`

#### Scenario: Author lost the section role
- **WHEN** `reader` authored a draft in `Sport` while `REPORTER` there, the role was removed, and `reader` is still `REPORTER` in `Kultur` and saves the draft
- **THEN** the response is `403` and the draft is unchanged

#### Scenario: Delete a draft
- **WHEN** the author deletes an article that was never published
- **THEN** the response is `204` and `GET` on it returns `404`

#### Scenario: Delete an offline article
- **WHEN** the author deletes an article that was published and is now offline
- **THEN** the response is `409` and the article still exists

### Requirement: Taking an article offline
The author, the section editors of the article's section, any `EDITOR_IN_CHIEF` and any
`PUBLISHER` SHALL be able to take a `PUBLISHED` article offline without approval; its status
becomes `OFFLINE` and it keeps its live revision reference. Taking offline an article that is not
`PUBLISHED` SHALL be answered with `409`; any other user SHALL receive `403`.

#### Scenario: Editor-in-chief takes the publisher's article offline
- **WHEN** an editor-in-chief takes a published article of the publisher offline
- **THEN** the response is `200` with `status` `OFFLINE`

#### Scenario: Section editor takes an article of their section offline
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and takes the publisher's published article in `Sport` offline
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

#### Scenario: Reporter on own draft
- **WHEN** a reporter of `Sport` fetches their own draft in `Sport`
- **THEN** `allowedActions` is `["EDIT", "DELETE"]`

#### Scenario: Section editor on a published article of their section
- **WHEN** a section editor of `Sport` fetches the publisher's published article in `Sport`
- **THEN** `allowedActions` is `["TAKE_OFFLINE"]`

### Requirement: Listing and reading articles
`GET /api/articles` SHALL return summaries of the articles visible to the requesting user, newest
change first, optionally filtered by `status` and by `mine=true` (only articles the requesting
user authored). An unknown `status` value SHALL be answered with `400`. `GET /api/articles/{id}`
SHALL return the article with the content of its latest revision; an unknown id or an article
not visible to the requesting user SHALL be answered with `404`.

#### Scenario: Filter own drafts
- **WHEN** the publisher and an editor-in-chief each have a draft and the editor-in-chief calls `GET /api/articles?status=DRAFT&mine=true`
- **THEN** only the editor-in-chief's draft is returned

#### Scenario: Unknown article
- **WHEN** a writer calls `GET /api/articles/999999`
- **THEN** the response is `404`

#### Scenario: Reporter reads another reporter's article
- **WHEN** `reader` and `nogroups` are both `REPORTER` in `Sport` and `reader` calls `GET /api/articles/{id}` on a draft of `nogroups`
- **THEN** the response is `404`

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

## ADDED Requirements

### Requirement: Article visibility
Publishers and editors-in-chief SHALL see every article. Any other writer SHALL see the articles
they authored and, in every section where they are `SECTION_EDITOR`, all articles of that section.
Every endpoint under `/api/articles/{id}` (reading, revisions, saving, deleting, publishing,
taking offline) SHALL answer `404` for an article the requesting user does not see, without
revealing whether it exists.

#### Scenario: Section editor lists their section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `REPORTER` in `Sport` and `Kultur`, `reader` has one draft in `Sport` and one in `Kultur`, and `nogroups` calls `GET /api/articles`
- **THEN** the response contains `reader`'s `Sport` draft and not the `Kultur` draft

#### Scenario: Reporter lists only own articles
- **WHEN** `reader` is `REPORTER` in `Sport` where the publisher has a published article, and `reader` calls `GET /api/articles`
- **THEN** the response contains only articles authored by `reader`

#### Scenario: Revisions of an invisible article
- **WHEN** a reporter calls `GET /api/articles/{id}/revisions` on the publisher's article
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
colour) when no section exists at all or when articles without a section exist, and SHALL file
every article without a section under it. `POST /api/articles` without `sectionId` SHALL file the
article under the default section when it exists and the requesting user may write there,
otherwise under the first section by position the user may write in. When no section exists at
all, the default section SHALL be created and used.

#### Scenario: Fresh installation
- **WHEN** the backend starts with no section and `section.default` resolving to `General`
- **THEN** a section `General` exists afterwards

#### Scenario: Existing articles are filed
- **WHEN** articles exist that were created before articles had sections and the backend starts
- **THEN** every such article belongs to the default section

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
