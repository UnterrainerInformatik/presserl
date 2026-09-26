# newspaper-settings Specification

## Purpose

Resolves the newspaper's effective settings from the configuration layers (code default,
deployment environment, newspaper overrides in the database) and exposes them to clients.

## Requirements

### Requirement: Layered resolution of newspaper settings
The system SHALL resolve each newspaper setting from the layers code default, deployment
(environment variable) and newspaper (database override), where a later layer wins over an
earlier one. The database layer SHALL store only overrides; a setting without an override SHALL
fall back to the deployment value or, if none is set, to the code default.

The settings resolved in this change and their code defaults are:
`name` = `My Newspaper`, `subtitle` = empty, `visibility` = `public`,
`retract.author-can-retract` = `true`, `section.default` = `General`,
`editor.level` = `standard`, `reader.text-size` = `m`, `media.max-size` = `10M`.

#### Scenario: Fresh installation uses code defaults
- **WHEN** no environment variable and no database override is set for any setting
- **THEN** every setting resolves to its code default

#### Scenario: Deployment variable overrides the code default
- **WHEN** `PRESSERL_NEWSPAPER_NAME` is set to `Die Zwergenpost` and no database override exists
- **THEN** the effective name is `Die Zwergenpost`

#### Scenario: Database override wins over the deployment variable
- **WHEN** `PRESSERL_NEWSPAPER_NAME` is `Die Zwergenpost` and the newspaper row overrides the name with `Zwergenpost Extra`
- **THEN** the effective name is `Zwergenpost Extra`

### Requirement: Invalid enumerated settings are rejected at startup
The system SHALL refuse to start when a deployment variable for an enumerated setting holds a
value outside its allowed set (`visibility`: `public`, `private`; `editor.level`: `starter`,
`standard`, `profi`; `reader.text-size`: `s`, `m`, `l`, `xl`), and SHALL name the offending
variable and the allowed values in the error message.

#### Scenario: Unknown visibility value
- **WHEN** the backend starts with `PRESSERL_NEWSPAPER_VISIBILITY=secret`
- **THEN** startup fails with an error naming `PRESSERL_NEWSPAPER_VISIBILITY` and the values `public`, `private`

### Requirement: Newspaper endpoint returns effective settings
The system SHALL expose `GET /api/newspaper` without authentication and return the effective
`name`, `subtitle`, `visibility` and a `settings` object containing the remaining resolved
settings keyed by their setting name, as `application/json`.

#### Scenario: Anonymous request on a fresh installation
- **WHEN** an anonymous client calls `GET /api/newspaper` on a fresh installation
- **THEN** the response is `200` with `name` `My Newspaper`, `subtitle` `""`, `visibility` `public` and `settings` containing `retract.author-can-retract: true`, `section.default: "General"`, `editor.level: "standard"`, `reader.text-size: "m"`, `media.max-size: "10M"`

#### Scenario: Request with a valid bearer token
- **WHEN** an authenticated client calls `GET /api/newspaper`
- **THEN** the response is identical to the anonymous one
