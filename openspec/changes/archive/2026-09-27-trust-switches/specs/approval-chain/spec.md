## MODIFIED Requirements

### Requirement: The chain of an article
The chain of an article SHALL consist of the levels above the author's level that are staffed and
do not trust the author. A level SHALL be staffed when at least one account other than the author
holds its role: `SECTION_EDITOR` of the article's section for the section-editor level, the
newspaper-wide `EDITOR_IN_CHIEF` or `PUBLISHER` role for the other levels. Locked accounts SHALL
count as holders. A level SHALL trust the author when a trust entry exists for the author at that
level (see accounts): for `SECTION_EDITOR` an entry for the article's section, for
`EDITOR_IN_CHIEF` and `PUBLISHER` the newspaper-wide entry; it does not matter who set it or
whether that person still holds the level's role. A level that is not staffed or trusts the author
SHALL be skipped. While the article is locked by the emergency brake (see articles), the
`PUBLISHER` level SHALL belong to the chain of every author below `PUBLISHER`, whether it is
staffed or not and whether it trusts the author or not.

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

### Requirement: Approving an article
`POST /api/articles/{id}/approve` SHALL be allowed for a user other than the author when a
submission is pending and the user's approval level is at least the pending level. A user's
approval level for an article SHALL be `PUBLISHER` if they hold `PUBLISHER`, else
`EDITOR_IN_CHIEF` if they hold `EDITOR_IN_CHIEF`, else `SECTION_EDITOR` if they are
`SECTION_EDITOR` of the article's section; other users have none. An approval SHALL settle every
level up to and including the approver's approval level. The article SHALL then wait for the
lowest level of its chain above the approver's approval level, determined at that moment
(staffing, trust and lock), or, when none remains, SHALL be published: the latest revision becomes
live, the status becomes `PUBLISHED`, `pendingLevel` becomes `null`, the lock ends, and the
revision and first publication timestamps are set as for publishing. Users not allowed SHALL
receive `403`; an article without a pending submission SHALL be answered with `409`.

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

### Requirement: The server decides, clients render
Whether a user may submit, publish, approve, reject or withdraw SHALL be reported only through
`allowedActions`; the chain SHALL be computed by the server for each response from the current
roles, section roles, staffing and trust. The `SUBMIT` action SHALL NOT be offered when the chain
is empty, and `PUBLISH` SHALL NOT be offered when it is not.

#### Scenario: Solo newspaper
- **WHEN** the only account is the bootstrap publisher, who fetches their draft
- **THEN** `allowedActions` contains `PUBLISH` and not `SUBMIT`

#### Scenario: Staffing changes
- **WHEN** `reader`, `REPORTER` in `Kultur`, fetches their draft while `Kultur` has no section editor, and then `nogroups` becomes `SECTION_EDITOR` in `Kultur` and `reader` submits
- **THEN** the article waits for `SECTION_EDITOR`

#### Scenario: Trust changes
- **WHEN** `chief`, not a publisher, fetches their draft and gets `SUBMIT`, then the publisher sets trust for `chief` and `chief` fetches the draft again
- **THEN** `allowedActions` contains `PUBLISH` and not `SUBMIT`

## ADDED Requirements

### Requirement: Trust does not move pending submissions
Setting or clearing trust SHALL NOT change the `pendingLevel` or status of any article. An article
that waits for a level when that level starts to trust its author SHALL keep waiting for it and can
be approved, rejected or withdrawn as before; trust takes effect the next time the chain is
computed (submit, the publish check, an approval).

#### Scenario: Pending article keeps waiting
- **WHEN** `chief`'s article waits for `PUBLISHER` and the publisher then sets trust for `chief`
- **THEN** the article still waits for `PUBLISHER` and the publisher can approve it

#### Scenario: Next submission uses trust
- **WHEN** after that `chief` withdraws the submission and fetches the article
- **THEN** `allowedActions` contains `PUBLISH` and not `SUBMIT`
