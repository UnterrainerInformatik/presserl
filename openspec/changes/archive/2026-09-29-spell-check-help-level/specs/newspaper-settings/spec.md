## MODIFIED Requirements

### Requirement: Layered resolution of newspaper settings
The system SHALL resolve each newspaper setting from the layers code default, deployment
(environment variable) and newspaper (database override), where a later layer wins over an
earlier one. The database layer SHALL store only overrides; a setting without an override SHALL
fall back to the deployment value or, if none is set, to the code default.

The settings resolved in this change and their code defaults are:
`name` = `My Newspaper`, `subtitle` = empty, `visibility` = `public`,
`retract.author-can-retract` = `true`, `section.default` = `General`,
`editor.level` = `standard`, `reader.text-size` = `m`, `media.max-size` = `10M`,
`spell-check.help` = `suggestions` (deployment variable `PRESSERL_SPELL_CHECK_HELP`).

#### Scenario: Fresh installation uses code defaults
- **WHEN** no environment variable and no database override is set for any setting
- **THEN** every setting resolves to its code default

#### Scenario: Deployment variable overrides the code default
- **WHEN** `PRESSERL_NEWSPAPER_NAME` is set to `Die Zwergenpost` and no database override exists
- **THEN** the effective name is `Die Zwergenpost`

#### Scenario: Database override wins over the deployment variable
- **WHEN** `PRESSERL_NEWSPAPER_NAME` is `Die Zwergenpost` and the newspaper row overrides the name with `Zwergenpost Extra`
- **THEN** the effective name is `Zwergenpost Extra`

#### Scenario: Deployment chooses the spell-check help
- **WHEN** `PRESSERL_SPELL_CHECK_HELP=messages` is set and no database override exists
- **THEN** the effective `spell-check.help` is `messages`

### Requirement: Invalid enumerated settings are rejected at startup
The system SHALL refuse to start when a deployment variable for an enumerated setting holds a
value outside its allowed set (`visibility`: `public`, `private`; `editor.level`: `starter`,
`standard`, `profi`; `reader.text-size`: `s`, `m`, `l`, `xl`; `spell-check.help`: `suggestions`,
`messages`, `marks`), and SHALL name the offending variable and the allowed values in the error
message.

#### Scenario: Unknown visibility value
- **WHEN** the backend starts with `PRESSERL_NEWSPAPER_VISIBILITY=secret`
- **THEN** startup fails with an error naming `PRESSERL_NEWSPAPER_VISIBILITY` and the values `public`, `private`

#### Scenario: Unknown spell-check help value
- **WHEN** the backend starts with `PRESSERL_SPELL_CHECK_HELP=hints`
- **THEN** startup fails with an error naming `PRESSERL_SPELL_CHECK_HELP` and the values `suggestions`, `messages`, `marks`

### Requirement: Newspaper endpoint returns effective settings
The system SHALL expose `GET /api/newspaper` without authentication and return the effective
`name`, `subtitle`, `visibility`, a `settings` object containing the remaining resolved settings
keyed by their setting name, and an `overrides` object containing, keyed by setting name, the
values of `settings` that currently come from a valid newspaper override (`{}` when none), as
`application/json`.

#### Scenario: Anonymous request on a fresh installation
- **WHEN** an anonymous client calls `GET /api/newspaper` on a fresh installation
- **THEN** the response is `200` with `name` `My Newspaper`, `subtitle` `""`, `visibility` `public`, `settings` containing `retract.author-can-retract: true`, `section.default: "General"`, `editor.level: "standard"`, `reader.text-size: "m"`, `media.max-size: "10M"`, `spell-check.help: "suggestions"`, and `overrides` equal to `{}`

#### Scenario: Request with a valid bearer token
- **WHEN** an authenticated client calls `GET /api/newspaper`
- **THEN** the response is identical to the anonymous one

#### Scenario: Overridden text size
- **WHEN** `PRESSERL_READER_TEXT_SIZE=l` is set and the newspaper overrides `reader.text-size` with `xl`
- **THEN** `settings` contains `reader.text-size: "xl"` and `overrides` equals `{"reader.text-size": "xl"}`

### Requirement: Newspaper settings can be overridden
The system SHALL expose `PUT /api/newspaper/settings` to users holding `PUBLISHER` or
`EDITOR_IN_CHIEF`. The body SHALL be a JSON object whose keys are writable setting names —
`reader.text-size` and `spell-check.help` — and whose values are either an allowed value of that
setting, which stores it as newspaper override, or `null`, which removes the override so the
deployment value or code default applies again. `spell-check.help` SHALL be writable by users
holding `PUBLISHER` only. Keys not in the body SHALL stay unchanged. The update SHALL be all or
nothing. On success the response SHALL be `200` with the same body as `GET /api/newspaper` after
the change. A body with an unknown or non-writable key, or with a value outside the allowed set,
SHALL be refused with `400` and an error naming the offending key; a request without a valid
token SHALL get `401`, a user without `PUBLISHER` or `EDITOR_IN_CHIEF` SHALL get `403`, and a
user without `PUBLISHER` whose body contains `spell-check.help` SHALL get `403` with nothing
stored.

#### Scenario: Publisher sets the default text size
- **WHEN** the publisher puts `{"reader.text-size": "l"}` on a fresh installation
- **THEN** the response is `200` with `settings` containing `reader.text-size: "l"` and `overrides` equal to `{"reader.text-size": "l"}`, and the next `GET /api/newspaper` answers the same

#### Scenario: Clearing the override
- **WHEN** the newspaper overrides `reader.text-size` with `l`, `PRESSERL_READER_TEXT_SIZE` is not set, and the editor-in-chief puts `{"reader.text-size": null}`
- **THEN** the response is `200` with `reader.text-size: "m"` and `overrides` equal to `{}`

#### Scenario: Invalid value
- **WHEN** the publisher puts `{"reader.text-size": "huge"}`
- **THEN** the response is `400` with an error for the field `reader.text-size` and the stored override is unchanged

#### Scenario: Setting that is not writable
- **WHEN** the publisher puts `{"media.max-size": "50M"}`
- **THEN** the response is `400` with an error for the field `media.max-size`

#### Scenario: Reader is refused
- **WHEN** a user holding only `READER` puts `{"reader.text-size": "l"}`
- **THEN** the response is `403` and no override is stored

#### Scenario: Anonymous request
- **WHEN** a client without token puts `{"reader.text-size": "l"}`
- **THEN** the response is `401`

#### Scenario: Publisher limits the spell-check help
- **WHEN** the publisher puts `{"spell-check.help": "marks"}`
- **THEN** the response is `200` with `settings` containing `spell-check.help: "marks"` and `overrides` containing `spell-check.help: "marks"`

#### Scenario: Editor-in-chief may not change the spell-check help
- **WHEN** a user holding only `EDITOR_IN_CHIEF` puts `{"reader.text-size": "l", "spell-check.help": "suggestions"}`
- **THEN** the response is `403` with an empty body, and neither `reader.text-size` nor `spell-check.help` is changed

#### Scenario: Invalid spell-check help
- **WHEN** the publisher puts `{"spell-check.help": "hints"}`
- **THEN** the response is `400` with an error for the field `spell-check.help` naming `suggestions`, `messages`, `marks`
