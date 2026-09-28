# issues Specification

## Purpose

Groups articles into numbered issues that editors-in-chief and publishers assemble, order and
switch live, so the newspaper can run as one ever-growing issue (blog mode) or as a series of
planned issues, and so an issue can be read and printed as a whole.

## Requirements

### Requirement: Issue attributes
An issue SHALL have a `number` (a positive integer, unique), an optional `publicationDate` (a
calendar date without time, display only), a `published` switch (whether readers can see the
issue) and an ordered list of articles. An article SHALL belong to at most one issue; the first
article of an issue is its **lead story**. Articles of any status MAY belong to an issue; what
readers see of it is decided by the reader (published articles only).

#### Scenario: Article in two issues is impossible
- **WHEN** article 7 belongs to issue 1 and is put into the article list of issue 2
- **THEN** article 7 belongs to issue 2 only

### Requirement: Initial issue
After installation the newspaper SHALL have issue number 1, not published, without publication
date. On an installation that already has articles when issues are introduced, every article that
has been published at least once SHALL belong to issue 1, ordered by first publication (oldest
first, ties by article id).

#### Scenario: Fresh installation
- **WHEN** the publisher lists the issues of a freshly installed newspaper
- **THEN** there is exactly issue 1, not published, without publication date and without articles

#### Scenario: Existing articles move into issue 1
- **WHEN** issues are introduced on a newspaper where article A was first published before article B and article C was never published
- **THEN** issue 1 lists A, then B, and C belongs to no issue

### Requirement: Newest issue collects newly published articles
When an article is published for the first time (by `POST /api/articles/{id}/publish` or by the
approval that publishes it) and belongs to no issue, the system SHALL append it to the end of the
issue with the highest number, whether or not that issue is published, in the same transaction as
the publication. When no issue exists the article SHALL stay without issue. Later publications of
the same article (new revisions, back online) SHALL NOT change its issue or position.

#### Scenario: Blog mode
- **WHEN** issue 1 is the only issue and is published, and a new article is published
- **THEN** the article is the last article of issue 1

#### Scenario: Planned issue collects
- **WHEN** issue 3 is published, issue 4 exists and is not published, and a new article is published
- **THEN** the article is appended to issue 4, not to issue 3

#### Scenario: Approval appends too
- **WHEN** a reporter's article is published by the publisher's approval
- **THEN** it is appended to the newest issue

#### Scenario: Republication keeps the issue
- **WHEN** an article in issue 2 is edited and published again after issue 3 was created
- **THEN** it stays in issue 2 at its position

#### Scenario: Assigned before first publication
- **WHEN** a draft was put into issue 2 manually and is then published for the first time while issue 3 is the newest
- **THEN** it stays in issue 2

#### Scenario: No issue exists
- **WHEN** every issue has been deleted and an article is published
- **THEN** the article belongs to no issue

### Requirement: Managing issues requires MANAGE_ISSUES
Every `/api/issues` endpoint SHALL require the newspaper action `MANAGE_ISSUES`, held by users with
`PUBLISHER` or `EDITOR_IN_CHIEF`. Other authenticated users SHALL get `403`, requests without a
valid token `401`.

#### Scenario: Reporter may not list issues
- **WHEN** a user who is only `REPORTER` in `Sport` calls `GET /api/issues`
- **THEN** the response is `403`

#### Scenario: Editor-in-chief manages issues
- **WHEN** `chief` (holding `EDITOR_IN_CHIEF`) calls `GET /api/issues`
- **THEN** the response is `200`

### Requirement: List issues
`GET /api/issues` SHALL return all issues ordered by number, highest first, each as an issue
summary: `id`, `number`, `publicationDate` (ISO date or `null`), `published`, `publishedAt` (time
of the latest switch to published, `null` while not published), `articleCount` (all articles of
the issue, any status), `newest` (whether it has the highest number).

#### Scenario: Two issues
- **WHEN** issue 1 is published with 5 articles and issue 2 is not published with 2 articles
- **THEN** the list is issue 2 (`newest: true`, `published: false`, `articleCount: 2`), then issue 1 (`newest: false`, `published: true`, `articleCount: 5`)

### Requirement: Create an issue
`POST /api/issues` with body `{ "publicationDate": "2026-10-12" | null }` SHALL create an issue that
is not published, has no articles and gets the number after the highest existing one (1 when none
exists). It SHALL answer `201` with `Location: /api/issues/{id}` and the issue. From then on it is
the newest issue and collects newly published articles. An invalid date SHALL be answered with
`400` naming the field `publicationDate`; unknown fields SHALL be rejected with `400`. When a
concurrent creation took the same number, the request SHALL be answered with `409` and nothing
SHALL be created.

