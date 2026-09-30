## ADDED Requirements

### Requirement: Contributors of an article
The contributors of an article SHALL be the distinct authors of its revisions numbered above its
live revision, or of all its revisions when it was never published. When that set is empty (an
offline article without unpublished changes), the article's author SHALL be its only contributor.
A contributor's level SHALL be determined like the author's level, from that person's current
roles: `PUBLISHER`, else `EDITOR_IN_CHIEF`, else `SECTION_EDITOR` of the article's section, else
`REPORTER`.

#### Scenario: Only the author wrote
- **WHEN** `reader` wrote every revision of their never-published article
- **THEN** the contributors are `reader`

#### Scenario: A correction adds a contributor
- **WHEN** `reader`'s never-published article in `Sport` has revision `1` by `reader` and revision `2` by `nogroups`, the section editor of `Sport`
- **THEN** the contributors are `reader` and `nogroups`

#### Scenario: Published revisions do not count
- **WHEN** revision `2` by `chief` is live and revision `3` by `reader` is not
- **THEN** the contributors are `reader`

#### Scenario: Offline without changes
- **WHEN** `reader`'s article is offline and its latest revision is its live revision
- **THEN** the contributors are `reader`

## MODIFIED Requirements

### Requirement: The chain of an article
The chain of an article SHALL be the union of the chains of its contributors. The chain of one
contributor SHALL consist of the levels above that contributor's level that are staffed and do not
trust that contributor. A level SHALL be staffed when at least one account other than that
contributor holds its role: `SECTION_EDITOR` of the article's section for the section-editor
level, the newspaper-wide `EDITOR_IN_CHIEF` or `PUBLISHER` role for the other levels. Locked
accounts SHALL count as holders. A level SHALL trust a contributor when a trust entry exists for
that contributor at that level (see accounts): for `SECTION_EDITOR` an entry for the article's
section, for `EDITOR_IN_CHIEF` and `PUBLISHER` the newspaper-wide entry. It SHALL NOT matter who
set the entry or whether that person still holds the level's role. A level that is not staffed or
that trusts the contributor SHALL be skipped for that contributor, so trust in the article's
author SHALL NOT skip a level that another contributor's chain contains. While the article is
locked by the emergency brake (see articles), the `PUBLISHER` level SHALL belong to the chain
whenever a contributor is below `PUBLISHER`, whether it is staffed or not and whether it trusts
them or not.

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

#### Scenario: Trusted editor-in-chief publishes directly
- **WHEN** `chief` holds `EDITOR_IN_CHIEF` but not `PUBLISHER`, the publisher level trusts `chief`, and `chief` fetches their draft
- **THEN** the chain is empty and `allowedActions` contains `PUBLISH` and not `SUBMIT`

#### Scenario: Trusted section-editor level is skipped
- **WHEN** `reader` is `REPORTER` in `Sport`, `nogroups` is `SECTION_EDITOR` in `Sport`, the section-editor level of `Sport` trusts `reader`, and `reader` submits an article in `Sport`
- **THEN** the article waits for `EDITOR_IN_CHIEF`

#### Scenario: Section trust applies to its section only
- **WHEN** `reader` is `REPORTER` in `Sport` and `Kultur`, `nogroups` is `SECTION_EDITOR` in both, the section-editor level of `Sport` trusts `reader`, and `reader` submits an article in `Kultur`
- **THEN** the article waits for `SECTION_EDITOR`

#### Scenario: Fully trusted reporter
- **WHEN** `reader` is `REPORTER` in `Sport` and the section-editor level of `Sport`, the editor-in-chief level and the publisher level all trust `reader`
- **THEN** the chain of `reader`'s article in `Sport` is empty and `reader` can publish it directly

#### Scenario: Trust outlives its setter's role
- **WHEN** `chief` set trust for `reader` at the editor-in-chief level and afterwards loses `EDITOR_IN_CHIEF`, while another account still holds it
- **THEN** the editor-in-chief level is still skipped for `reader`

#### Scenario: Locked article with an unstaffed publisher level
- **WHEN** an article of an editor-in-chief is locked and no account other than its author holds `PUBLISHER`
- **THEN** the chain of the article is `PUBLISHER`

#### Scenario: Lock overrides trust
- **WHEN** the publisher level trusts `chief` and the publisher took `chief`'s article offline, so it is locked
- **THEN** the chain of the article is `PUBLISHER` and `chief` cannot publish it directly

#### Scenario: Locked article of a publisher
- **WHEN** an article of the publisher is locked
- **THEN** its chain is empty and the publisher can publish it directly

#### Scenario: Trust in the author does not skip the corrector's levels
- **WHEN** the editor-in-chief and publisher levels trust `reader`, the section-editor level of `Sport` does not, `nogroups` (`SECTION_EDITOR` in `Sport`) corrected `reader`'s pending article in `Sport`, and neither level trusts `nogroups`
- **THEN** the chain of the article is `SECTION_EDITOR`, `EDITOR_IN_CHIEF`, `PUBLISHER`

#### Scenario: Publisher's correction of a published article
- **WHEN** the publisher corrected `reader`'s published article, and the only revision above the live one is the publisher's
- **THEN** the chain is empty and the publisher can publish the correction directly

### Requirement: Submitting an article
`POST /api/articles/{id}/submit` SHALL start a submission when the requesting user is the author
and may write in the article's section, or is a contributor who may currently correct the article
(see articles). Further conditions: no submission is pending, the article is `DRAFT`, `OFFLINE`,
or `PUBLISHED` with unpublished changes, and its chain is not empty. The article SHALL then wait
for the lowest level of its chain (`pendingLevel`). A never-published article SHALL change to
status `SUBMITTED`; a `PUBLISHED` or `OFFLINE` article SHALL keep its status and live revision.
Submitting SHALL require a non-empty headline (`400` naming `headline` otherwise). A user who is
neither such an author nor such a contributor, or whose article's chain is empty, SHALL receive
`403`. A pending submission, or a `PUBLISHED` article without unpublished changes, SHALL be
answered with `409`.

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

