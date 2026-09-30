## ADDED Requirements

### Requirement: Articles state whether readers see them
Article representations and article summaries SHALL carry `readerVisible`: `true` exactly when the
article's status is `PUBLISHED` and it belongs to an issue that is published, otherwise `false`.
The value SHALL be computed at request time, so switching an issue live or back changes it for all
its articles at once.

#### Scenario: Published in a live issue
- **WHEN** a published article belongs to the published issue 1 and its author calls `GET /api/articles/{id}`
- **THEN** `readerVisible` is `true`

#### Scenario: Published in a planned issue
- **WHEN** a published article belongs to issue 2, which is not published
- **THEN** `readerVisible` is `false` and `status` is `PUBLISHED`

#### Scenario: Draft
- **WHEN** a writer fetches a draft
- **THEN** `readerVisible` is `false`

### Requirement: Front-page weight
An article SHALL carry an optional front-page weight, an integer from 1 to 999 or none.
Article and article summary representations SHALL carry it as `frontPageWeight` (a number or
`null`). `PUT /api/articles/{id}/front-page-weight` with the body `{"weight": <number or null>}`
SHALL set or clear it and answer `200` with the article representation. Only users holding
`EDITOR_IN_CHIEF` or `PUBLISHER` SHALL be allowed; any other user SHALL receive `403`, and an unknown
article `404`. A weight outside 1–999 or a non-integer SHALL be answered with `400` naming the field
`weight`. Setting the weight SHALL be possible in every status, SHALL NOT create a revision, SHALL
NOT touch the approval state or the lock, and SHALL NOT change the article's version used for
optimistic concurrency of content saves. The weight SHALL be kept when the article goes offline,
comes back online, or changes its section.

#### Scenario: Editor-in-chief weights a published article
- **WHEN** `chief` sends `PUT /api/articles/{id}/front-page-weight` with `{"weight": 1}` for a reporter's published article
- **THEN** the response is `200` with `frontPageWeight` `1`, `status` `PUBLISHED` and the same live revision

#### Scenario: Clearing the weight
- **WHEN** the publisher sends `{"weight": null}` for an article with weight 2
- **THEN** the response is `200` with `frontPageWeight` `null`

#### Scenario: Section editor may not weight
- **WHEN** a section editor of `Sport` sends a weight for an article in `Sport`
- **THEN** the response is `403` and the weight is unchanged

#### Scenario: Invalid weight
- **WHEN** `chief` sends `{"weight": 0}`
- **THEN** the response is `400` with a field error for `weight`

#### Scenario: Weight survives going offline
- **WHEN** an article with weight 1 is taken offline and later published again
- **THEN** its `frontPageWeight` is still `1`

#### Scenario: New articles have no weight
- **WHEN** a writer creates an article
- **THEN** its `frontPageWeight` is `null`
