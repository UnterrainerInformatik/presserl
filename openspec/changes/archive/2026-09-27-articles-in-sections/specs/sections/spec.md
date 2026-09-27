## MODIFIED Requirements

### Requirement: List sections
`GET /api/sections` SHALL be available to every authenticated user and return all sections
ordered by position, each with `id`, `name`, `slug`, `color`, `position`, the section roles
the requesting user may assign in that section (`assignableRoles`, in the order
`SECTION_EDITOR`, `REPORTER`) and whether the requesting user may write articles in that section
(`canWrite`: true for `PUBLISHER` and `EDITOR_IN_CHIEF` in every section, and for holders of a
section role in that section). The response SHALL also state whether the requesting user may
create, change and reorder sections (`canManage`: true for `PUBLISHER` and `EDITOR_IN_CHIEF`).
Requests without a valid token SHALL get `401`.

#### Scenario: Publisher lists sections
- **WHEN** sections `Sport` and `Kultur` exist in this order and the publisher calls `GET /api/sections`
- **THEN** the response is `200` with `canManage` `true` and both sections in this order, each with `assignableRoles` `["SECTION_EDITOR", "REPORTER"]` and `canWrite` `true`

#### Scenario: Reader lists sections
- **WHEN** a user holding only `READER` calls `GET /api/sections`
- **THEN** the response is `200` with `canManage` `false` and every section with `assignableRoles` `[]` and `canWrite` `false`

#### Scenario: Reporter lists sections
- **WHEN** `reader` is `REPORTER` in `Sport` only and calls `GET /api/sections`
- **THEN** `Sport` has `canWrite` `true` and every other section `canWrite` `false`

#### Scenario: No sections yet
- **WHEN** no section exists and the publisher calls `GET /api/sections`
- **THEN** the response is `200` with `sections` `[]`
