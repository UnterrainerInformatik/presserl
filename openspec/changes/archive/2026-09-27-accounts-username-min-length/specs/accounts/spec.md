## MODIFIED Requirements

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

### Requirement: Account input is validated
The system SHALL answer `POST /api/accounts` with `400` and an error body listing every violation
when: `firstName` is missing, blank or longer than 100 characters; `lastName` is longer than 100
characters; a name contains control characters; `username` does not match
`^[a-z0-9]+(-[a-z0-9]+)*$`, is shorter than 3 or longer than 32 characters or starts with
`service-account-`; `roles` is missing or empty or contains an unknown value; or the body has
unknown fields. Names SHALL be stored trimmed; duplicate roles SHALL be collapsed. A username that
is already taken (ignoring case) SHALL be answered with `409` naming the field `username`.

#### Scenario: Invalid username and no roles
- **WHEN** the publisher posts `{"firstName": "Max", "username": "Max Mustermann", "roles": []}`
- **THEN** the response is `400` with errors for the fields `username` and `roles`, and no user is created

#### Scenario: Username too short
- **WHEN** the publisher posts `{"firstName": "Li", "username": "li", "roles": ["READER"]}`
- **THEN** the response is `400` with an error for the field `username` stating the minimum of 3 characters, and no user is created

#### Scenario: Username taken
- **WHEN** the publisher posts an account with `username` `chief`
- **THEN** the response is `409` with an error for the field `username`, and the existing `chief` is unchanged
