## MODIFIED Requirements

### Requirement: Newspaper endpoint returns effective settings
The system SHALL expose `GET /api/newspaper` without authentication and return the effective
`name`, `subtitle`, `visibility`, a `settings` object containing the remaining resolved settings
keyed by their setting name, and an `overrides` object containing, keyed by setting name, the
values of `settings` that currently come from a valid newspaper override (`{}` when none), as
`application/json`.

#### Scenario: Anonymous request on a fresh installation
- **WHEN** an anonymous client calls `GET /api/newspaper` on a fresh installation
- **THEN** the response is `200` with `name` `My Newspaper`, `subtitle` `""`, `visibility` `public`, `settings` containing `retract.author-can-retract: true`, `section.default: "General"`, `editor.level: "standard"`, `reader.text-size: "m"`, `media.max-size: "10M"`, and `overrides` equal to `{}`

#### Scenario: Request with a valid bearer token
- **WHEN** an authenticated client calls `GET /api/newspaper`
- **THEN** the response is identical to the anonymous one

#### Scenario: Overridden text size
- **WHEN** `PRESSERL_READER_TEXT_SIZE=l` is set and the newspaper overrides `reader.text-size` with `xl`
- **THEN** `settings` contains `reader.text-size: "xl"` and `overrides` equals `{"reader.text-size": "xl"}`

## ADDED Requirements

### Requirement: Newspaper settings can be overridden
The system SHALL expose `PUT /api/newspaper/settings` to users holding `PUBLISHER` or
`EDITOR_IN_CHIEF`. The body SHALL be a JSON object whose keys are writable setting names — in
this change only `reader.text-size` — and whose values are either an allowed value of that
setting, which stores it as newspaper override, or `null`, which removes the override so the
deployment value or code default applies again. Keys not in the body SHALL stay unchanged. The
update SHALL be all or nothing. On success the response SHALL be `200` with the same body as
`GET /api/newspaper` after the change. A body with an unknown or non-writable key, or with a value
outside the allowed set, SHALL be refused with `400` and an error naming the offending key; a
request without a valid token SHALL get `401`, a user without `PUBLISHER` or `EDITOR_IN_CHIEF`
SHALL get `403`.

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
