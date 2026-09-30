## ADDED Requirements

### Requirement: Corrections by higher levels
A user other than the author SHALL be allowed to correct (save) an article when all of these hold:

- the newspaper setting `article.corrections` is `true`;
- the user's approval level for the article (see approval-chain) is above the author's level,
  determined from the author's current roles;
- the article is not `DRAFT`;
- while a submission is pending, the user's approval level is at least the pending level.

A correction SHALL follow the save rules (validation, version, new-revision rule) and SHALL keep
the article's section: a save by a corrector naming another `sectionId` SHALL be answered with
`403` naming `sectionId`, and nothing SHALL change. A correction SHALL NOT change the author, the
status, the pending level or the live revision. `EDIT` SHALL be listed in `allowedActions` for
exactly the users who may save the article. Other non-authors SHALL receive `403` on save. A
corrector whose level is too low for the pending level SHALL receive `403`, and a correction of a
draft SHALL be answered with `409`.

#### Scenario: Section editor corrects a pending article
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and `reader`'s article in `Sport` waits for `SECTION_EDITOR`
- **THEN** `nogroups` gets `allowedActions` `["EDIT", "APPROVE", "REJECT"]`, and a save by `nogroups` answers `200` with a new revision authored by `nogroups`

#### Scenario: Editor-in-chief corrects a published article
- **WHEN** `chief` saves a changed version of `reader`'s published article
- **THEN** the response is `200`, a new revision by `chief` exists, `liveRevision` is unchanged and `hasUnpublishedChanges` is `true`

#### Scenario: Drafts stay the author's
- **WHEN** `chief` saves `reader`'s never-submitted draft
- **THEN** the response is `409` and the draft is unchanged

#### Scenario: Pending level above the corrector
- **WHEN** `reader`'s article in `Sport` waits for `PUBLISHER` and `nogroups`, `SECTION_EDITOR` in `Sport`, saves it
- **THEN** the response is `403`

#### Scenario: Equal level may not correct
- **WHEN** `chief` saves a published article of another editor-in-chief
- **THEN** the response is `403`

#### Scenario: Corrections switched off
- **WHEN** the newspaper sets `article.corrections` to `false` and the publisher saves `reader`'s published article
- **THEN** the response is `403` and `EDIT` is not listed for the publisher

#### Scenario: Corrector may not move the article
- **WHEN** `chief` corrects `reader`'s published article in `Sport` and sends the `sectionId` of `Kultur`
- **THEN** the response is `403` naming `sectionId` and the article stays in `Sport` unchanged

### Requirement: Revisions record their author
Every revision SHALL record its author (token subject, username and display name at the time of
writing). Revisions that existed before this requirement SHALL be attributed to the article's
author. Article representations SHALL carry `lastEditor`, the author of the latest revision (same
shape as `author`), and every entry of the revision history and every revision SHALL carry its
`author`.

#### Scenario: Author wrote the latest revision
- **WHEN** `reader` fetches their own draft
- **THEN** `lastEditor` equals `author`

#### Scenario: Corrected article
- **WHEN** `chief` corrected `reader`'s published article and `reader` fetches it
- **THEN** `author` is `reader` and `lastEditor` is `chief`

## MODIFIED Requirements

### Requirement: Working revision and live revision
Each article SHALL have numbered revisions starting at `1`. Saving an article (`PUT`) SHALL
overwrite its latest revision while that revision has never been published and was written by the
saving user. In every other case, saving SHALL create a new revision numbered one higher. There is
one exception: when the saved content (kicker, headline, subheadline, lead, body and lead image
with caption) equals the latest revision's content, no revision SHALL be created or changed.
Publishing SHALL make the latest revision the article's live revision and mark it as published.
The live revision SHALL remain unchanged by later saves until the next publish.

#### Scenario: Autosave on a draft
- **WHEN** the author saves a draft article three times
- **THEN** the article still has only revision `1`, containing the last saved content

#### Scenario: Editing a published article
- **WHEN** the author saves a published article whose live revision is `1`
- **THEN** a revision `2` with the new content exists, `liveRevision` stays `1` and `hasUnpublishedChanges` is `true`

