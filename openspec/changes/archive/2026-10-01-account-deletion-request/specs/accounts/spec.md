## MODIFIED Requirements

### Requirement: List accounts
`GET /api/accounts` SHALL return every user of the realm except service-account users, sorted by
username. Each account SHALL carry:

- its id, username, first name and last name (empty string when unset);
- its newspaper roles derived from its Keycloak groups (in the order `PUBLISHER`,
  `EDITOR_IN_CHIEF`, `READER`);
- its section roles (`sectionRoles`: `sectionId`, `role`, ordered by section position);
- whether it carries the `sectionlessReporter` marker;
- whether it is enabled;
- when it requested its own deletion (`deletionRequestedAt`, ISO-8601 instant, `null` without a
  pending request);
- its trust entries (`trusts`: `level`, `sectionId`, ordered `PUBLISHER`, `EDITOR_IN_CHIEF`, then
  `SECTION_EDITOR` entries by section position);
- the trust entries the requesting user may set or clear on it (`trustScopes`, same shape and
  order, following the trust rules);
- the actions the requesting user may perform on it now (`allowedActions`, in the order
  `EDIT_ROLES`, `RESET_PASSWORD`, `LOCK`, `UNLOCK`, `DELETE`, following the role-editing,
  password-reset, lock and deletion rules). `LOCK` SHALL be listed only for enabled accounts and
  `UNLOCK` only for disabled ones.

The response SHALL also list the newspaper roles the requesting user may assign (`assignableRoles`)
and whether they may assign the marker (`mayAssignSectionlessReporter`). Every other account
endpoint that answers with an account SHALL carry the same fields computed for the requesting
user.

#### Scenario: Publisher lists accounts of the dev realm
- **WHEN** the publisher calls `GET /api/accounts`
- **THEN** the list contains `chief` with roles `["EDITOR_IN_CHIEF"]`, `reader` with `["READER"]`, `nogroups` with `[]` and the publisher with `["PUBLISHER"]`, each with `sectionlessReporter` `false` and `deletionRequestedAt` `null`, and no user whose username starts with `service-account-`

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
- **THEN** `chief`, `reader` and `nogroups` have `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD", "LOCK", "DELETE"]` and the publisher's own account has `[]`

#### Scenario: Allowed actions on a locked account
- **WHEN** `reader` is locked and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD", "UNLOCK", "DELETE"]`

#### Scenario: Allowed actions of an editor-in-chief
- **WHEN** `chief` calls `GET /api/accounts`
- **THEN** `reader` and `nogroups` have `allowedActions` `["EDIT_ROLES", "RESET_PASSWORD"]`, and `chief` and the publisher have `[]`

#### Scenario: Pending deletion request in the list
- **WHEN** `reader` has requested its deletion and the publisher calls `GET /api/accounts`
- **THEN** `reader` has `deletionRequestedAt` set to the time of the request

## ADDED Requirements

### Requirement: Request deletion of one's own account
Every authenticated user SHALL be able to request the deletion of their own account with
`POST /api/me/deletion-request` and to withdraw the request with
`DELETE /api/me/deletion-request`, whatever their roles. A request SHALL delete nothing: it is
stored with its time until a publisher deletes the account or the user withdraws it. Both calls
SHALL be idempotent and answer `200` with `{"deletionRequestedAt": <instant or null>}`; a repeated
request SHALL keep the time of the first one. `GET /api/me` SHALL carry `deletionRequestedAt` the
same way. Both calls SHALL be logged at INFO with the username. Service accounts SHALL get `403`.

#### Scenario: Reporter requests deletion
- **WHEN** `reader` calls `POST /api/me/deletion-request`
- **THEN** the response is `200` with a `deletionRequestedAt` instant, `GET /api/me` for `reader` carries the same instant, and `reader` can still log in and work

#### Scenario: Request twice
- **WHEN** `reader` has requested deletion and calls `POST /api/me/deletion-request` again
- **THEN** the response is `200` with the instant of the first request

#### Scenario: Withdraw a request
- **WHEN** `reader` has requested deletion and calls `DELETE /api/me/deletion-request`
- **THEN** the response is `200` with `deletionRequestedAt` `null`, and the account list shows `reader` with `deletionRequestedAt` `null`