#### Scenario: Next number
- **WHEN** issues 1 and 2 exist and `chief` creates an issue without date
- **THEN** the response is `201` with `number: 3`, `published: false`, `publicationDate: null`

#### Scenario: Invalid date
- **WHEN** `chief` creates an issue with `publicationDate` `"2026-13-40"`
- **THEN** the response is `400` with a field error for `publicationDate` and no issue is created

### Requirement: Read an issue
`GET /api/issues/{id}` SHALL return the issue with its summary fields and `articles`: the article
summaries (as in `GET /api/articles`) in issue order. An unknown id SHALL be answered with `404`.

#### Scenario: Issue with articles in order
- **WHEN** issue 2 holds articles 9, 4, 6 in this order
- **THEN** `GET /api/issues/{id}` lists 9, 4, 6 with their headline, status and section

### Requirement: Change the publication date
`PUT /api/issues/{id}` with body `{ "publicationDate": "2026-10-12" | null }` SHALL set or clear the
publication date, for published issues as well, and answer with the issue. Validation as for
creation; unknown id `404`.

#### Scenario: Date added before publishing
- **WHEN** `chief` sets `publicationDate` of issue 4 to `2026-10-12`
- **THEN** the response shows `publicationDate: "2026-10-12"` and the number is unchanged

### Requirement: Publish and unpublish an issue
`POST /api/issues/{id}/publish` SHALL make the issue visible to readers and set `publishedAt` to
the current time; `POST /api/issues/{id}/unpublish` SHALL hide it again and clear `publishedAt`.
Both SHALL be idempotent (publishing a published issue keeps its `publishedAt`), SHALL NOT need an
approval and SHALL NOT change the status of any article. Any number of issues MAY be published at
the same time. Unknown id `404`. Each switch SHALL be logged at INFO with the issue number and the
acting user.

#### Scenario: Publish
- **WHEN** `chief` publishes issue 4
- **THEN** the response shows `published: true` and `/issues/{id}` is visible to readers

#### Scenario: Unpublish
- **WHEN** `chief` unpublishes issue 4
- **THEN** the response shows `published: false` and `/issues/{id}` answers `404` to readers, while its articles stay `PUBLISHED`

### Requirement: Set the articles of an issue
`PUT /api/issues/{id}/articles` with body `{ "articleIds": [9, 4, 6] }` SHALL make exactly these
articles the issue's articles in the given order, in one transaction. A listed article that
belongs to another issue SHALL move to this issue; an article of this issue that is not listed
SHALL belong to no issue afterwards. An empty list SHALL empty the issue. An unknown or repeated
id SHALL be answered with `400` naming the field `articleIds`, and nothing SHALL change. The
response SHALL be the issue as for `GET /api/issues/{id}`.

#### Scenario: Reorder
- **WHEN** issue 2 holds 9, 4, 6 and `chief` sends `[6, 9, 4]`
- **THEN** issue 2 lists 6, 9, 4 and article 6 is the lead story

#### Scenario: Move from another issue and drop one
- **WHEN** issue 2 holds 9, 4, article 5 belongs to issue 1, and `chief` sends `[9, 5]` for issue 2
- **THEN** issue 2 lists 9, 5, issue 1 no longer holds 5, and article 4 belongs to no issue

#### Scenario: Unknown article
- **WHEN** `chief` sends `[9, 999999]` and article 999999 does not exist
- **THEN** the response is `400` with a field error for `articleIds` and issue 2 is unchanged

### Requirement: Delete an issue
`DELETE /api/issues/{id}` SHALL delete an issue that is not published and answer `204`; its
articles SHALL belong to no issue afterwards and are otherwise unchanged. A published issue SHALL
be answered with `409` (unpublish it first) and stay unchanged. Unknown id `404`. Deleting SHALL
be logged at INFO with the issue number and the acting user. Numbers SHALL NOT be reassigned to
existing issues; a new issue gets the number after the highest remaining one.

#### Scenario: Delete a planned issue
- **WHEN** `chief` deletes issue 4, which is not published and holds articles 11 and 12
- **THEN** the response is `204` and articles 11 and 12 belong to no issue

#### Scenario: Published issue cannot be deleted
- **WHEN** `chief` deletes issue 3, which is published
- **THEN** the response is `409` and issue 3 still exists

### Requirement: Articles carry their issue
`ArticleDto` and the article summaries returned by `GET /api/articles` SHALL contain `issue`:
`{ "id": 2, "number": 2 }` for the issue the article belongs to, or `null`.

#### Scenario: Article in an issue
- **WHEN** article 9 belongs to issue 2 and its author calls `GET /api/articles/9`
- **THEN** the response contains `"issue": { "id": <id of issue 2>, "number": 2 }`

#### Scenario: Draft without issue
- **WHEN** a never-published draft belongs to no issue
- **THEN** its `issue` is `null`
