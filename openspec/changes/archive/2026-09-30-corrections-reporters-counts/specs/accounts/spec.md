## ADDED Requirements

### Requirement: Reporter without a section
An account SHALL be able to carry the newspaper-wide marker `sectionlessReporter`, stored in the
Presserl database (not in Keycloak) and effective at once. It SHALL let the account use the media
endpoints (action `USE_MEDIA`, see api-authentication) without writing articles. It SHALL be
independent of section roles: an account may carry it together with section roles, and it SHALL
count as a role wherever at least one role is required. Only users holding `PUBLISHER` or
`EDITOR_IN_CHIEF` SHALL add or remove it. A request by anyone else that would change it SHALL be
answered with `403` naming the field `sectionlessReporter`, and nothing SHALL change. Deleting the
account from Keycloak outside Presserl leaves a stale marker without effect.

#### Scenario: Editor-in-chief creates a photographer
- **WHEN** `chief` posts `{"firstName": "Pia", "username": "pia", "roles": [], "sectionlessReporter": true}`
- **THEN** the response is `201` with `account.roles` `[]`, `account.sectionRoles` `[]` and `account.sectionlessReporter` `true`

#### Scenario: Section editor may not assign it
- **WHEN** `nogroups`, `SECTION_EDITOR` in `Sport`, posts an account with `"sectionlessReporter": true`
- **THEN** the response is `403` naming `sectionlessReporter` and no user is created

#### Scenario: Marker alone is a role
- **WHEN** the publisher puts `{"roles": [], "sectionRoles": [], "sectionlessReporter": true}` on `reader`
- **THEN** the response is `200` and `reader` has `roles` `[]`, `sectionRoles` `[]` and `sectionlessReporter` `true`

### Requirement: Losing the last section role leaves a sectionless reporter
When an account loses its last section role, the system SHALL set `sectionlessReporter` on it in
the same transaction if the account holds neither `PUBLISHER` nor `EDITOR_IN_CHIEF`. This applies
when the role is lost through member removal, section deletion, or a role edit that does not
contain the field `sectionlessReporter`. The system SHALL log the change with the acting user. The
automatic marker SHALL NOT need the permission to assign it. A role edit that explicitly sends
`"sectionlessReporter": false` SHALL be honoured under the assignment rule.

#### Scenario: Removed from the last section
- **WHEN** `reader` is `READER` and `REPORTER` in `Sport` only and the section editor of `Sport` removes `reader` from `Sport`
- **THEN** `reader` holds no section role and has `sectionlessReporter` `true`

#### Scenario: Editor-in-chief keeps no marker
- **WHEN** `chief` holds `EDITOR_IN_CHIEF` and loses their only section role
- **THEN** `chief` has `sectionlessReporter` `false`

#### Scenario: Explicit removal of every writing right
- **WHEN** the publisher puts `{"roles": ["READER"], "sectionRoles": [], "sectionlessReporter": false}` on `reader`, who is `REPORTER` in `Sport` only
- **THEN** the response is `200` and `reader` holds only `READER`

## MODIFIED Requirements

### Requirement: Account input is validated
The system SHALL answer `POST /api/accounts` with `400` and an error body listing every violation.
The violations are:

- `firstName` is missing, blank or longer than 100 characters;
- `lastName` is longer than 100 characters;
- a name contains control characters;
- `username` does not match `^[a-z0-9]+(-[a-z0-9]+)*$`, is shorter than 3 or longer than 32
  characters, or starts with `service-account-`;
- `roles` is missing or contains an unknown value;
- `sectionRoles` is not a list, names a section that does not exist, names a section twice or
  contains an unknown role;
- `sectionlessReporter` is present and not a boolean;
- `roles` and `sectionRoles` are both empty while `sectionlessReporter` is not `true` (field
  `roles`);
- the body has unknown fields.

Names SHALL be stored trimmed, and duplicate roles SHALL be collapsed. A username that is already
taken (ignoring case) SHALL be answered with `409` naming the field `username`.

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

#### Scenario: Marker is not a boolean
- **WHEN** the publisher posts an account with `"sectionlessReporter": "yes"`
- **THEN** the response is `400` naming `sectionlessReporter`

