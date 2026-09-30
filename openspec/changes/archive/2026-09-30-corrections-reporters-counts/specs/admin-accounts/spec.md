## MODIFIED Requirements

### Requirement: Create an account in the admin app
"New account" SHALL open a form with:

- first name (required) and last name (optional);
- username;
- the newspaper roles from `assignableRoles` as individual choices, none preselected;
- when `mayAssignSectionlessReporter` is true, the choice "Redakteur (ohne Ressort)" (English
  "Reporter (no section)"), not preselected;
- for every section in which the user may assign section roles (`assignableRoles` of
  `GET /api/sections`), a choice between no role and each assignable section role, preset to no
  role.

When the first name changes and the user has not edited the username, the form SHALL fill the
username from `GET /api/accounts/username-suggestion`. "Create" SHALL be possible only with a first
name, a username, and at least one newspaper role, section role or the sectionless-reporter choice.
It SHALL send `POST /api/accounts`, including `sectionlessReporter` when the choice is offered.
Server errors (`400`, `403`, `409`) SHALL be shown next to the named fields, other errors as a
message, and the form SHALL keep the input.

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
- **THEN** the newspaper role choices are "Chefredakteur", "Leser" and "Redakteur (ohne Ressort)" only (German browser)

#### Scenario: Section editor creates a reporter
- **WHEN** a user who is `SECTION_EDITOR` in `Sport` only opens "New account" and chooses "Redakteur" for `Sport`
- **THEN** the form shows no newspaper role choices and no "Redakteur (ohne Ressort)", offers only `Sport`, and "Create" is possible once first name and username are set

#### Scenario: Editor-in-chief creates a photographer
- **WHEN** `chief` fills in first name and username, chooses only "Redakteur (ohne Ressort)" and creates the account
- **THEN** the app sends `"sectionlessReporter": true` with empty `roles` and `sectionRoles`, and shows the slip

### Requirement: Edit roles in the admin app
"Edit roles" SHALL open a form naming the account and showing its current roles. The form SHALL
contain the newspaper roles as individual choices, the "Redakteur (ohne Ressort)" choice, and a
choice between no role and each section role for every section, all preset to the account's roles.

Only roles the user may change SHALL be changeable: newspaper roles from `assignableRoles`, the
sectionless-reporter choice when `mayAssignSectionlessReporter` is true, and section roles in
sections whose `assignableRoles` (`GET /api/sections`) contain both the current and the chosen
role. The account's other roles SHALL be shown read-only. Sections in which the user may assign
nothing and the account holds no role SHALL be left out. When the user may change the
sectionless-reporter choice and removes the account's last section role while no `PUBLISHER` or
`EDITOR_IN_CHIEF` remains selected, the form SHALL select "Redakteur (ohne Ressort)", and the user
may clear it again.

"Save" SHALL be possible only when something changed and at least one role, section role or the
sectionless-reporter choice remains. It SHALL send `PUT /api/accounts/{id}/roles` with the complete
roles, and with `sectionlessReporter` only when the user may change it. On success the account list
SHALL be shown with the account's new roles. Server errors (`400`, `403`) SHALL be shown next to the
named fields, other errors as a message, keeping the input. "Cancel" SHALL return to the list
without sending anything.

#### Scenario: Publisher promotes a reader
- **WHEN** the publisher chooses "Edit roles" for `reader`, also selects "Chefredakteur" and saves (German browser)
- **THEN** the account list shows `reader` with "Chefredakteur" and "Leser"

#### Scenario: Current roles are preset
- **WHEN** `reader` is `READER` and `REPORTER` in `Sport` and the publisher opens "Edit roles" for `reader`
- **THEN** "Leser" is selected and `Sport` shows "Redakteur" (German browser), and "Save" is not possible yet

#### Scenario: Section editor edits their reporter
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` only and opens "Edit roles" for `reader`, who is `READER` and `REPORTER` in `Sport`
- **THEN** "Leser" and "Redakteur (ohne Ressort)" are shown read-only, no other section is offered, and after choosing no role for `Sport` "Save" is possible because "Leser" remains

#### Scenario: Last section removed by an editor-in-chief
- **WHEN** `chief` opens "Edit roles" for `reader`, who is `REPORTER` in `Sport` only, and chooses no role for `Sport`
- **THEN** "Redakteur (ohne Ressort)" becomes selected and saving sends `"sectionlessReporter": true`

#### Scenario: Cancel
- **WHEN** the publisher changes a role in the form and chooses "Cancel"
- **THEN** no request is sent and the list shows the account unchanged

### Requirement: Account list
The accounts screen SHALL list the accounts from `GET /api/accounts` in the server's order. Each
account SHALL show:

- username, first name and last name;
- its localized role labels, together with "Redakteur (ohne Ressort)" when it carries the
  `sectionlessReporter` marker and its section roles as "role label · section name" (or a "no
  role" label when it has none of these);
- a "locked" marker for disabled accounts.

Each account SHALL offer exactly the actions in its `allowedActions`: "Edit roles" for `EDIT_ROLES`,
"Reset password" for `RESET_PASSWORD`, "Lock" for `LOCK`, "Unlock" for `UNLOCK`. Values the app
does not know SHALL be ignored. The screen SHALL offer "New account".

#### Scenario: List after login
- **WHEN** the publisher opens the accounts screen of the dev realm
- **THEN** `chief` is listed with "Chefredakteur" (German browser) and `nogroups` with the "no role" label

#### Scenario: Section role in the list
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher opens the accounts screen (German browser)
- **THEN** `reader` is listed with "Leser" and "Redakteur · Sport"

#### Scenario: Sectionless reporter in the list
- **WHEN** `nogroups` carries the `sectionlessReporter` marker only and the publisher opens the accounts screen (German browser)
- **THEN** `nogroups` is listed with "Redakteur (ohne Ressort)"

#### Scenario: Actions follow allowedActions
- **WHEN** `chief` opens the accounts screen
- **THEN** `reader` offers "Edit roles" and "Reset password" but neither "Lock" nor "Unlock", and `chief`'s own row offers no action

#### Scenario: Locked account
- **WHEN** `reader` is locked and the publisher opens the accounts screen
- **THEN** `reader` is marked "locked" and offers "Unlock" instead of "Lock"
