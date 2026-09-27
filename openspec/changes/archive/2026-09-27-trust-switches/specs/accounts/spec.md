## MODIFIED Requirements

### Requirement: List accounts
`GET /api/accounts` SHALL return every user of the realm except service-account users, sorted by
username, each with its id, username, first name, last name (empty string when unset), its
newspaper roles derived from its Keycloak groups (in the order `PUBLISHER`, `EDITOR_IN_CHIEF`,
`READER`), its section roles (`sectionRoles`: `sectionId`, `role`, ordered by section position),
whether it is enabled, its trust entries (`trusts`: `level`, `sectionId`, ordered `PUBLISHER`,
`EDITOR_IN_CHIEF`, then `SECTION_EDITOR` entries by section position), the trust entries the
requesting user may set or clear on it (`trustScopes`, same shape and order, following the trust
rules) and the actions the requesting user may perform on it now (`allowedActions`, in the order
`EDIT_ROLES`, `RESET_PASSWORD`, `LOCK`, `UNLOCK`, following the role-editing, password-reset and
lock rules). `LOCK` SHALL be listed only for enabled accounts and `UNLOCK` only for disabled ones.
The response SHALL also list the newspaper roles the requesting user may assign
(`assignableRoles`). Every other account endpoint that answers with an account SHALL carry the
same fields computed for the requesting user.

#### Scenario: Publisher lists accounts of the dev realm
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** the list contains `chief` with roles `["EDITOR_IN_CHIEF"]`, `reader` with `["READER"]`, `nogroups` with `[]` and the publisher with `["PUBLISHER"]`, and no user whose username starts with `service-account-`

#### Scenario: Section roles in the list
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `sectionRoles` `[{"sectionId": <Sport>, "role": "REPORTER"}]` and `chief` has `sectionRoles` `[]`

#### Scenario: Trust in the list
- **WHEN** the publisher level trusts `chief` and `nogroups` calls `GET /api/accounts` as `SECTION_EDITOR` in `Sport`
- **THEN** `chief` has `trusts` `[{"level": "PUBLISHER", "sectionId": null}]` and `trustScopes` `[]`

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

### Requirement: Trust entries
A trust entry SHALL consist of an approval level, a trusted account and, for the `SECTION_EDITOR`
level only, a section; for `EDITOR_IN_CHIEF` and `PUBLISHER` the section SHALL be `null`. At most
one entry SHALL exist per account, level and section. Each entry SHALL record who set it and when.
Deleting a section SHALL delete its trust entries. Changing the roles of the trusted account or
of the person who set the entry SHALL NOT delete it.

#### Scenario: Section deleted
- **WHEN** the section-editor level of `Kultur` trusts `reader` and `Kultur` is deleted
- **THEN** `reader` has no trust entry for `Kultur`

#### Scenario: Role change keeps trust
- **WHEN** the publisher level trusts `chief` and the publisher removes and re-adds `EDITOR_IN_CHIEF` for `chief`
- **THEN** `chief` still has `trusts` `[{"level": "PUBLISHER", "sectionId": null}]`

### Requirement: Trust is set by the own highest level on a person below
A user's trust level SHALL be their own highest approving level: `PUBLISHER` if they hold
`PUBLISHER`; else `EDITOR_IN_CHIEF` if they hold `EDITOR_IN_CHIEF`; else `SECTION_EDITOR` in each
section in which they are `SECTION_EDITOR`; other users have none. A user MAY set trust only at
their trust level (for `SECTION_EDITOR` only in their own sections), never on their own account,
and only on an account below that level that writes there:
- `PUBLISHER`: the account does not hold `PUBLISHER` and holds `EDITOR_IN_CHIEF` or at least one
  section role;
- `EDITOR_IN_CHIEF`: the account holds neither `PUBLISHER` nor `EDITOR_IN_CHIEF` and holds at least
  one section role;
- `SECTION_EDITOR` in section *S*: the account holds neither `PUBLISHER` nor `EDITOR_IN_CHIEF` and
  is `REPORTER` in *S*.

A user MAY clear an existing entry at their trust level (for `SECTION_EDITOR` in their own
sections) on any account but their own, whoever set it and whether or not the account is still
below. `trustScopes` SHALL list exactly the entries the user may set or clear on the account.
Locked accounts SHALL be treated like enabled ones.

#### Scenario: Publisher's trust scopes
- **WHEN** `reader` is `READER` and `REPORTER` in `Sport` and the publisher calls `GET /api/accounts`
- **THEN** `chief` and `reader` have `trustScopes` `[{"level": "PUBLISHER", "sectionId": null}]`, and `nogroups`, who holds no role, and the publisher's own account have `[]`

#### Scenario: Editor-in-chief's trust scopes
- **WHEN** `reader` is `REPORTER` in `Sport` and `chief` calls `GET /api/accounts`
- **THEN** `reader` has `trustScopes` `[{"level": "EDITOR_IN_CHIEF", "sectionId": null}]`, and the publisher and `chief` have `[]`

