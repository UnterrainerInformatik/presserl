## ADDED Requirements

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

## MODIFIED Requirements

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
