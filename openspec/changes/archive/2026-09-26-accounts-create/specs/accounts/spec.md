## Purpose

Lets publishers and editors-in-chief see and create newspaper accounts through the backend, which
manages the Keycloak users and groups, so nobody needs the Keycloak admin console.

## ADDED Requirements

### Requirement: Account endpoints require an administering role
The account endpoints under `/api/accounts` SHALL be available to users holding `PUBLISHER` or
`EDITOR_IN_CHIEF`. Other authenticated users SHALL get `403` with an empty body; requests without
a valid token SHALL get `401`.

#### Scenario: Reader lists accounts
- **WHEN** a user holding only `READER` calls `GET /api/accounts`
- **THEN** the response is `403`

#### Scenario: Editor-in-chief lists accounts
- **WHEN** a user holding `EDITOR_IN_CHIEF` calls `GET /api/accounts`
- **THEN** the response is `200`

### Requirement: List accounts
`GET /api/accounts` SHALL return every user of the realm except service-account users, sorted by
username, each with its id, username, first name, last name (empty string when unset), its
newspaper roles derived from its Keycloak groups (in the order `PUBLISHER`, `EDITOR_IN_CHIEF`,
`READER`) and whether it is enabled. The response SHALL also list the roles the requesting user
may assign (`assignableRoles`).

#### Scenario: Publisher lists accounts of the dev realm
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** the list contains `chief` with roles `["EDITOR_IN_CHIEF"]`, `reader` with `["READER"]`, `nogroups` with `[]` and the publisher with `["PUBLISHER"]`, and no user whose username starts with `service-account-`

#### Scenario: Assignable roles of a publisher
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** `assignableRoles` is `["PUBLISHER", "EDITOR_IN_CHIEF", "READER"]`

#### Scenario: Assignable roles of an editor-in-chief
- **WHEN** an editor-in-chief who is not a publisher calls `GET /api/accounts`
- **THEN** `assignableRoles` is `["EDITOR_IN_CHIEF", "READER"]`

### Requirement: Username derived from the first name
`GET /api/accounts/username-suggestion?firstName=<name>` SHALL return a username that is not yet
taken, derived from the first name as follows: lower case; `ä`→`ae`, `ö`→`oe`, `ü`→`ue`, `ß`→`ss`;
other letters with diacritics reduced to their base letter; every run of characters outside
`a-z0-9` replaced by a single `-`; leading and trailing `-` removed; cut to 32 characters. An empty
result SHALL become `user`. When the result is taken, the system SHALL append `-2`, `-3`, … and
return the first free one (shortening the base so the whole stays within 32 characters).

#### Scenario: Umlaut and space
- **WHEN** the publisher asks for a suggestion for `Jürgen Maria`
- **THEN** the response is `{"username": "juergen-maria"}`

#### Scenario: Collision
- **WHEN** users `anna` and `anna-2` exist and the publisher asks for a suggestion for `Anna`
- **THEN** the response is `{"username": "anna-3"}`

#### Scenario: Nothing usable left
- **WHEN** the publisher asks for a suggestion for `李` and no user `user` exists
- **THEN** the response is `{"username": "user"}`

#### Scenario: Missing first name
- **WHEN** the publisher asks for a suggestion without `firstName` or with a blank one
- **THEN** the response is `400` naming the field `firstName`

### Requirement: Create an account
`POST /api/accounts` with `firstName`, optional `lastName`, `username` and `roles` SHALL create an
enabled Keycloak user with these names, make it a member of the Keycloak group of every given
role, set a newly generated default password (not temporary) and answer `201` with a `Location`
header `/api/accounts/{id}` and a body containing the account (as in the list) and the generated
`password`. The password SHALL appear in this response only; the system SHALL NOT store or log
it. When the account cannot be created completely, no user SHALL remain in Keycloak.

#### Scenario: Publisher creates the editor-in-chief
- **WHEN** the publisher posts `{"firstName": "Lena", "username": "lena", "roles": ["EDITOR_IN_CHIEF"]}`
- **THEN** the response is `201` with `account.username` `lena`, `account.roles` `["EDITOR_IN_CHIEF"]`, `account.enabled` `true` and a `password` of four dash-joined words, and `lena` can log in with that password and gets `roles` `["EDITOR_IN_CHIEF"]` from `GET /api/me`

#### Scenario: Several roles
- **WHEN** the publisher creates an account with `roles` `["READER", "PUBLISHER"]`
- **THEN** the account is a member of both groups and `account.roles` is `["PUBLISHER", "READER"]`

#### Scenario: Password is not logged
- **WHEN** an account is created
- **THEN** the backend log names the new username, the creator and the roles, and does not contain the password

### Requirement: Account input is validated
The system SHALL answer `POST /api/accounts` with `400` and an error body listing every violation
when: `firstName` is missing, blank or longer than 100 characters; `lastName` is longer than 100
characters; a name contains control characters; `username` does not match
`^[a-z0-9]+(-[a-z0-9]+)*$`, is longer than 32 characters or starts with `service-account-`;
`roles` is missing or empty or contains an unknown value; or the body has unknown fields. Names
SHALL be stored trimmed; duplicate roles SHALL be collapsed. A username that is already taken
(ignoring case) SHALL be answered with `409` naming the field `username`.

#### Scenario: Invalid username and no roles
- **WHEN** the publisher posts `{"firstName": "Max", "username": "Max Mustermann", "roles": []}`
- **THEN** the response is `400` with errors for the fields `username` and `roles`, and no user is created

#### Scenario: Username taken
- **WHEN** the publisher posts an account with `username` `chief`
- **THEN** the response is `409` with an error for the field `username`, and the existing `chief` is unchanged

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

### Requirement: Unavailable Keycloak is reported
When Keycloak is unreachable or refuses the backend's service account during an account request,
the system SHALL answer `503` with an error body stating that the account service is unavailable.

#### Scenario: Keycloak down
- **WHEN** the publisher calls `GET /api/accounts` while Keycloak is unreachable
- **THEN** the response is `503` with an error body
