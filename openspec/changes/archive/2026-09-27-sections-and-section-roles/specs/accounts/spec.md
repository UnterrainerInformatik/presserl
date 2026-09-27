## MODIFIED Requirements

### Requirement: Account endpoints require an administering role
The account endpoints under `/api/accounts` SHALL be available to users holding `PUBLISHER` or
`EDITOR_IN_CHIEF` and to users who are `SECTION_EDITOR` in at least one section. Other
authenticated users SHALL get `403` with an empty body; requests without a valid token SHALL get
`401`.

#### Scenario: Reader lists accounts
- **WHEN** a user holding only `READER` calls `GET /api/accounts`
- **THEN** the response is `403`

#### Scenario: Editor-in-chief lists accounts
- **WHEN** a user holding `EDITOR_IN_CHIEF` calls `GET /api/accounts`
- **THEN** the response is `200`

#### Scenario: Section editor lists accounts
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and calls `GET /api/accounts`
- **THEN** the response is `200` with `assignableRoles` `[]`

#### Scenario: Reporter lists accounts
- **WHEN** `nogroups` is only `REPORTER` in `Sport` and calls `GET /api/accounts`
- **THEN** the response is `403`

### Requirement: List accounts
`GET /api/accounts` SHALL return every user of the realm except service-account users, sorted by
username, each with its id, username, first name, last name (empty string when unset), its
newspaper roles derived from its Keycloak groups (in the order `PUBLISHER`, `EDITOR_IN_CHIEF`,
`READER`), its section roles (`sectionRoles`: `sectionId`, `role`, ordered by section position)
and whether it is enabled. The response SHALL also list the newspaper roles the requesting user
may assign (`assignableRoles`).

#### Scenario: Publisher lists accounts of the dev realm
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** the list contains `chief` with roles `["EDITOR_IN_CHIEF"]`, `reader` with `["READER"]`, `nogroups` with `[]` and the publisher with `["PUBLISHER"]`, and no user whose username starts with `service-account-`

#### Scenario: Section roles in the list
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `sectionRoles` `[{"sectionId": <Sport>, "role": "REPORTER"}]` and `chief` has `sectionRoles` `[]`

#### Scenario: Assignable roles of a publisher
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** `assignableRoles` is `["PUBLISHER", "EDITOR_IN_CHIEF", "READER"]`

#### Scenario: Assignable roles of an editor-in-chief
- **WHEN** an editor-in-chief who is not a publisher calls `GET /api/accounts`
- **THEN** `assignableRoles` is `["EDITOR_IN_CHIEF", "READER"]`

### Requirement: Create an account
`POST /api/accounts` with `firstName`, optional `lastName`, `username`, `roles` and optional
`sectionRoles` (each `sectionId` and `role`) SHALL create an enabled Keycloak user with these
names, make it a member of the Keycloak group of every given role, give it the given section
roles, set a newly generated default password (not temporary) and answer `201` with a `Location`
header `/api/accounts/{id}` and a body containing the account (as in the list) and the generated
`password`. The password SHALL appear in this response only; the system SHALL NOT store or log
it. When the account cannot be created completely, no user SHALL remain in Keycloak and no
section role SHALL be stored.

#### Scenario: Publisher creates the editor-in-chief
- **WHEN** the publisher posts `{"firstName": "Lena", "username": "lena", "roles": ["EDITOR_IN_CHIEF"]}`
- **THEN** the response is `201` with `account.username` `lena`, `account.roles` `["EDITOR_IN_CHIEF"]`, `account.sectionRoles` `[]`, `account.enabled` `true` and a `password` of four dash-joined words, and `lena` can log in with that password and gets `roles` `["EDITOR_IN_CHIEF"]` from `GET /api/me`

#### Scenario: Several roles
- **WHEN** the publisher creates an account with `roles` `["READER", "PUBLISHER"]`
- **THEN** the account is a member of both groups and `account.roles` is `["PUBLISHER", "READER"]`

#### Scenario: Account with a section role only
- **WHEN** the publisher posts `{"firstName": "Max", "username": "max", "roles": [], "sectionRoles": [{"sectionId": <Sport>, "role": "REPORTER"}]}`
- **THEN** the response is `201` with `account.roles` `[]` and `account.sectionRoles` `[{"sectionId": <Sport>, "role": "REPORTER"}]`, and `max` gets that section role from `GET /api/me`

#### Scenario: Password is not logged
- **WHEN** an account is created
- **THEN** the backend log names the new username, the creator, the roles and the section roles, and does not contain the password

### Requirement: Account input is validated
The system SHALL answer `POST /api/accounts` with `400` and an error body listing every violation
when: `firstName` is missing, blank or longer than 100 characters; `lastName` is longer than 100
characters; a name contains control characters; `username` does not match
`^[a-z0-9]+(-[a-z0-9]+)*$`, is shorter than 3 or longer than 32 characters or starts with
`service-account-`; `roles` is missing or contains an unknown value; `sectionRoles` is not a list,
names a section that does not exist, names a section twice or contains an unknown role; `roles`
and `sectionRoles` are both empty (field `roles`); or the body has unknown fields. Names SHALL be
stored trimmed; duplicate roles SHALL be collapsed. A username that is already taken (ignoring
case) SHALL be answered with `409` naming the field `username`.

#### Scenario: Invalid username and no roles
- **WHEN** the publisher posts `{"firstName": "Max", "username": "Max Mustermann", "roles": []}`
- **THEN** the response is `400` with errors for the fields `username` and `roles`, and no user is created

#### Scenario: Username too short
- **WHEN** the publisher posts `{"firstName": "Li", "username": "li", "roles": ["READER"]}`
- **THEN** the response is `400` with an error for the field `username` stating the minimum of 3 characters, and no user is created

#### Scenario: Username taken
- **WHEN** the publisher posts an account with `username` `chief`
- **THEN** the response is `409` with an error for the field `username`, and the existing `chief` is unchanged

#### Scenario: Unknown section
- **WHEN** the publisher posts an account with `sectionRoles` `[{"sectionId": 999, "role": "REPORTER"}]` and no section 999 exists
- **THEN** the response is `400` with an error for the field `sectionRoles`, and no user is created

## ADDED Requirements

### Requirement: Section roles of a new account are assigned within the own scope
Section roles given when creating an account SHALL follow the scope rule for section roles: a
publisher or editor-in-chief may give `SECTION_EDITOR` and `REPORTER` in every section, a section
editor only in their own sections. A request containing a section role the requesting user may
not assign SHALL be answered with `403` and an error body naming the field `sectionRoles`, and no
user SHALL be created.

#### Scenario: Section editor creates a reporter
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and posts an account with `roles` `[]` and `sectionRoles` `[{"sectionId": <Sport>, "role": "REPORTER"}]`
- **THEN** the response is `201`

#### Scenario: Section editor outside their scope
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` only and posts an account with a `REPORTER` role in `Kultur`
- **THEN** the response is `403` naming the field `sectionRoles`, and no user is created

#### Scenario: Section editor assigns a newspaper role
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and posts an account with `roles` `["READER"]`
- **THEN** the response is `403` naming the field `roles`, and no user is created
