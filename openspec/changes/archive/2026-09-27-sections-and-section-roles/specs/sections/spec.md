## Purpose

Lets editors-in-chief and publishers structure the newspaper into sections and lets everyone
from section editor up assign the per-section roles `SECTION_EDITOR` and `REPORTER` within their
own scope.

## ADDED Requirements

### Requirement: List sections
`GET /api/sections` SHALL be available to every authenticated user and return all sections
ordered by position, each with `id`, `name`, `slug`, `color`, `position` and the section roles
the requesting user may assign in that section (`assignableRoles`, in the order
`SECTION_EDITOR`, `REPORTER`). The response SHALL also state whether the requesting user may
create, change and reorder sections (`canManage`: true for `PUBLISHER` and `EDITOR_IN_CHIEF`).
Requests without a valid token SHALL get `401`.

#### Scenario: Publisher lists sections
- **WHEN** sections `Sport` and `Kultur` exist in this order and the publisher calls `GET /api/sections`
- **THEN** the response is `200` with `canManage` `true` and both sections in this order, each with `assignableRoles` `["SECTION_EDITOR", "REPORTER"]`

#### Scenario: Reader lists sections
- **WHEN** a user holding only `READER` calls `GET /api/sections`
- **THEN** the response is `200` with `canManage` `false` and every section with `assignableRoles` `[]`

#### Scenario: No sections yet
- **WHEN** no section exists and the publisher calls `GET /api/sections`
- **THEN** the response is `200` with `sections` `[]`

### Requirement: Create a section
`POST /api/sections` with `name` and optional `color` SHALL be available to users holding
`PUBLISHER` or `EDITOR_IN_CHIEF`; other authenticated users SHALL get `403`. It SHALL create the
section at the last position and answer `201` with a `Location` header `/api/sections/{id}` and
the section in the body. Without `color` the system SHALL pick the palette colour at index
(number of existing sections modulo palette size). The slug SHALL be derived from the name like a
username suggestion (lower case, umlauts and `ß` transliterated, diacritics removed, runs outside
`a-z0-9` replaced by `-`, trimmed, at most 40 characters, empty → `section`), with `-2`, `-3`, …
appended when taken. The slug SHALL NOT change later.

#### Scenario: Editor-in-chief creates a section
- **WHEN** `chief` posts `{"name": "Sport & Spiel"}` while no section exists
- **THEN** the response is `201` with `name` `Sport & Spiel`, `slug` `sport-spiel`, `color` `red` and `position` `0`

#### Scenario: Slug collision
- **WHEN** a section with slug `sport` exists and the publisher creates a section named `Sport!`
- **THEN** the new section has slug `sport-2`

#### Scenario: Reader may not create sections
- **WHEN** a user holding only `READER` posts a section
- **THEN** the response is `403` and no section is created

### Requirement: Section palette
A section colour SHALL be one of `red`, `orange`, `yellow`, `green`, `teal`, `blue`, `purple`,
`pink` (in this palette order).

#### Scenario: Unknown colour
- **WHEN** the publisher creates a section with `color` `#ff0000`
- **THEN** the response is `400` with an error for the field `color`

### Requirement: Section input is validated
The system SHALL answer section creation and update with `400` and an error body listing every
violation when `name` is missing, blank, longer than 40 characters or contains control
characters, `color` is not a palette colour, or the body has unknown fields. Names SHALL be stored
trimmed. A name already used by another section (ignoring case) SHALL be answered with `409`
naming the field `name`.

#### Scenario: Blank name
- **WHEN** the publisher posts `{"name": "  "}`
- **THEN** the response is `400` with an error for the field `name`

#### Scenario: Duplicate name
- **WHEN** a section `Sport` exists and the publisher posts `{"name": "sport"}`
- **THEN** the response is `409` naming the field `name`, and no section is created

### Requirement: Update a section
`PUT /api/sections/{id}` with `name` and `color` SHALL be available to `PUBLISHER` and
`EDITOR_IN_CHIEF` and replace both values, keeping slug and position, and answer `200` with the
section. An unknown id SHALL be answered with `404`; other authenticated users SHALL get `403`.

#### Scenario: Rename a section
- **WHEN** `chief` puts `{"name": "Sportnews", "color": "blue"}` on section `Sport` (slug `sport`)
- **THEN** the response is `200` with `name` `Sportnews`, `color` `blue` and `slug` `sport`

#### Scenario: Unknown section
- **WHEN** the publisher puts a valid body on a section id that does not exist
- **THEN** the response is `404`