#### Scenario: Reader-only account requests deletion
- **WHEN** a user holding only `READER` calls `POST /api/me/deletion-request`
- **THEN** the response is `200`

### Requirement: Delete an account
`DELETE /api/accounts/{id}` SHALL delete the account. It SHALL be allowed only to users holding
`PUBLISHER`, never for their own account, and for an account holding `PUBLISHER` only when that
account has a pending deletion request and at least one other enabled account holding `PUBLISHER`
remains besides it. `allowedActions` SHALL list `DELETE` exactly when this call would be allowed.
On success the Keycloak user SHALL be removed (which ends its sessions and makes its login and
slip stop working), and the account SHALL no longer appear in `GET /api/accounts`. The call SHALL
answer `204`, `403` with an error body when refused, `404` for an unknown id or a service account,
`503` when Keycloak is unavailable, and follow the access rule of the account endpoints. It SHALL
be logged at INFO with the deleted username and the acting user.

#### Scenario: Publisher deletes a reporter
- **WHEN** the publisher calls `DELETE /api/accounts/<reader>`
- **THEN** the response is `204`, `reader` is missing from `GET /api/accounts` and can no longer log in

#### Scenario: Deletion without a request
- **WHEN** `reader` has not requested deletion and the publisher deletes it
- **THEN** the response is `204`

#### Scenario: Editor-in-chief may not delete
- **WHEN** `chief` calls `DELETE /api/accounts/<reader>`
- **THEN** the response is `403` and `reader` still exists

#### Scenario: Own account
- **WHEN** the publisher calls `DELETE` for their own account
- **THEN** the response is `403`

#### Scenario: Publisher without a request
- **WHEN** a second publisher exists without a deletion request and the publisher calls `DELETE` for it
- **THEN** the response is `403`

#### Scenario: Publisher who requested deletion
- **WHEN** a second publisher has requested deletion and the publisher calls `DELETE` for it
- **THEN** the response is `204`

#### Scenario: Last remaining publisher
- **WHEN** publisher `A` has requested deletion and the only other publisher `B` is locked, and `B` calls `DELETE` for `A`
- **THEN** the response is `403`

### Requirement: A deleted account's content is kept anonymised
Deleting an account SHALL keep every article (in every status), revision, review and image the
account wrote, approved, rejected or uploaded. In each of them the account's name SHALL be
replaced by a neutral "former newsroom member": API responses SHALL carry `null` as username and
display name of such an author, revision author, reviewer or uploader, and the reader byline SHALL
read "ehemaliges Redaktionsmitglied" (German) / "former newsroom member" (English). Deleting SHALL
remove the account's section roles, its sectionless-reporter marker, the trust entries placed on
it and its deletion request, and SHALL withdraw every pending submission of an article it authored
(the article keeps its status and live revision, as on a withdrawal). Trust entries and section
roles the account set for others SHALL stay. Review notes and article texts SHALL stay unchanged.
Live articles SHALL stay live.

#### Scenario: Published article keeps its place
- **WHEN** `reader` wrote a published article in a live issue and is deleted
- **THEN** the article stays on the front page, its byline reads "ehemaliges Redaktionsmitglied" in a German browser, and `GET /api/articles/{id}` has `authorUsername` and `authorDisplayName` `null`

#### Scenario: Pending submission withdrawn
- **WHEN** `reader`'s draft article waits for approval and `reader` is deleted
- **THEN** the article is a draft again with no pending level and appears in nobody's "Waiting for me"

#### Scenario: Uploaded image kept
- **WHEN** `reader` uploaded an image used in a published article and is deleted
- **THEN** the image is still shown and its uploader is `null` in the media endpoints

#### Scenario: Reviews kept
- **WHEN** `chief` approved an article and is deleted
- **THEN** the article's review history still lists the approval with its level, revision and note, and the reviewer as `null`

#### Scenario: Trust placed by the deleted account stays
- **WHEN** `chief` set editor-in-chief trust on `reader` and `chief` is deleted
- **THEN** `reader` keeps that trust entry