### Requirement: Create an account
`POST /api/accounts` SHALL accept `firstName`, optional `lastName`, `username`, `roles`, optional
`sectionRoles` (each `sectionId` and `role`) and optional `sectionlessReporter` (default `false`).
It SHALL:

- create an enabled Keycloak user with these names;
- make it a member of the Keycloak group of every given role;
- give it the given section roles and, when requested, the `sectionlessReporter` marker;
- set a newly generated default password (not temporary);
- answer `201` with a `Location` header `/api/accounts/{id}` and a body containing the account (as
  in the list) and the generated `password`.

The password SHALL appear in this response only; the system SHALL NOT store or log it. When the
account cannot be created completely, no user SHALL remain in Keycloak, and no section role or
marker SHALL be stored.

#### Scenario: Publisher creates the editor-in-chief
- **WHEN** the publisher posts `{"firstName": "Lena", "username": "lena", "roles": ["EDITOR_IN_CHIEF"]}`
- **THEN** the response is `201` with `account.username` `lena`, `account.roles` `["EDITOR_IN_CHIEF"]`, `account.sectionRoles` `[]`, `account.sectionlessReporter` `false`, `account.enabled` `true` and a `password` of four dash-joined words, and `lena` can log in with that password and gets `roles` `["EDITOR_IN_CHIEF"]` from `GET /api/me`

#### Scenario: Several roles
- **WHEN** the publisher creates an account with `roles` `["READER", "PUBLISHER"]`
- **THEN** the account is a member of both groups and `account.roles` is `["PUBLISHER", "READER"]`

#### Scenario: Account with a section role only
- **WHEN** the publisher posts `{"firstName": "Max", "username": "max", "roles": [], "sectionRoles": [{"sectionId": <Sport>, "role": "REPORTER"}]}`
- **THEN** the response is `201` with `account.roles` `[]` and `account.sectionRoles` `[{"sectionId": <Sport>, "role": "REPORTER"}]`, and `max` gets that section role from `GET /api/me`

#### Scenario: Password is not logged
- **WHEN** an account is created
- **THEN** the backend log names the new username, the creator, the roles, the section roles and the marker, and does not contain the password

### Requirement: List accounts
`GET /api/accounts` SHALL return every user of the realm except service-account users, sorted by
username. Each account SHALL carry:

- its id, username, first name and last name (empty string when unset);
- its newspaper roles derived from its Keycloak groups (in the order `PUBLISHER`,
  `EDITOR_IN_CHIEF`, `READER`);
- its section roles (`sectionRoles`: `sectionId`, `role`, ordered by section position);
- whether it carries the `sectionlessReporter` marker;
- whether it is enabled;
- its trust entries (`trusts`: `level`, `sectionId`, ordered `PUBLISHER`, `EDITOR_IN_CHIEF`, then
  `SECTION_EDITOR` entries by section position);
- the trust entries the requesting user may set or clear on it (`trustScopes`, same shape and
  order, following the trust rules);
- the actions the requesting user may perform on it now (`allowedActions`, in the order
  `EDIT_ROLES`, `RESET_PASSWORD`, `LOCK`, `UNLOCK`, following the role-editing, password-reset and
  lock rules). `LOCK` SHALL be listed only for enabled accounts and `UNLOCK` only for disabled
  ones.

The response SHALL also list the newspaper roles the requesting user may assign (`assignableRoles`)
and whether they may assign the marker (`mayAssignSectionlessReporter`). Every other account
endpoint that answers with an account SHALL carry the same fields computed for the requesting
user.

#### Scenario: Publisher lists accounts of the dev realm
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** the list contains `chief` with roles `["EDITOR_IN_CHIEF"]`, `reader` with `["READER"]`, `nogroups` with `[]` and the publisher with `["PUBLISHER"]`, each with `sectionlessReporter` `false`, and no user whose username starts with `service-account-`

#### Scenario: Section roles in the list
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `sectionRoles` `[{"sectionId": <Sport>, "role": "REPORTER"}]` and `chief` has `sectionRoles` `[]`

#### Scenario: Trust in the list
- **WHEN** the publisher level trusts `chief` and `nogroups` calls `GET /api/accounts` as `SECTION_EDITOR` in `Sport`
- **THEN** `chief` has `trusts` `[{"level": "PUBLISHER", "sectionId": null}]` and `trustScopes` `[]`

