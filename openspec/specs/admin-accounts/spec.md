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
The accounts screen SHALL list the accounts from `GET /api/accounts`, accounts with a pending
deletion request first (oldest request first), the others in the server's order. Each account
SHALL show:

- username, first name and last name;
- its localized role labels, together with "Redakteur (ohne Ressort)" when it carries the
  `sectionlessReporter` marker and its section roles as "role label · section name" (or a "no
  role" label when it has none of these);
- a "locked" marker for disabled accounts;
- a "deletion requested" marker with the request's date when `deletionRequestedAt` is set.

Each account SHALL offer exactly the actions in its `allowedActions`: "Edit roles" for `EDIT_ROLES`,
"Reset password" for `RESET_PASSWORD`, "Lock" for `LOCK`, "Unlock" for `UNLOCK`, "Delete" for
`DELETE`. Values the app does not know SHALL be ignored. The screen SHALL offer "New account".

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
- **THEN** `reader` offers "Edit roles" and "Reset password" but neither "Lock", "Unlock" nor "Delete", and `chief`'s own row offers no action

#### Scenario: Locked account
- **WHEN** `reader` is locked and the publisher opens the accounts screen
- **THEN** `reader` is marked "locked" and offers "Unlock" instead of "Lock"

#### Scenario: Deletion request first
- **WHEN** `reader` has requested deletion and the publisher opens the accounts screen
- **THEN** `reader` is listed first, marked "deletion requested" with the date, and offers "Delete"

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

### Requirement: Printable account slip
After a successful creation or password reset the app SHALL show the account slip with the
newspaper name, the web address of the reader, the username and the generated password, together
with a note that the password is shown only now, and a QR code. The QR code SHALL encode
`<reader address>/qr?u=<username>#pw=<password>` (the reader address without a trailing slash;
username and password as they are, since both consist of lowercase letters, digits and dashes
only) and SHALL be accompanied by a short localized line saying that scanning it opens the login.
"Print" SHALL open the browser's print dialog for the slip alone, laid out to fit on one A4 page
without the app's navigation, with the QR code printed sharply in black on white. "Done" SHALL
return to the account list, which then contains the new account; neither the password nor the
QR code SHALL be shown anywhere else.

#### Scenario: Slip after creation
- **WHEN** the publisher creates the account `lena`
- **THEN** the slip shows the newspaper name, the reader address, `lena`, the four-word password and a QR code

#### Scenario: Slip after a password reset
- **WHEN** the publisher confirms "Reset password" for `reader`
- **THEN** the slip shows the newspaper name, the reader address, `reader`, the new four-word password and a QR code for the new password

#### Scenario: QR code content
- **WHEN** the slip for `lena` with password `tiger-wolke-apfel-leiter` is shown on a newspaper whose reader address is `https://zeitung.example.org`
- **THEN** the QR code decodes to `https://zeitung.example.org/qr?u=lena#pw=tiger-wolke-apfel-leiter`

#### Scenario: Print the slip
- **WHEN** the publisher chooses "Print" on the slip
- **THEN** the print preview shows only the slip's content, including the QR code, on one page

#### Scenario: Back to the list
- **WHEN** the publisher chooses "Done"
- **THEN** the account list is shown including `lena`, without any password or QR code

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

### Requirement: Delete an account in the admin app
"Delete" SHALL ask for confirmation naming the account and saying that the login and slip stop
working at once, that the account's articles and images stay with the byline "former newsroom
member", and that deleting cannot be undone. Cancelling SHALL send nothing. After a confirmed
`DELETE /api/accounts/{id}` answered with `204` the account SHALL disappear from the list. Errors
SHALL be shown as a message and leave the list unchanged.

#### Scenario: Cancel a deletion
- **WHEN** the publisher chooses "Delete" for `reader` and cancels the confirmation
- **THEN** no request is sent and `reader` is still listed

#### Scenario: Confirm a deletion
- **WHEN** the publisher chooses "Delete" for `reader` and confirms
- **THEN** `reader` disappears from the list

#### Scenario: Refused deletion
- **WHEN** a confirmed deletion is answered with `403`
- **THEN** the app shows an error message and the list is unchanged

### Requirement: My account
The admin app SHALL offer every logged-in user a "My account" screen, on the web and on Android,
showing their username and display name and:

- without a pending request, "Request account deletion", explaining that the newspaper's
  publishers delete the account, that the account's articles and images stay with the byline
  "former newsroom member", and that the data stored on this device is removed by logging out;
- with a pending request (`deletionRequestedAt` of `GET /api/me`), the date of the request and
  "Withdraw request".

Both actions SHALL ask for confirmation and then send `POST` or `DELETE /api/me/deletion-request`;
the screen SHALL show the new state afterwards. For a user holding `PUBLISHER` the explanation SHALL
add that another publisher deletes the account, and that the operator of the newspaper's server
does it when no other publisher exists. The screen SHALL offer a way back.

#### Scenario: Reporter requests deletion
- **WHEN** a reporter opens "My account", chooses "Request account deletion" and confirms
- **THEN** the screen shows the date of the request and "Withdraw request", and the publisher sees `reader` marked "deletion requested"

#### Scenario: Withdraw
- **WHEN** a user with a pending request chooses "Withdraw request" and confirms
- **THEN** the screen offers "Request account deletion" again

#### Scenario: Cancel the request
- **WHEN** a user chooses "Request account deletion" and cancels the confirmation
- **THEN** no request is sent

### Requirement: Content of deleted accounts in the admin app
Wherever the admin app names an article's author, a revision's author, a reviewer or an image's
uploader, it SHALL show "ehemaliges Redaktionsmitglied" (German) / "former newsroom member"
(English) when the server sends `null` as username and display name.

#### Scenario: Article list after a deletion
- **WHEN** `reader`'s article is listed under "All articles" after `reader` was deleted (German browser)
- **THEN** the article shows the author "ehemaliges Redaktionsmitglied"
