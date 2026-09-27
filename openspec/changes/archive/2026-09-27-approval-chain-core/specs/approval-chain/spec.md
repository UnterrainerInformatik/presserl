## Purpose

Decides which approval levels an article must pass before it goes online, and lets authors submit
and withdraw and approvers approve or reject, so that a newsroom of several people publishes only
what the responsible roles have checked.

## ADDED Requirements

### Requirement: Approval levels and the author's level
The approval levels SHALL be, bottom to top, `SECTION_EDITOR` (of the article's section),
`EDITOR_IN_CHIEF` and `PUBLISHER`. The author's level for an article SHALL be the highest of:
`PUBLISHER` if the author holds `PUBLISHER`, `EDITOR_IN_CHIEF` if they hold `EDITOR_IN_CHIEF`,
`SECTION_EDITOR` if they are `SECTION_EDITOR` of the article's section, otherwise `REPORTER`
(below all levels). The author's level SHALL be determined from the author's roles at the moment
they submit.

#### Scenario: Reporter's level
- **WHEN** `reader` is `REPORTER` in `Sport`, holds no newspaper-wide writer role and submits an article in `Sport`
- **THEN** their level is `REPORTER` and the levels considered are `SECTION_EDITOR`, `EDITOR_IN_CHIEF` and `PUBLISHER`

#### Scenario: Section editor writing in a foreign section
- **WHEN** a user is `SECTION_EDITOR` in `Sport` and `REPORTER` in `Kultur` and submits an article in `Kultur`
- **THEN** their level is `REPORTER` and the `SECTION_EDITOR` level of `Kultur` is considered

### Requirement: The chain of an article
The chain of an article SHALL consist of the levels above the author's level that are staffed. A
level SHALL be staffed when at least one account other than the author holds its role:
`SECTION_EDITOR` of the article's section for the section-editor level, the newspaper-wide
`EDITOR_IN_CHIEF` or `PUBLISHER` role for the other levels. Locked accounts SHALL count as
holders. A level that is not staffed SHALL be skipped.

#### Scenario: Section without section editor
- **WHEN** `reader` is `REPORTER` in `Kultur`, nobody is `SECTION_EDITOR` in `Kultur`, and `reader` submits an article in `Kultur`
- **THEN** the article waits for `EDITOR_IN_CHIEF`

#### Scenario: No other editor-in-chief
- **WHEN** a reporter submits an article and no account other than the reporter holds `EDITOR_IN_CHIEF`, while the section has no section editor
- **THEN** the article waits for `PUBLISHER`

#### Scenario: Publisher's chain is empty
- **WHEN** the publisher, who holds all roles, writes an article
- **THEN** the chain is empty and the article can be published directly

#### Scenario: Editor-in-chief's chain
- **WHEN** `chief` holds `EDITOR_IN_CHIEF` but not `PUBLISHER` and another account holds `PUBLISHER`
- **THEN** the chain of `chief`'s article is `PUBLISHER`

### Requirement: Submitting an article
`POST /api/articles/{id}/submit` SHALL start a submission when the requesting user is the author,
may write in the article's section, no submission is pending, the article is `DRAFT`, `OFFLINE`, or
`PUBLISHED` with unpublished changes, and its chain is not empty. The article SHALL then wait for
the lowest level of its chain (`pendingLevel`). A never-published article SHALL change to status
`SUBMITTED`; a `PUBLISHED` or `OFFLINE` article SHALL keep its status and live revision. Submitting
SHALL require a non-empty headline (`400` naming `headline` otherwise). A user who is not the
author, may not write in the section, or whose chain is empty SHALL receive `403`; a pending
submission or a `PUBLISHED` article without unpublished changes SHALL be answered with `409`.

#### Scenario: Reporter submits a draft
- **WHEN** `reader` is `REPORTER` in `Sport`, `nogroups` is `SECTION_EDITOR` in `Sport`, and `reader` submits their draft in `Sport` with headline `Goal`
- **THEN** the response is `200` with `status` `SUBMITTED` and `pendingLevel` `SECTION_EDITOR`

#### Scenario: Submitting changes to a published article
- **WHEN** `chief` submits changes to their published article whose live revision is `1` and latest revision is `2`
- **THEN** the response is `200` with `status` `PUBLISHED`, `liveRevision` `1` and `pendingLevel` `PUBLISHER`, and the reader still shows revision `1`

#### Scenario: Bringing an offline article back online
- **WHEN** `chief` submits their `OFFLINE` article without unpublished changes
- **THEN** the response is `200` with `status` `OFFLINE` and `pendingLevel` `PUBLISHER`

#### Scenario: Publisher cannot submit
- **WHEN** the publisher submits their own draft
- **THEN** the response is `403` and the article stays `DRAFT` without `pendingLevel`

#### Scenario: Submit twice
- **WHEN** the author submits an article that already waits for a level
- **THEN** the response is `409`

#### Scenario: Submit without headline
- **WHEN** a reporter submits a draft with an empty headline
- **THEN** the response is `400` naming `headline` and nothing changes

### Requirement: Content is frozen while a submission is pending
While a submission is pending the article's content and section SHALL NOT change: saving SHALL be
answered with `409` and `EDIT` SHALL NOT be listed in `allowedActions`. The revision under review
SHALL always be the article's latest revision.

#### Scenario: Save during review
- **WHEN** the author saves an article that waits for `SECTION_EDITOR`
- **THEN** the response is `409` and the latest revision is unchanged

### Requirement: Approving an article
`POST /api/articles/{id}/approve` SHALL be allowed for a user other than the author when a
submission is pending and the user's approval level is at least the pending level. A user's
approval level for an article SHALL be `PUBLISHER` if they hold `PUBLISHER`, else
`EDITOR_IN_CHIEF` if they hold `EDITOR_IN_CHIEF`, else `SECTION_EDITOR` if they are
`SECTION_EDITOR` of the article's section; other users have none. An approval SHALL settle every
level up to and including the approver's approval level. The article SHALL then wait for the
lowest level of the chain above the approver's approval level that is staffed at that moment, or,
when none remains, SHALL be published: the latest revision becomes live, the status becomes
`PUBLISHED`, `pendingLevel` becomes `null`, and the revision and first publication timestamps are
set as for publishing. Users not allowed SHALL receive `403`; an article without a pending
submission SHALL be answered with `409`.

#### Scenario: Section editor approves
- **WHEN** a reporter's article in `Sport` waits for `SECTION_EDITOR` and `nogroups`, `SECTION_EDITOR` in `Sport`, approves it
- **THEN** the response is `200` with `pendingLevel` `EDITOR_IN_CHIEF`

#### Scenario: Editor-in-chief approves
- **WHEN** that article waits for `EDITOR_IN_CHIEF` and `chief` approves it
- **THEN** the response is `200` with `pendingLevel` `PUBLISHER`

#### Scenario: Publisher approves
- **WHEN** that article waits for `PUBLISHER` and the publisher approves it
- **THEN** the response is `200` with `status` `PUBLISHED`, `pendingLevel` `null`, a `liveRevision` and a `publishedAt` timestamp, and the reader shows the article

#### Scenario: Higher role approves a lower level
- **WHEN** a reporter's article waits for `SECTION_EDITOR` and the publisher approves it
- **THEN** the article is `PUBLISHED`

#### Scenario: Approved changes go live
- **WHEN** `chief`'s published article with live revision `1` waits for `PUBLISHER` with revision `2` and the publisher approves it
- **THEN** `liveRevision` is `2`, `status` is `PUBLISHED` and `hasUnpublishedChanges` is `false`

#### Scenario: Author cannot approve
- **WHEN** `chief`'s article waits for `PUBLISHER`, `chief` then receives `PUBLISHER` and approves their own article
- **THEN** the response is `403` and the article still waits for `PUBLISHER`

#### Scenario: Section editor of another section
- **WHEN** a reporter's article in `Kultur` waits for `SECTION_EDITOR` and `nogroups`, `SECTION_EDITOR` in `Sport` only, approves it
- **THEN** the response is `404` because the article is not visible to `nogroups`

#### Scenario: Level too low
- **WHEN** an article waits for `PUBLISHER` and `chief`, who is not a publisher, approves it
- **THEN** the response is `403` and the article still waits for `PUBLISHER`

#### Scenario: Nothing to approve
- **WHEN** the publisher approves a `DRAFT`
- **THEN** the response is `409`

### Requirement: Rejecting an article
`POST /api/articles/{id}/reject` SHALL be allowed to the same users as approving and SHALL require
a JSON body `{"note": "..."}`. The note SHALL be stored trimmed, SHALL be 1 to 1000 characters
long, MAY contain line feeds and SHALL NOT contain other control characters; otherwise the
response SHALL be `400` naming `note`. Rejecting SHALL end the submission (`pendingLevel` `null`):
a `SUBMITTED` article SHALL return to `DRAFT`, a `PUBLISHED` or `OFFLINE` article SHALL keep its
status and live revision. Revisions SHALL NOT change.

#### Scenario: Reject a draft
- **WHEN** `nogroups` rejects a reporter's submitted article in `Sport` with the note `Please add who scored.`
- **THEN** the response is `200` with `status` `DRAFT`, `pendingLevel` `null`, and the author may edit it again

#### Scenario: Reject changes to a published article
- **WHEN** the publisher rejects the pending changes of `chief`'s published article
- **THEN** `status` stays `PUBLISHED`, `liveRevision` is unchanged and `hasUnpublishedChanges` is `true`

#### Scenario: Reject without note
- **WHEN** an approver rejects with `{"note": "   "}`
- **THEN** the response is `400` naming `note` and the article still waits

### Requirement: Withdrawing a submission
`POST /api/articles/{id}/withdraw` SHALL let the author end their pending submission at any time,
even after losing their section role. A `SUBMITTED` article SHALL return to `DRAFT`; a `PUBLISHED`
or `OFFLINE` article SHALL keep its status. Any other user SHALL receive `403`; an article without
a pending submission SHALL be answered with `409`. Withdrawing SHALL NOT create a review entry.

#### Scenario: Author withdraws
- **WHEN** the author withdraws their article that waits for `EDITOR_IN_CHIEF`
- **THEN** the response is `200` with `status` `DRAFT` and `pendingLevel` `null`

#### Scenario: Approver cannot withdraw
- **WHEN** a section editor withdraws a reporter's submitted article
- **THEN** the response is `403`

### Requirement: Pending level in article representations
Article and article summary representations SHALL carry `pendingLevel`: the level the article
waits for (`SECTION_EDITOR`, `EDITOR_IN_CHIEF` or `PUBLISHER`), or `null` when no submission is
pending.

#### Scenario: Draft
- **WHEN** a writer fetches a draft that was never submitted
- **THEN** `pendingLevel` is `null`

### Requirement: Reviews are recorded
Every approval and rejection SHALL be recorded with the decision (`APPROVED` or `REJECTED`), the
level the article waited for, the revision number, the reviewer's username and display name, the
note (`null` for approvals) and the time. `GET /api/articles/{id}/reviews` SHALL return these
entries newest first to every writer who sees the article, and SHALL answer `404` for an article
the user does not see. Deleting an article SHALL delete its reviews.

#### Scenario: Author reads the rejection note
- **WHEN** an article was approved by `nogroups` and then rejected by `chief` with the note `Too short`, and the author calls `GET /api/articles/{id}/reviews`
- **THEN** the response lists the rejection by `chief` with the note `Too short` and level `EDITOR_IN_CHIEF`, followed by the approval by `nogroups` with level `SECTION_EDITOR`

#### Scenario: Reviews of an invisible article
- **WHEN** a reporter calls `GET /api/articles/{id}/reviews` on another reporter's article
- **THEN** the response is `404`

### Requirement: The server decides, clients render
Whether a user may submit, publish, approve, reject or withdraw SHALL be reported only through
`allowedActions`; the chain SHALL be computed by the server for each response from the current
roles, section roles and staffing. The `SUBMIT` action SHALL NOT be offered when the chain is
empty, and `PUBLISH` SHALL NOT be offered when it is not.

#### Scenario: Solo newspaper
- **WHEN** the only account is the bootstrap publisher, who fetches their draft
- **THEN** `allowedActions` contains `PUBLISH` and not `SUBMIT`

#### Scenario: Staffing changes
- **WHEN** `reader`, `REPORTER` in `Kultur`, fetches their draft while `Kultur` has no section editor, and then `nogroups` becomes `SECTION_EDITOR` in `Kultur` and `reader` submits
- **THEN** the article waits for `SECTION_EDITOR`