#### Scenario: Assignable roles of a publisher
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** `assignableRoles` is `["PUBLISHER", "EDITOR_IN_CHIEF", "READER"]` and `mayAssignSectionlessReporter` is `true`

#### Scenario: Assignable roles of an editor-in-chief
- **WHEN** an editor-in-chief who is not a publisher calls `GET /api/accounts`
- **THEN** `assignableRoles` is `["EDITOR_IN_CHIEF", "READER"]` and `mayAssignSectionlessReporter` is `true`

#### Scenario: Section editor may not assign the marker
- **WHEN** `nogroups`, `SECTION_EDITOR` in `Sport`, calls `GET /api/accounts`
- **THEN** `mayAssignSectionlessReporter` is `false`

#### Scenario: Allowed actions of a publisher
- **WHEN** the publisher calls `GET /api/accounts` while all accounts are enabled
- **THEN** `chief`, `reader` and `nogroups` have `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD", "LOCK"]` and the publisher's own account has `[]`

#### Scenario: Allowed actions on a locked account
- **WHEN** `reader` is locked and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD", "UNLOCK"]`

#### Scenario: Allowed actions of an editor-in-chief
- **WHEN** `chief` calls `GET /api/accounts`
- **THEN** `reader` and `nogroups` have `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD"]`, and `chief` and the publisher have `[]`

### Requirement: Edit the roles of an account
`PUT /api/accounts/{id}/roles` SHALL accept `roles`, `sectionRoles` and optional
`sectionlessReporter`. It SHALL replace the account's newspaper roles (Keycloak group memberships)
and section roles with the given ones, and set or clear the marker when `sectionlessReporter` is
given. When the field is absent, the marker SHALL stay unchanged, except for the automatic marker
when the last section role goes. It SHALL answer `200` with the account as listed (including
`sectionRoles`, `sectionlessReporter` and `allowedActions` computed for the requesting user).

`roles` and `sectionRoles` SHALL be required and follow the rules of `POST /api/accounts`: known
newspaper roles (duplicates collapsed), and section roles as a list of `sectionId` and `role`
naming existing sections at most once each. At least one role of either kind, or the marker, SHALL
remain (field `roles`). Unknown fields SHALL be rejected. Violations SHALL be answered with `400`
listing every violation.

The change SHALL be all or nothing: when it cannot be completed, the account SHALL keep its
previous roles of both kinds and its marker. A request that changes nothing SHALL answer `200`
without writing. An effective change SHALL be logged at INFO with the account, the acting user and
the roles and marker before and after.

The endpoint SHALL answer `403` with an error body when the role-editing rule refuses, `404` for an
unknown id or a service account, and follow the access rule of the account endpoints. Removed
newspaper roles SHALL take effect for the person with their next token refresh; no session SHALL
be ended.

#### Scenario: Publisher makes a reader editor-in-chief
- **WHEN** the publisher puts `{"roles": ["EDITOR_IN_CHIEF", "READER"], "sectionRoles": []}` on `reader`
- **THEN** the response is `200` with `roles` `["EDITOR_IN_CHIEF", "READER"]`, and after logging in again `reader` gets `roles` `["EDITOR_IN_CHIEF", "READER"]` from `GET /api/me`

#### Scenario: Publisher moves a reporter to another section
- **WHEN** `reader` is `READER` and `REPORTER` in `Sport` and the publisher puts `{"roles": ["READER"], "sectionRoles": [{"sectionId": <Kultur>, "role": "REPORTER"}]}` on `reader`
- **THEN** the response is `200` with `sectionRoles` `[{"sectionId": <Kultur>, "role": "REPORTER"}]`, and `reader` holds no role in `Sport`

#### Scenario: No role left
- **WHEN** the publisher puts `{"roles": [], "sectionRoles": [], "sectionlessReporter": false}` on `reader`, who holds only `READER`
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

#### Scenario: Last section removed without the field
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `READER` and `REPORTER` in `Sport` only, and `nogroups` puts `{"roles": ["READER"], "sectionRoles": []}` on `reader`
- **THEN** the response is `200` with `sectionRoles` `[]` and `sectionlessReporter` `true`
