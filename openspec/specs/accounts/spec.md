# accounts Specification

## Purpose

Lets publishers, editors-in-chief and section editors see and create newspaper accounts through
the backend, which manages the Keycloak users and groups, so nobody needs the Keycloak admin
console. Section editors create accounts for their own sections only.

## Requirements

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

### Requirement: Username derived from the first name
`GET /api/accounts/username-suggestion?firstName=<name>` SHALL return a username that is not yet
taken, derived from the first name as follows: lower case; `ä`→`ae`, `ö`→`oe`, `ü`→`ue`, `ß`→`ss`;
other letters with diacritics reduced to their base letter; every run of characters outside
`a-z0-9` replaced by a single `-`; leading and trailing `-` removed; cut to 32 characters. An empty
result SHALL become `user`. When the result is at least 3 characters long and taken, the system
SHALL append `-2`, `-3`, … and return the first free one (shortening the base so the whole stays
within 32 characters). When the result is shorter than 3 characters, the system SHALL never return
it unchanged but append `-1`, `-2`, `-3`, … and return the first free one. A suggestion SHALL
always satisfy the username rules of `POST /api/accounts`.

#### Scenario: Umlaut and space
- **WHEN** the publisher asks for a suggestion for `Jürgen Maria`
- **THEN** the response is `{"username": "juergen-maria"}`

#### Scenario: Collision
- **WHEN** users `anna` and `anna-2` exist and the publisher asks for a suggestion for `Anna`
- **THEN** the response is `{"username": "anna-3"}`

#### Scenario: Short first name
- **WHEN** no user `li-1` exists and the publisher asks for a suggestion for `Li`
- **THEN** the response is `{"username": "li-1"}`

#### Scenario: Short first name with collision
- **WHEN** user `li-1` exists and no user `li-2` exists and the publisher asks for a suggestion for `Li`
- **THEN** the response is `{"username": "li-2"}`

#### Scenario: Three characters are enough
- **WHEN** no user `max` exists and the publisher asks for a suggestion for `Max`
- **THEN** the response is `{"username": "max"}`

#### Scenario: Nothing usable left
- **WHEN** the publisher asks for a suggestion for `李` and no user `user` exists
- **THEN** the response is `{"username": "user"}`

#### Scenario: Missing first name
- **WHEN** the publisher asks for a suggestion without `firstName` or with a blank one
- **THEN** the response is `400` naming the field `firstName`

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

### Requirement: Newspaper roles are assigned at or below the own level
A publisher SHALL be allowed to assign `PUBLISHER`, `EDITOR_IN_CHIEF` and `READER`; an
editor-in-chief SHALL be allowed to assign `EDITOR_IN_CHIEF` and `READER`. A request containing a
role the requesting user may not assign SHALL be answered with `403` and an error body naming the
field `roles`, and no user SHALL be created.

#### Scenario: Editor-in-chief tries to create a publisher
- **WHEN** an editor-in-chief who is not a publisher posts an account with `roles` `["PUBLISHER"]`
- **THEN** the response is `403` naming the field `roles`, and no user is created

#### Scenario: Editor-in-chief creates a reader
- **WHEN** an editor-in-chief posts an account with `roles` `["READER"]`
- **THEN** the response is `201`

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

### Requirement: Default password is a four-word pass-phrase
The generated default password SHALL consist of four words chosen independently and uniformly
with a cryptographically secure random source from a curated list of at least 1000 distinct,
kid-friendly German words, joined by `-`. Every word of the list SHALL be 3 to 8 characters long
and consist of the letters `a-z` only.

#### Scenario: Shape of the password
- **WHEN** an account is created
- **THEN** its password matches `^[a-z]{3,8}(-[a-z]{3,8}){3}$` and each of the four words is on the word list

#### Scenario: Passwords differ
- **WHEN** two accounts are created one after the other
- **THEN** their passwords differ

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

### Requirement: Unavailable Keycloak is reported
When Keycloak is unreachable or refuses the backend's service account during an account request,
the system SHALL answer `503` with an error body stating that the account service is unavailable.

#### Scenario: Keycloak down
- **WHEN** the publisher calls `GET /api/accounts` while Keycloak is unreachable
- **THEN** the response is `503` with an error body
