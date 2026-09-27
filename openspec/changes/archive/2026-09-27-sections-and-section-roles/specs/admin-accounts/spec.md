## MODIFIED Requirements

### Requirement: Accounts screen entry point
The admin app header SHALL offer "Accounts" to users holding `PUBLISHER` or `EDITOR_IN_CHIEF` or
holding `SECTION_EDITOR` in at least one section (per `GET /api/me`), and SHALL NOT show it to
other users. The accounts screen SHALL offer a way back to the article list.

#### Scenario: Publisher opens accounts
- **WHEN** the publisher chooses "Accounts" in the header
- **THEN** the account list is shown

#### Scenario: Reader has no accounts entry
- **WHEN** a user holding only `READER` is logged in to the admin app
- **THEN** the header shows no "Accounts" entry

#### Scenario: Section editor has an accounts entry
- **WHEN** a user who is `SECTION_EDITOR` in `Sport` and holds no newspaper role is logged in
- **THEN** the header shows "Accounts"

### Requirement: Account list
The accounts screen SHALL list the accounts from `GET /api/accounts` in the server's order, each
with username, first and last name, its localized role labels together with its section roles as
"role label · section name" (or a "no role" label when it has neither) and a marker for disabled
accounts. It SHALL offer "New account".

#### Scenario: List after login
- **WHEN** the publisher opens the accounts screen of the dev realm
- **THEN** `chief` is listed with "Chefredakteur" (German browser) and `nogroups` with the "no role" label

#### Scenario: Section role in the list
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher opens the accounts screen (German browser)
- **THEN** `reader` is listed with "Leser" and "Redakteur · Sport"

### Requirement: Create an account in the admin app
"New account" SHALL open a form with first name (required), last name (optional), username, the
newspaper roles from `assignableRoles` as individual choices, none preselected, and — for every
section in which the user may assign section roles (`assignableRoles` of `GET /api/sections`) —
a choice between no role and each assignable section role, preset to no role. When the first
name changes and the user has not edited the username, the form SHALL fill the username from
`GET /api/accounts/username-suggestion`. "Create" SHALL be possible only with a first name, a
username and at least one newspaper or section role, and SHALL send `POST /api/accounts`. Server
errors (`400`, `403`, `409`) SHALL be shown next to the named fields, other errors as a message;
the form SHALL keep the input.

#### Scenario: Username follows the first name
- **WHEN** the publisher types `Anna` into the first name while `anna` already exists
- **THEN** the username field shows `anna-2`

#### Scenario: Edited username is kept
- **WHEN** the publisher changes the username to `annika` and then edits the first name
- **THEN** the username stays `annika`

#### Scenario: Username taken meanwhile
- **WHEN** "Create" is answered with `409` for `username`
- **THEN** the form shows the message at the username field and keeps all input

#### Scenario: Editor-in-chief sees only assignable roles
- **WHEN** an editor-in-chief opens "New account"
- **THEN** the newspaper role choices are "Chefredakteur" and "Leser" only (German browser)

#### Scenario: Section editor creates a reporter
- **WHEN** a user who is `SECTION_EDITOR` in `Sport` only opens "New account" and chooses "Redakteur" for `Sport`
- **THEN** the form shows no newspaper role choices, offers only `Sport`, and "Create" is possible once first name and username are set
