## MODIFIED Requirements

### Requirement: Account list
The accounts screen SHALL list the accounts from `GET /api/accounts` in the server's order, each
with username, first and last name, its localized role labels together with its section roles as
"role label · section name" (or a "no role" label when it has neither) and a "locked" marker for
disabled accounts. Each account SHALL offer exactly the actions in its `allowedActions`: "Edit
roles" for `EDIT_ROLES`, "Reset password" for `RESET_PASSWORD`, "Lock" for `LOCK`, "Unlock" for
`UNLOCK`; values the app does not know SHALL be ignored. The screen SHALL offer "New account".

#### Scenario: List after login
- **WHEN** the publisher opens the accounts screen of the dev realm
- **THEN** `chief` is listed with "Chefredakteur" (German browser) and `nogroups` with the "no role" label

#### Scenario: Section role in the list
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher opens the accounts screen (German browser)
- **THEN** `reader` is listed with "Leser" and "Redakteur · Sport"

#### Scenario: Actions follow allowedActions
- **WHEN** `chief` opens the accounts screen
- **THEN** `reader` offers "Edit roles" and "Reset password" but neither "Lock" nor "Unlock", and `chief`'s own row offers no action

#### Scenario: Locked account
- **WHEN** `reader` is locked and the publisher opens the accounts screen
- **THEN** `reader` is marked "locked" and offers "Unlock" instead of "Lock"

## ADDED Requirements

### Requirement: Edit roles in the admin app
"Edit roles" SHALL open a form naming the account and showing its current roles: the newspaper
roles as individual choices, and a choice between no role and each section role for every
section, both preset to the account's roles. Only roles the user may change SHALL be changeable:
newspaper roles from `assignableRoles`, section roles in sections whose `assignableRoles`
(`GET /api/sections`) contain both the current and the chosen role; the account's other roles
SHALL be shown read-only, and sections in which the user may assign nothing and the account holds
no role SHALL be left out. "Save" SHALL be possible only when something changed and at least one
role of either kind remains, and SHALL send `PUT /api/accounts/{id}/roles` with the complete
roles. On success the account list SHALL be shown with the account's new roles. Server errors
(`400`, `403`) SHALL be shown next to the named fields, other errors as a message, keeping the
input. "Cancel" SHALL return to the list without sending anything.

#### Scenario: Publisher promotes a reader
- **WHEN** the publisher chooses "Edit roles" for `reader`, also selects "Chefredakteur" and saves (German browser)
- **THEN** the account list shows `reader` with "Chefredakteur" and "Leser"

#### Scenario: Current roles are preset
- **WHEN** `reader` is `READER` and `REPORTER` in `Sport` and the publisher opens "Edit roles" for `reader`
- **THEN** "Leser" is selected and `Sport` shows "Redakteur" (German browser), and "Save" is not possible yet

#### Scenario: Section editor edits their reporter
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` only and opens "Edit roles" for `reader`, who is `READER` and `REPORTER` in `Sport`
- **THEN** "Leser" is shown read-only, no other section is offered, and after choosing no role for `Sport` "Save" is possible because "Leser" remains

#### Scenario: Cancel
- **WHEN** the publisher changes a role in the form and chooses "Cancel"
- **THEN** no request is sent and the list shows the account unchanged
