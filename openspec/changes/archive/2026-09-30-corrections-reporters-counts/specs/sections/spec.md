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
`null` for everyone else. It SHALL hold `live` (articles of the section with status `PUBLISHED`),
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
- **WHEN** `Sport` holds two published articles in issue 2, one offline article in issue 1 and one draft, and the publisher calls `GET /api/sections`
- **THEN** `Sport` has `articleCounts` `{"live": 2, "total": 4, "issues": [{"issueId": <2>, "number": 2, "count": 2}, {"issueId": <1>, "number": 1, "count": 1}]}`

#### Scenario: Empty section
- **WHEN** `Kultur` holds no article and the publisher calls `GET /api/sections`
- **THEN** `Kultur` has `articleCounts` `{"live": 0, "total": 0, "issues": []}`

### Requirement: Delete a section
`DELETE /api/sections/{id}` SHALL be available to users holding `PUBLISHER` or `EDITOR_IN_CHIEF`;
other authenticated users SHALL get `403`. An unknown id SHALL be answered with `404`. While at
least one article (in any status) belongs to the section, the system SHALL answer `409` with the
error body and change nothing. Otherwise it SHALL:

- delete the section together with all section roles held in it;
- apply the rule for losing the last section role (see accounts) to every account that held its
  last section role there;
- set the positions of the remaining sections to `0, 1, 2, …` in their previous order;
- answer `204` with an empty body.

Deleting the only remaining section SHALL be allowed.

#### Scenario: Editor-in-chief deletes an empty section
- **WHEN** sections `Sport`, `Kultur` and `Wetter` exist in this order, `Kultur` has no articles and `chief` deletes `Kultur`
- **THEN** the response is `204` and `GET /api/sections` lists `Sport` at position `0` and `Wetter` at position `1`

#### Scenario: Section still contains articles
- **WHEN** `Sport` contains one draft and the publisher deletes `Sport`
- **THEN** the response is `409` with the error body, and `Sport` and its draft are unchanged

#### Scenario: Section roles go with the section
- **WHEN** `reader` is `READER` and `REPORTER` in `Kultur` only, `Kultur` has no articles and the publisher deletes `Kultur`
- **THEN** `reader` holds no section role any more and carries the `sectionlessReporter` marker

#### Scenario: Section editor may not delete
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and deletes `Sport`
- **THEN** the response is `403` and `Sport` still exists

#### Scenario: Unknown section
- **WHEN** the publisher deletes a section id that does not exist
- **THEN** the response is `404`

### Requirement: Assign and remove a section role
`PUT /api/sections/{id}/members/{accountId}` with `role` SHALL give the account that role in the
section, replacing an existing one, and answer `200` with the member.
`DELETE /api/sections/{id}/members/{accountId}` SHALL remove the account's role in the section,
apply the rule for losing the last section role (see accounts), and answer `204`. These SHALL be
answered with `404`: an unknown section, an account that does not exist in Keycloak (or is a
service account), and, for `DELETE`, an account without a role in the section. An unknown role
SHALL be answered with `400` naming the field `role`. Every change SHALL be logged with section,
account, role and the acting user.

#### Scenario: Publisher makes a section editor
- **WHEN** the publisher puts `{"role": "SECTION_EDITOR"}` on section `Sport` for `nogroups`
- **THEN** the response is `200` with `username` `nogroups` and `role` `SECTION_EDITOR`, and `GET /api/me` for `nogroups` lists that section role

#### Scenario: Remove a reporter
- **WHEN** `reader` is `REPORTER` in `Sport` and `Kultur` and the publisher deletes the membership in `Sport`
- **THEN** the response is `204`, `reader` holds no role in `Sport` and does not carry the `sectionlessReporter` marker

#### Scenario: Remove a reporter from the last section
- **WHEN** `reader` is `REPORTER` in `Sport` only and the publisher deletes that membership
- **THEN** the response is `204`, `reader` holds no section role and carries the `sectionlessReporter` marker

#### Scenario: Unknown account
- **WHEN** the publisher assigns a role in `Sport` to an account id that does not exist
- **THEN** the response is `404`

#### Scenario: Unknown role
- **WHEN** the publisher puts `{"role": "PUBLISHER"}` on a section member
- **THEN** the response is `400` naming the field `role`
