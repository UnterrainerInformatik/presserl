## MODIFIED Requirements

### Requirement: Listing and reading articles
`GET /api/articles` SHALL return summaries of the articles visible to the requesting user, newest
change first, optionally filtered by `status`, by `mine=true` (only articles the requesting
user authored), by `pending=true` (only articles with a pending submission) and by
`awaitingMe=true` (only articles whose `allowedActions` for the requesting user contain
`APPROVE`, i.e. articles waiting for a decision the user may take now); filters combine.
An unknown `status` value SHALL be answered with `400`. `GET /api/articles/{id}` SHALL return the
article with the content of its latest revision; an unknown id or an article not visible to the
requesting user SHALL be answered with `404`.

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
