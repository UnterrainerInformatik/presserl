# admin-accounts Specification

## Purpose

Lets publishers, editors-in-chief and section editors create accounts in the administration app
and hand them over on a printed account slip, on top of the `/api/accounts` endpoints.

## Requirements

### Requirement: Accounts screen entry point
The admin app header SHALL offer "Accounts" to users whose `allowedActions` of `GET /api/me`
contain `ADMINISTER_ACCOUNTS` (the server lists it for `PUBLISHER`, `EDITOR_IN_CHIEF` and holders
of `SECTION_EDITOR` in at least one section), and SHALL NOT show it to other users. The accounts
screen SHALL offer a way back to the article list.

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

### Requirement: Printable account slip
After a successful creation or password reset the app SHALL show the account slip with the
newspaper name, the web address of the reader, the username and the generated password, together
with a note that the password is shown only now. "Print" SHALL open the browser's print dialog
for the slip alone, laid out to fit on one A4 page without the app's navigation. "Done" SHALL
return to the account list, which then contains the new account; the password SHALL NOT be shown
anywhere else.

#### Scenario: Slip after creation
- **WHEN** the publisher creates the account `lena`
- **THEN** the slip shows the newspaper name, the reader address, `lena` and the four-word password

#### Scenario: Slip after a password reset
- **WHEN** the publisher confirms "Reset password" for `reader`
- **THEN** the slip shows the newspaper name, the reader address, `reader` and the new four-word password

#### Scenario: Print the slip
- **WHEN** the publisher chooses "Print" on the slip
- **THEN** the print preview shows only the slip's content on one page

#### Scenario: Back to the list
- **WHEN** the publisher chooses "Done"
- **THEN** the account list is shown including `lena`, without any password

### Requirement: Account actions are confirmed
"Reset password", "Lock" and "Unlock" SHALL ask for confirmation naming the account before
sending `POST /api/accounts/{id}/password-reset`, `/lock` or `/unlock`; cancelling SHALL send
nothing. The reset confirmation SHALL say that the old password stops working. After a
successful lock or unlock the list SHALL show the account's new state and actions; after a reset
the slip SHALL be shown. Errors SHALL be shown as a message and leave the list unchanged.

#### Scenario: Cancel a lock
- **WHEN** the publisher chooses "Lock" for `reader` and cancels the confirmation
- **THEN** no request is sent and `reader` stays enabled

#### Scenario: Confirm a lock
- **WHEN** the publisher chooses "Lock" for `reader` and confirms
- **THEN** `reader` is marked "locked" and offers "Unlock"

#### Scenario: Refused action
- **WHEN** a confirmed action is answered with `403`
- **THEN** the app shows an error message and the list is unchanged

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

### Requirement: Account texts are localized
All texts of the accounts screens and the slip SHALL come from the German and English resources
of the admin app, following the app's language choice.

#### Scenario: English slip
- **WHEN** a user whose browser prefers English creates an account
- **THEN** the slip's labels read "Username" and "Password"

### Requirement: Trust in the account list
The account list SHALL show each account's `trusts` as "trusted by" markers with the localized
level label, for `SECTION_EDITOR` together with the section name ("Ressortleiter · Sport").
For every entry of the account's `trustScopes` the account SHALL offer a trust switch labelled with
the level (and section), on when the entry is among its `trusts`; values the app does not know
SHALL be ignored. Turning a switch on SHALL ask for confirmation naming the account and saying
that its articles will no longer wait for that level; cancelling SHALL send nothing. Turning a
switch off SHALL NOT ask. Either SHALL send `PUT /api/accounts/{id}/trust` with the entry's
`level`, `sectionId` and the new `trusted` value; on success the list SHALL show the returned
account, on error an error message and the list unchanged.

#### Scenario: Trust marker
- **WHEN** the publisher level trusts `chief` and the publisher opens the accounts screen (German browser)
- **THEN** `chief` is marked as trusted by "Herausgeber" and its trust switch for "Herausgeber" is on

#### Scenario: No switch without scope
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and opens the accounts screen while the publisher level trusts `chief`
- **THEN** `chief` shows the trust marker but no trust switch

#### Scenario: Set trust with confirmation
- **WHEN** the publisher turns on the trust switch of `chief` and confirms
- **THEN** `PUT /api/accounts/<chief>/trust` is sent with `{"level": "PUBLISHER", "sectionId": null, "trusted": true}` and `chief` is shown as trusted

#### Scenario: Cancel setting trust
- **WHEN** the publisher turns on the trust switch of `chief` and cancels the confirmation
- **THEN** no request is sent and the switch stays off

#### Scenario: Clear trust without confirmation
- **WHEN** the publisher turns off the trust switch of a trusted `chief`
- **THEN** the request with `"trusted": false` is sent at once and `chief` is no longer marked as trusted

#### Scenario: Section editor's switch
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `REPORTER` in `Sport`, and `nogroups` opens the accounts screen (German browser)
- **THEN** `reader` offers a trust switch "Ressortleiter · Sport"

#### Scenario: Refused trust change
- **WHEN** a trust change is answered with `403`
- **THEN** the app shows an error message and the switch keeps its previous state