### Requirement: Reorder sections
`PUT /api/sections/order` with `ids` SHALL be available to `PUBLISHER` and `EDITOR_IN_CHIEF` and
set the positions `0, 1, 2, …` in the given order, answering `200` with the same body as
`GET /api/sections`. When `ids` does not contain every existing section id exactly once, the
system SHALL answer `400` naming the field `ids` and change nothing.

#### Scenario: Move a section to the front
- **WHEN** sections `Sport` (id 1) and `Kultur` (id 2) exist in this order and the publisher puts `{"ids": [2, 1]}`
- **THEN** the response lists `Kultur` at position `0` and `Sport` at position `1`

#### Scenario: Incomplete order
- **WHEN** three sections exist and the publisher puts `{"ids": [2, 1]}`
- **THEN** the response is `400` naming the field `ids` and the order is unchanged

### Requirement: Section roles
Every account SHALL hold at most one section role per section: `SECTION_EDITOR` or `REPORTER`.
Section roles SHALL be stored in the Presserl database, keyed by the Keycloak user id (the
token's `sub`), and SHALL NOT appear as Keycloak groups.

#### Scenario: Replacing a section role
- **WHEN** `nogroups` is `REPORTER` in `Sport` and the publisher makes `nogroups` `SECTION_EDITOR` in `Sport`
- **THEN** `nogroups` holds only `SECTION_EDITOR` in `Sport`

### Requirement: Section roles are assigned within the own scope
Publishers and editors-in-chief SHALL be allowed to assign and remove `SECTION_EDITOR` and
`REPORTER` in every section. A section editor of a section SHALL be allowed to assign and remove
`SECTION_EDITOR` and `REPORTER` in that section only. Other users SHALL NOT assign or remove
section roles. Replacing a role SHALL require permission for both the old and the new role in
that section. A refused change SHALL be answered with `403` and SHALL change nothing.

#### Scenario: Section editor adds a reporter to their section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and makes `reader` `REPORTER` in `Sport`
- **THEN** the response is `200` and `reader` is `REPORTER` in `Sport`

#### Scenario: Section editor outside their section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` only and tries to make `reader` `REPORTER` in `Kultur`
- **THEN** the response is `403` and `reader` holds no role in `Kultur`

#### Scenario: Reporter may not assign
- **WHEN** `nogroups` is `REPORTER` in `Sport` and tries to make `reader` `REPORTER` in `Sport`
- **THEN** the response is `403`

### Requirement: Section members
`GET /api/sections/{id}/members` SHALL be available to users who may assign section roles in that
section and return the roles they may assign there (`assignableRoles`) and the section's members,
each with `accountId`, `username`, `firstName`, `lastName` (empty string when unset) and `role`,
section editors first, then by username. Members whose Keycloak user no longer exists SHALL be
omitted. Other authenticated users SHALL get `403`; an unknown section `404`.

#### Scenario: Members of a section
- **WHEN** `nogroups` is `SECTION_EDITOR` and `reader` is `REPORTER` in `Sport` and the publisher calls `GET /api/sections/{Sport}/members`
- **THEN** the response lists `nogroups` with `SECTION_EDITOR` before `reader` with `REPORTER`

#### Scenario: Reporter may not list members
- **WHEN** `reader` is `REPORTER` in `Sport` and calls `GET /api/sections/{Sport}/members`
- **THEN** the response is `403`

### Requirement: Assign and remove a section role
`PUT /api/sections/{id}/members/{accountId}` with `role` SHALL give the account that role in the
section, replacing an existing one, and answer `200` with the member.
`DELETE /api/sections/{id}/members/{accountId}` SHALL remove the account's role in the section
and answer `204`. An unknown section, an account that does not exist in Keycloak (or is a
service account), or — for `DELETE` — an account without a role in the section SHALL be answered
with `404`. An unknown role SHALL be answered with `400` naming the field `role`. Every change
SHALL be logged with section, account, role and the acting user.

#### Scenario: Publisher makes a section editor
- **WHEN** the publisher puts `{"role": "SECTION_EDITOR"}` on section `Sport` for `nogroups`
- **THEN** the response is `200` with `username` `nogroups` and `role` `SECTION_EDITOR`, and `GET /api/me` for `nogroups` lists that section role

#### Scenario: Remove a reporter
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher deletes that membership
- **THEN** the response is `204` and `reader` holds no role in `Sport`

#### Scenario: Unknown account
- **WHEN** the publisher assigns a role in `Sport` to an account id that does not exist
- **THEN** the response is `404`

#### Scenario: Unknown role
- **WHEN** the publisher puts `{"role": "PUBLISHER"}` on a section member
- **THEN** the response is `400` naming the field `role`
