## MODIFIED Requirements

### Requirement: List sections
`GET /api/sections` SHALL be available to every authenticated user and return all sections
ordered by position. Each section SHALL carry:

- `id`, `name`, `slug`, `color` and `position`;
- the section roles the requesting user may assign in that section (`assignableRoles`, in the
  order `SECTION_EDITOR`, `REPORTER`);
- whether the requesting user may write articles in that section (`canWrite`: true for
  `PUBLISHER` and `EDITOR_IN_CHIEF` in every section, and for holders of a section role in that
  section);
- `articleCounts`.

`articleCounts` SHALL be present for users whose `allowedActions` contain `WRITE_ARTICLES` and
`null` for everyone else. It SHALL hold `live` (articles of the section visible to readers: status `PUBLISHED` in a
published issue),
`total` (all articles currently in the section, in any status) and `issues`: one entry per issue
that contains at least one article of the section, with `issueId`, `number` and `count` (articles
of the section in that issue, in any status), highest issue number first.

The response SHALL also state whether the requesting user may create, change and reorder sections
(`canManage`: true for `PUBLISHER` and `EDITOR_IN_CHIEF`). Requests without a valid token SHALL get
`401`.

#### Scenario: Publisher lists sections
- **WHEN** sections `Sport` and `Kultur` exist in this order and the publisher calls `GET /api/sections`
- **THEN** the response is `200` with `canManage` `true` and both sections in this order, each with `assignableRoles` `["SECTION_EDITOR", "REPORTER"]` and `canWrite` `true`

#### Scenario: Reader lists sections
- **WHEN** a user holding only `READER` calls `GET /api/sections`
- **THEN** the response is `200` with `canManage` `false` and every section with `assignableRoles` `[]`, `canWrite` `false` and `articleCounts` `null`

#### Scenario: Reporter lists sections
- **WHEN** `reader` is `REPORTER` in `Sport` only and calls `GET /api/sections`
- **THEN** `Sport` has `canWrite` `true` and every other section `canWrite` `false`

#### Scenario: No sections yet
- **WHEN** no section exists and the publisher calls `GET /api/sections`
- **THEN** the response is `200` with `sections` `[]`

#### Scenario: Article counts
- **WHEN** `Sport` holds two published articles in the published issue 2, one offline article in issue 1 and one draft, and the publisher calls `GET /api/sections`
- **THEN** `Sport` has `articleCounts` `{"live": 2, "total": 4, "issues": [{"issueId": <2>, "number": 2, "count": 2}, {"issueId": <1>, "number": 1, "count": 1}]}`

#### Scenario: Empty section
- **WHEN** `Kultur` holds no article and the publisher calls `GET /api/sections`
- **THEN** `Kultur` has `articleCounts` `{"live": 0, "total": 0, "issues": []}`

#### Scenario: Articles waiting for their issue are not live
- **WHEN** `Sport` holds one published article in the published issue 1 and one published article in issue 2, which is not published, and the publisher calls `GET /api/sections`
- **THEN** `Sport` has `articleCounts.live` `1` and `total` `2`