#### Scenario: Section editor submits their correction
- **WHEN** `nogroups`, `SECTION_EDITOR` in `Sport`, corrected `reader`'s published article in `Sport`, is its only contributor, and no level trusts `nogroups`
- **THEN** `nogroups` may submit, and the article waits for `EDITOR_IN_CHIEF`

#### Scenario: Corrector who is not a contributor
- **WHEN** `reader` edited their published article in `Sport` and `nogroups`, `SECTION_EDITOR` in `Sport`, who changed nothing, submits it
- **THEN** the response is `403`

### Requirement: Content is frozen while a submission is pending
While a submission is pending, the article's author SHALL NOT change its content, and nobody SHALL
change its section. A save by the author SHALL be answered with `409`, and `EDIT` SHALL NOT be
listed for them. A correction by a user who may correct the article (see articles) SHALL remain
possible and SHALL NOT change the pending level. The revision under review SHALL always be the
article's latest revision.

#### Scenario: Save during review
- **WHEN** the author saves an article that waits for `SECTION_EDITOR`
- **THEN** the response is `409` and the latest revision is unchanged

#### Scenario: Correction during review
- **WHEN** `nogroups`, `SECTION_EDITOR` in `Sport`, corrects `reader`'s article in `Sport` that waits for `SECTION_EDITOR`
- **THEN** the response is `200`, a new latest revision by `nogroups` exists, and the article still waits for `SECTION_EDITOR`

### Requirement: Approving an article
`POST /api/articles/{id}/approve` SHALL be allowed for a user other than the author when a
submission is pending and the user's approval level is at least the pending level. A user's
approval level for an article SHALL be `PUBLISHER` if they hold `PUBLISHER`, else
`EDITOR_IN_CHIEF` if they hold `EDITOR_IN_CHIEF`, else `SECTION_EDITOR` if they are
`SECTION_EDITOR` of the article's section; other users have none. The request MAY carry a JSON body
`{"version": <n>}` with the article version the approver saw. A version other than the stored
one SHALL be answered with `409`, and nothing SHALL change. An approval SHALL settle every level up to and including the
approver's approval level. The article SHALL then wait for the lowest level of its chain (over all
contributors) above the approver's approval level, determined at that moment (staffing, trust and
lock), or, when none remains, SHALL be published: the latest revision becomes live, the status
becomes `PUBLISHED`, `pendingLevel` becomes `null`, the lock ends, and the revision and first
publication timestamps are set as for publishing. Users not allowed SHALL receive `403`; an
article without a pending submission SHALL be answered with `409`.

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

#### Scenario: Trusted levels above are skipped after an approval
- **WHEN** a reporter's article in `Sport` waits for `SECTION_EDITOR`, the editor-in-chief level trusts the reporter, and `nogroups`, `SECTION_EDITOR` in `Sport`, approves it
- **THEN** the response is `200` with `pendingLevel` `PUBLISHER`

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

#### Scenario: Locked article back online
- **WHEN** the publisher took a reporter's article in `Sport` offline, the reporter submits it, `nogroups` as `SECTION_EDITOR` and `chief` approve it, and then the publisher approves it
- **THEN** after `chief`'s approval it waits for `PUBLISHER` and is still `OFFLINE`, and after the publisher's approval it is `PUBLISHED` with `locked` `false`

#### Scenario: Corrector approves after correcting
- **WHEN** `reader`'s article in `Sport` waits for `SECTION_EDITOR`, the editor-in-chief level trusts `reader` but not `nogroups`, and `nogroups` corrects and then approves it
- **THEN** the response is `200` with `pendingLevel` `EDITOR_IN_CHIEF`

#### Scenario: Stale approval
- **WHEN** the approver loaded the article at version `5`, a correction raised it to `6`, and the approver approves with `{"version": 5}`
- **THEN** the response is `409` and the article still waits for the same level

### Requirement: Rejecting an article
`POST /api/articles/{id}/reject` SHALL be allowed to the same users as approving and SHALL require
a JSON body `{"note": "..."}`, which MAY also carry `version`, the article version the reviewer
saw. A version other than the stored one SHALL be answered with `409`, and nothing SHALL change. The note SHALL be stored trimmed, SHALL be 1 to 1000
characters long, MAY contain line feeds and SHALL NOT contain other control characters; otherwise
the response SHALL be `400` naming `note`. Rejecting SHALL end the submission (`pendingLevel`
`null`): a `SUBMITTED` article SHALL return to `DRAFT`, a `PUBLISHED` or `OFFLINE` article SHALL
keep its status and live revision. Revisions SHALL NOT change.

#### Scenario: Reject a draft
- **WHEN** `nogroups` rejects a reporter's submitted article in `Sport` with the note `Please add who scored.`
- **THEN** the response is `200` with `status` `DRAFT`, `pendingLevel` `null`, and the author may edit it again

#### Scenario: Reject changes to a published article
- **WHEN** the publisher rejects the pending changes of `chief`'s published article
- **THEN** `status` stays `PUBLISHED`, `liveRevision` is unchanged and `hasUnpublishedChanges` is `true`

#### Scenario: Reject without note
- **WHEN** an approver rejects with `{"note": "   "}`
- **THEN** the response is `400` naming `note` and the article still waits

#### Scenario: Stale rejection
- **WHEN** the reviewer loaded the article at version `5`, a correction raised it to `6`, and the reviewer rejects with `{"note": "Too short", "version": 5}`
- **THEN** the response is `409` and the article still waits
