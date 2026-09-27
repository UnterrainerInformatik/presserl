## MODIFIED Requirements

### Requirement: List accounts
`GET /api/accounts` SHALL return every user of the realm except service-account users, sorted by
username, each with its id, username, first name, last name (empty string when unset), its
newspaper roles derived from its Keycloak groups (in the order `PUBLISHER`, `EDITOR_IN_CHIEF`,
`READER`), its section roles (`sectionRoles`: `sectionId`, `role`, ordered by section position),
whether it is enabled and the actions the requesting user may perform on it now
(`allowedActions`, in the order `RESET_PASSWORD`, `LOCK`, `UNLOCK`, following the password-reset
and lock rules). `LOCK` SHALL be listed only for enabled accounts and `UNLOCK` only for disabled
ones. The response SHALL also list the newspaper roles the requesting user may assign
(`assignableRoles`).

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
- **THEN** `chief`, `reader` and `nogroups` have `allowedActions` `["RESET_PASSWORD", "LOCK"]` and the publisher's own account has `[]`

#### Scenario: Allowed actions on a locked account
- **WHEN** `reader` is locked and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `allowedActions` `["RESET_PASSWORD", "UNLOCK"]`

#### Scenario: Allowed actions of an editor-in-chief
- **WHEN** `chief` calls `GET /api/accounts`
- **THEN** `reader` and `nogroups` have `allowedActions` `["RESET_PASSWORD"]`, and `chief` and the publisher have `[]`

## ADDED Requirements

### Requirement: Password reset only by someone above the person
A user SHALL be allowed to reset the password of an account only when they rank above it:
- a publisher: every account that does not hold `PUBLISHER`;
- an editor-in-chief: every account holding neither `PUBLISHER` nor `EDITOR_IN_CHIEF`;
- a section editor: only accounts that hold no newspaper role other than `READER`, are
  `SECTION_EDITOR` in no section, and hold at least one section role, every one of them
  `REPORTER` in a section where the requesting user is `SECTION_EDITOR`;
- nobody else. Nobody SHALL reset the password of their own account.

#### Scenario: Publisher resets another publisher
- **WHEN** a publisher calls the password reset for another account holding `PUBLISHER`
- **THEN** the response is `403` and that account's password is unchanged

#### Scenario: Editor-in-chief resets another editor-in-chief
- **WHEN** `chief` calls the password reset for another account holding `EDITOR_IN_CHIEF`
- **THEN** the response is `403`

#### Scenario: Section editor resets their reporter
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and `reader` is `READER` and `REPORTER` in `Sport` only, and `nogroups` calls the password reset for `reader`
- **THEN** the response is `200`

#### Scenario: Reporter also in another section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and `reader` is `REPORTER` in `Sport` and in `Kultur`, and `nogroups` calls the password reset for `reader`
- **THEN** the response is `403`

#### Scenario: Section editor and a plain reader
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and `reader` holds only `READER`, and `nogroups` calls the password reset for `reader`
- **THEN** the response is `403`

#### Scenario: Own account
- **WHEN** the publisher calls the password reset for their own account
- **THEN** the response is `403`

### Requirement: Reset a password
`POST /api/accounts/{id}/password-reset` SHALL, for a user allowed by the password-reset rule,
set a newly generated default password (not temporary, following the pass-phrase rule), end
every Keycloak session of the account and answer `200` with the account (as listed, including
`sectionRoles` and `allowedActions`) and the new `password`. The password SHALL appear only in
this response and SHALL be neither stored nor logged by the backend. A locked account SHALL stay
locked. The reset SHALL be logged at INFO with the account and the acting user. The endpoint
SHALL answer `403` with an error body when the rule refuses, `404` for an unknown id or a service
account, and follow the access rule of the account endpoints.

#### Scenario: Publisher resets a password
- **WHEN** the publisher calls `POST /api/accounts/<reader>/password-reset`
- **THEN** the response is `200` with `account.username` `reader` and a four-word `password`, `reader` can log in with it, the old password no longer works, and a refresh token `reader` held before is rejected

#### Scenario: Reset of a locked account
- **WHEN** `reader` is locked and the publisher resets its password
- **THEN** the response is `200` with `account.enabled` `false`, and `reader` still cannot log in

#### Scenario: Unknown account
- **WHEN** the publisher calls the password reset for an id that does not exist
- **THEN** the response is `404`

#### Scenario: Reader resets a password
- **WHEN** a user holding only `READER` calls the password reset for any account
- **THEN** the response is `403`

### Requirement: Lock and unlock accounts
`POST /api/accounts/{id}/lock` SHALL disable the account in Keycloak and end every session of
it; `POST /api/accounts/{id}/unlock` SHALL enable it again. Both SHALL be allowed only to users
holding `PUBLISHER`, and only for accounts that do not hold `PUBLISHER` and are not their own.
Both SHALL answer `200` with the account (as listed) and SHALL be idempotent: locking a locked
account or unlocking an enabled one answers `200` without change. Both SHALL be logged at INFO
with the account and the acting user. They SHALL answer `403` with an error body when refused,
`404` for an unknown id or a service account, and follow the access rule of the account
endpoints. Access tokens issued before a lock SHALL be accepted until they expire.

#### Scenario: Publisher locks an account
- **WHEN** the publisher calls `POST /api/accounts/<reader>/lock`
- **THEN** the response is `200` with `account.enabled` `false`, `reader` can no longer log in, and a refresh token `reader` held before is rejected

#### Scenario: Publisher unlocks an account
- **WHEN** `reader` is locked and the publisher calls `POST /api/accounts/<reader>/unlock`
- **THEN** the response is `200` with `account.enabled` `true`, and `reader` can log in with its password again

#### Scenario: Editor-in-chief locks an account
- **WHEN** `chief` calls `POST /api/accounts/<reader>/lock`
- **THEN** the response is `403` and `reader` stays enabled

#### Scenario: Publisher locks another publisher
- **WHEN** a publisher calls the lock for another account holding `PUBLISHER`
- **THEN** the response is `403`

#### Scenario: Lock twice
- **WHEN** `reader` is locked and the publisher calls the lock for `reader` again
- **THEN** the response is `200` with `account.enabled` `false`