#### Scenario: Saving a published article unchanged
- **WHEN** the author saves a published article whose live revision is `1` with exactly its current content
- **THEN** the article still has only revision `1` and `hasUnpublishedChanges` is `false`

#### Scenario: Changing only the caption of a published article
- **WHEN** the author saves a published article whose live revision is `1` with only the lead image's caption changed
- **THEN** a revision `2` exists with the new caption, `liveRevision` stays `1` and `hasUnpublishedChanges` is `true`

#### Scenario: Republishing
- **WHEN** the publisher-author publishes that article again
- **THEN** `liveRevision` is `2` and `hasUnpublishedChanges` is `false`

#### Scenario: Correction of an unpublished revision
- **WHEN** `reader`'s never-published article waits for `SECTION_EDITOR` with revision `1` by `reader`, and `nogroups`, `SECTION_EDITOR` of its section, saves it twice
- **THEN** revision `1` is unchanged and revision `2`, authored by `nogroups`, holds the last saved content

### Requirement: Only the author edits and deletes
Only the article's author SHALL delete it, and only while they may write in the article's section.
The author SHALL save (`PUT`) it only while they may write in the article's section and no
submission is pending. Other users SHALL save it only as a correction (see "Corrections by higher
levels"). An article SHALL be deletable only while it has never been published. Deleting SHALL
remove the article, its revisions and its reviews, also while a submission is pending. Violations
SHALL be answered with `403` (not the author and no correction right, or no write access to the
article's section) or `409` (already published once, a submission is pending on the author's
save, or a correction of a draft).

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

#### Scenario: Corrector may not delete
- **WHEN** `chief` deletes `reader`'s never-published article that waits for `EDITOR_IN_CHIEF`
- **THEN** the response is `403`

### Requirement: Server-computed allowed actions
Every article representation SHALL carry `allowedActions`, the subset of `EDIT`, `SUBMIT`,
`PUBLISH`, `WITHDRAW`, `APPROVE`, `REJECT`, `TAKE_OFFLINE`, `UNLOCK` and `DELETE` (in this order)
that the requesting user may perform on the article in its current state under the rules above and
those of the approval chain. Clients SHALL be able to rely on an action being accepted exactly when
it is listed. Content validation, a missing headline, a missing note and concurrency are the only
exceptions.

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
- **THEN** `allowedActions` is `["EDIT", "APPROVE", "REJECT"]`

#### Scenario: Section editor on a submitted article with corrections switched off
- **WHEN** `article.corrections` is `false` and a section editor of `Sport` fetches a reporter's article in `Sport` that waits for `SECTION_EDITOR`
- **THEN** `allowedActions` is `["APPROVE", "REJECT"]`

#### Scenario: Section editor on a published article of their section
- **WHEN** a section editor of `Sport` fetches the publisher's published article in `Sport`
- **THEN** `allowedActions` is `["TAKE_OFFLINE"]`

#### Scenario: Publisher on a reporter's published article
- **WHEN** the publisher fetches a reporter's published article without unpublished changes
- **THEN** `allowedActions` is `["EDIT", "TAKE_OFFLINE"]`

#### Scenario: Publisher on their correction
- **WHEN** the publisher corrected a reporter's published article and is the only contributor
- **THEN** `allowedActions` is `["EDIT", "PUBLISH", "TAKE_OFFLINE"]`

#### Scenario: Publisher on a locked article
- **WHEN** the publisher fetches a locked article of `chief` without a pending submission or unpublished changes
- **THEN** `allowedActions` is `["EDIT", "UNLOCK"]`

#### Scenario: Author on their locked article
- **WHEN** `chief` fetches their locked article without a pending submission
- **THEN** `allowedActions` is `["EDIT", "SUBMIT"]`

### Requirement: Listing and reading articles
`GET /api/articles` SHALL return summaries of the articles visible to the requesting user. It
SHALL accept these optional filters, which combine:

- `status`;
- `mine=true`: only articles the requesting user authored;
- `pending=true`: only articles with a pending submission;
- `awaitingMe=true`: only articles whose `allowedActions` for the requesting user contain
  `APPROVE`, i.e. articles waiting for a decision the user may take now.

The order SHALL follow `sort`: `changed` (default) is newest change first, `newest` is newest
creation first, and `section` is section position, then newest change first; ties SHALL be broken
by descending id. Every summary SHALL carry `createdAt` in addition to its other fields. An unknown
`status` or `sort` value SHALL be answered with `400` naming the parameter.

`GET /api/articles/{id}` SHALL return the article with the content of its latest revision. An
unknown id, or an article not visible to the requesting user, SHALL be answered with `404`.

#### Scenario: Filter own drafts
- **WHEN** the publisher and an editor-in-chief each have a draft and the editor-in-chief calls `GET /api/articles?status=DRAFT&mine=true`
- **THEN** only the editor-in-chief's draft is returned

#### Scenario: Filter pending articles
- **WHEN** one draft waits for `SECTION_EDITOR`, a published article has changes waiting for `PUBLISHER`, another draft is not submitted, and the publisher calls `GET /api/articles?pending=true`
- **THEN** exactly the two waiting articles are returned

#### Scenario: Articles awaiting the section editor
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, a reporter's article in `Sport` waits for `SECTION_EDITOR`, another article waits for `PUBLISHER`, and `nogroups` calls `GET /api/articles?awaitingMe=true`
- **THEN** exactly the article waiting for `SECTION_EDITOR` is returned

#### Scenario: Own submission is not awaiting the author
- **WHEN** `chief` holds `EDITOR_IN_CHIEF`, has submitted an article that waits for `PUBLISHER`, a reporter's article waits for `EDITOR_IN_CHIEF`, and `chief` calls `GET /api/articles?awaitingMe=true`
- **THEN** exactly the reporter's article is returned

#### Scenario: Higher level sees lower pending levels
- **WHEN** one article waits for `SECTION_EDITOR`, another for `PUBLISHER`, a third is a draft that was never submitted, and the publisher calls `GET /api/articles?awaitingMe=true`
- **THEN** exactly the two waiting articles are returned

#### Scenario: Reporter awaits nothing
- **WHEN** `reader` is `REPORTER` in `Sport` and calls `GET /api/articles?awaitingMe=true`
- **THEN** the response is `200` with an empty list

#### Scenario: Solo newspaper awaits nothing
- **WHEN** the bootstrap publisher is the only account and calls `GET /api/articles?awaitingMe=true`
- **THEN** the response is `200` with an empty list

#### Scenario: Unknown article
- **WHEN** a writer calls `GET /api/articles/999999`
- **THEN** the response is `404`

#### Scenario: Reporter reads another reporter's article
- **WHEN** `reader` and `nogroups` are both `REPORTER` in `Sport` and `reader` calls `GET /api/articles/{id}` on a draft of `nogroups`
- **THEN** the response is `404`

#### Scenario: Newest first
- **WHEN** article A was created before article B, A was changed after B, and the publisher calls `GET /api/articles?sort=newest`
- **THEN** B is listed before A, and each summary carries `createdAt`

#### Scenario: By section
- **WHEN** sections `Sport` and `Kultur` exist in this order, each has two articles, and the publisher calls `GET /api/articles?sort=section`
- **THEN** both `Sport` articles come first, newest change first, then both `Kultur` articles

#### Scenario: Unknown sort
- **WHEN** a writer calls `GET /api/articles?sort=title`
- **THEN** the response is `400` naming `sort`

### Requirement: Revision history
`GET /api/articles/{id}/revisions` SHALL list the article's revisions newest first. Each entry
SHALL carry the number, the created and updated timestamps, the publication timestamp or `null`,
whether it is live, the headline and the author. `GET /api/articles/{id}/revisions/{number}` SHALL
return the content and author of that revision. An unknown number SHALL be answered with `404`.

#### Scenario: History after republishing
- **WHEN** an article was published, edited and published again
- **THEN** the history lists revisions `2` (live) and `1` (published, not live)

#### Scenario: Corrector in the history
- **WHEN** `chief` corrected `reader`'s published article, creating revision `2`
- **THEN** the history lists revision `2` with author `chief` and revision `1` with author `reader`
