## MODIFIED Requirements

### Requirement: The chain of an article
The chain of an article SHALL consist of the levels above the author's level that are staffed. A
level SHALL be staffed when at least one account other than the author holds its role:
`SECTION_EDITOR` of the article's section for the section-editor level, the newspaper-wide
`EDITOR_IN_CHIEF` or `PUBLISHER` role for the other levels. Locked accounts SHALL count as
holders. A level that is not staffed SHALL be skipped. While the article is locked by the
emergency brake (see articles), the `PUBLISHER` level SHALL belong to the chain of every author
below `PUBLISHER`, whether it is staffed or not.

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

#### Scenario: Locked article with an unstaffed publisher level
- **WHEN** an article of an editor-in-chief is locked and no account other than its author holds `PUBLISHER`
- **THEN** the chain of the article is `PUBLISHER`

#### Scenario: Locked article of a publisher
- **WHEN** an article of the publisher is locked
- **THEN** its chain is empty and the publisher can publish it directly

### Requirement: Approving an article
`POST /api/articles/{id}/approve` SHALL be allowed for a user other than the author when a
submission is pending and the user's approval level is at least the pending level. A user's
approval level for an article SHALL be `PUBLISHER` if they hold `PUBLISHER`, else
`EDITOR_IN_CHIEF` if they hold `EDITOR_IN_CHIEF`, else `SECTION_EDITOR` if they are
`SECTION_EDITOR` of the article's section; other users have none. An approval SHALL settle every
level up to and including the approver's approval level. The article SHALL then wait for the
lowest level of its chain above the approver's approval level, determined at that moment
(staffing and lock), or, when none remains, SHALL be published: the latest revision becomes live,
the status becomes `PUBLISHED`, `pendingLevel` becomes `null`, the lock ends, and the revision and
first publication timestamps are set as for publishing. Users not allowed SHALL receive `403`; an
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