#### Scenario: Section editor's trust scopes
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` only, `reader` is `REPORTER` in `Sport` and `Kultur`, and `nogroups` calls `GET /api/accounts`
- **THEN** `reader` has `trustScopes` `[{"level": "SECTION_EDITOR", "sectionId": <Sport>}]`

#### Scenario: Editor-in-chief does not trust for the section-editor level
- **WHEN** `chief` holds `EDITOR_IN_CHIEF` and is `SECTION_EDITOR` in `Sport`, `reader` is `REPORTER` in `Sport`, and `chief` calls `GET /api/accounts`
- **THEN** `reader`'s `trustScopes` contain no `SECTION_EDITOR` entry

#### Scenario: Stale entry can still be cleared
- **WHEN** the editor-in-chief level trusts `reader` and `reader` has since become `EDITOR_IN_CHIEF`, and another editor-in-chief calls `GET /api/accounts`
- **THEN** `reader`'s `trustScopes` contain `{"level": "EDITOR_IN_CHIEF", "sectionId": null}`

### Requirement: Set or clear trust
`PUT /api/accounts/{id}/trust` SHALL take a JSON body `{"level": ..., "sectionId": ..., "trusted":
true|false}` and set (`true`) or clear (`false`) that trust entry. `level` SHALL be one of
`SECTION_EDITOR`, `EDITOR_IN_CHIEF`, `PUBLISHER`; `sectionId` SHALL be an existing section for
`SECTION_EDITOR` and `null` or absent otherwise; `trusted` SHALL be a boolean; unknown fields SHALL
be rejected. Violations SHALL be answered with `400` naming every offending field. When the entry
is not among the requesting user's `trustScopes` for the account, the response SHALL be `403` with
an error body. The endpoint SHALL be idempotent and answer `200` with the account (as listed,
computed for the requesting user). Setting SHALL record the requesting user and the time; an
entry that already exists SHALL be left unchanged. Every actual change SHALL be logged at INFO
with the account, the level, the section and the acting user. It SHALL answer `404` for an
unknown id or a service account and follow the access rule of the account endpoints. Setting or
clearing trust SHALL NOT change any article.

#### Scenario: Publisher trusts the editor-in-chief
- **WHEN** the publisher puts `{"level": "PUBLISHER", "trusted": true}` on `chief`
- **THEN** the response is `200` with `trusts` `[{"level": "PUBLISHER", "sectionId": null}]`

#### Scenario: Another publisher clears it
- **WHEN** the publisher level trusts `chief`, set by one publisher, and a second publisher puts `{"level": "PUBLISHER", "trusted": false}` on `chief`
- **THEN** the response is `200` with `trusts` `[]`

#### Scenario: Section editor trusts their reporter
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `REPORTER` in `Sport`, and `nogroups` puts `{"level": "SECTION_EDITOR", "sectionId": <Sport>, "trusted": true}` on `reader`
- **THEN** the response is `200` and `reader`'s `trusts` contain `{"level": "SECTION_EDITOR", "sectionId": <Sport>}`

#### Scenario: Trust at a level that is not the own
- **WHEN** the publisher puts `{"level": "EDITOR_IN_CHIEF", "trusted": true}` on `reader`
- **THEN** the response is `403` and `reader` has no trust entry

#### Scenario: Section editor outside their section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` only and puts `{"level": "SECTION_EDITOR", "sectionId": <Kultur>, "trusted": true}` on `reader`, who is `REPORTER` in `Kultur`
- **THEN** the response is `403`

#### Scenario: Trust on oneself
- **WHEN** `chief` puts `{"level": "EDITOR_IN_CHIEF", "trusted": true}` on their own account
- **THEN** the response is `403`

#### Scenario: Trust on a person not below
- **WHEN** `chief` puts `{"level": "EDITOR_IN_CHIEF", "trusted": true}` on another account holding `EDITOR_IN_CHIEF`
- **THEN** the response is `403`

#### Scenario: Section missing
- **WHEN** a section editor puts `{"level": "SECTION_EDITOR", "trusted": true}` without `sectionId`
- **THEN** the response is `400` naming `sectionId`

#### Scenario: Section with a newspaper-wide level
- **WHEN** the publisher puts `{"level": "PUBLISHER", "sectionId": <Sport>, "trusted": true}` on `chief`
- **THEN** the response is `400` naming `sectionId`

#### Scenario: Set twice
- **WHEN** the publisher level already trusts `chief` and the publisher puts `{"level": "PUBLISHER", "trusted": true}` on `chief` again
- **THEN** the response is `200` and the entry keeps its original setter and time

#### Scenario: Unknown account
- **WHEN** the publisher puts trust on an id that does not exist
- **THEN** the response is `404`
