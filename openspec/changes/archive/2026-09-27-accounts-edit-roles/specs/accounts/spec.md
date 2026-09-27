## MODIFIED Requirements

### Requirement: List accounts
`GET /api/accounts` SHALL return every user of the realm except service-account users, sorted by
username, each with its id, username, first name, last name (empty string when unset), its
newspaper roles derived from its Keycloak groups (in the order `PUBLISHER`, `EDITOR_IN_CHIEF`,
`READER`), its section roles (`sectionRoles`: `sectionId`, `role`, ordered by section position),
whether it is enabled and the actions the requesting user may perform on it now
(`allowedActions`, in the order `EDIT_ROLES`, `RESET_PASSWORD`, `LOCK`, `UNLOCK`, following the
role-editing, password-reset and lock rules). `LOCK` SHALL be listed only for enabled accounts and
`UNLOCK` only for disabled ones. The response SHALL also list the newspaper roles the requesting
user may assign (`assignableRoles`).

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

#### Scenario: Allowed actions of a publisher
- **WHEN** the publisher calls `GET /api/accounts` while all accounts are enabled
- **THEN** `chief`, `reader` and `nogroups` have `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD", "LOCK"]` and the publisher's own account has `[]`

#### Scenario: Allowed actions on a locked account
- **WHEN** `reader` is locked and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD", "UNLOCK"]`

#### Scenario: Allowed actions of an editor-in-chief
- **WHEN** `chief` calls `GET /api/accounts`
- **THEN** `reader` and `nogroups` have `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD"]`, and `chief` and the publisher have `[]`

## ADDED Requirements

### Requirement: Roles are edited only by someone above the person
A user SHALL be allowed to edit the roles of an account exactly when the password-reset rule
allows them to reset its password: publishers for accounts not holding `PUBLISHER`,
editors-in-chief for accounts holding neither `PUBLISHER` nor `EDITOR_IN_CHIEF`, section editors
for accounts whose only newspaper role is at most `READER` and whose section roles are all
`REPORTER` in sections the section editor leads; nobody for their own account. Locked accounts
SHALL be editable as well.

#### Scenario: Editor-in-chief edits another editor-in-chief
- **WHEN** `chief` calls the role edit for another account holding `EDITOR_IN_CHIEF`
- **THEN** the response is `403` and that account's roles are unchanged

#### Scenario: Own account
- **WHEN** the publisher calls the role edit for their own account
- **THEN** the response is `403`

#### Scenario: Section editor and a plain reader
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and `reader` holds only `READER`, and `nogroups` calls the role edit for `reader`
- **THEN** the response is `403`

### Requirement: Edit the roles of an account
`PUT /api/accounts/{id}/roles` with `roles` and `sectionRoles` SHALL replace the account's
newspaper roles (Keycloak group memberships) and section roles with the given ones and answer
`200` with the account as listed (including `sectionRoles` and `allowedActions` computed for the
requesting user). Both fields SHALL be required and follow the rules of `POST /api/accounts`:
known newspaper roles (duplicates collapsed), section roles as a list of `sectionId` and `role`
naming existing sections at most once each, and at least one role of either kind (field
`roles`); unknown fields SHALL be rejected. Violations SHALL be answered with `400` listing every
violation. The change SHALL be all or nothing: when it cannot be completed, the account SHALL keep
its previous roles of both kinds. A request that changes nothing SHALL answer `200` without
writing. An effective change SHALL be logged at INFO with the account, the acting user and the
roles before and after. The endpoint SHALL answer `403` with an error body when the role-editing
rule refuses, `404` for an unknown id or a service account, and follow the access rule of the
account endpoints. Removed newspaper roles SHALL take effect for the person with their next token
refresh; no session SHALL be ended.

#### Scenario: Publisher makes a reader editor-in-chief
- **WHEN** the publisher puts `{"roles": ["EDITOR_IN_CHIEF", "READER"], "sectionRoles": []}` on `reader`
- **THEN** the response is `200` with `roles` `["EDITOR_IN_CHIEF", "READER"]`, and after logging in again `reader` gets `roles` `["EDITOR_IN_CHIEF", "READER"]` from `GET /api/me`

#### Scenario: Publisher moves a reporter to another section
- **WHEN** `reader` is `READER` and `REPORTER` in `Sport` and the publisher puts `{"roles": ["READER"], "sectionRoles": [{"sectionId": <Kultur>, "role": "REPORTER"}]}` on `reader`
- **THEN** the response is `200` with `sectionRoles` `[{"sectionId": <Kultur>, "role": "REPORTER"}]`, and `reader` holds no role in `Sport`

#### Scenario: No role left
- **WHEN** the publisher puts `{"roles": [], "sectionRoles": []}` on `reader`
- **THEN** the response is `400` naming the field `roles`, and `reader` keeps `READER`

#### Scenario: Missing section roles
- **WHEN** the publisher puts `{"roles": ["READER"]}` on `reader`
- **THEN** the response is `400` naming the field `sectionRoles`

#### Scenario: Unknown section
- **WHEN** the publisher puts section roles naming section 999 and no section 999 exists
- **THEN** the response is `400` naming the field `sectionRoles`, and the account is unchanged

#### Scenario: Unknown account
- **WHEN** the publisher calls the role edit for an id that does not exist
- **THEN** the response is `404`

#### Scenario: Unchanged roles
- **WHEN** the publisher puts the roles `reader` already holds
- **THEN** the response is `200` and nothing is logged as changed

### Requirement: Role changes stay within the own scope
Every newspaper role that a role edit adds or removes SHALL be one the requesting user may assign
(newspaper roles at or below the own level); every section in which the section role is added,
changed or removed SHALL follow the scope rule for section roles (both the old and the new role
assignable there). Roles the edit leaves unchanged SHALL need no permission. A refused change
SHALL be answered with `403` and an error body naming the field `roles` or `sectionRoles`, and
SHALL change nothing.

#### Scenario: Editor-in-chief makes a publisher
- **WHEN** `chief` puts `{"roles": ["PUBLISHER", "READER"], "sectionRoles": []}` on `reader`
- **THEN** the response is `403` naming the field `roles`, and `reader` keeps only `READER`

#### Scenario: Section editor keeps the reader role
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `READER` and `REPORTER` in `Sport`, and `nogroups` puts `{"roles": ["READER"], "sectionRoles": [{"sectionId": <Sport>, "role": "SECTION_EDITOR"}]}` on `reader`
- **THEN** the response is `200` and `reader` is `SECTION_EDITOR` in `Sport` and still `READER`

#### Scenario: Section editor removes the reader role
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `READER` and `REPORTER` in `Sport`, and `nogroups` puts `{"roles": [], "sectionRoles": [{"sectionId": <Sport>, "role": "REPORTER"}]}` on `reader`
- **THEN** the response is `403` naming the field `roles`, and `reader` keeps `READER`

#### Scenario: Section editor outside their section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` only, `reader` is `REPORTER` in `Sport`, and `nogroups` puts section roles adding `REPORTER` in `Kultur` for `reader`
- **THEN** the response is `403` naming the field `sectionRoles`, and `reader` holds no role in `Kultur`
