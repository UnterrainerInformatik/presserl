## Purpose

Lets publishers and editors-in-chief create accounts in the administration app and hand them over
on a printed account slip, on top of the `/api/accounts` endpoints.

## ADDED Requirements

### Requirement: Accounts screen entry point
The admin app header SHALL offer "Accounts" to users holding `PUBLISHER` or `EDITOR_IN_CHIEF` and
SHALL NOT show it to other users. The accounts screen SHALL offer a way back to the article list.

#### Scenario: Publisher opens accounts
- **WHEN** the publisher chooses "Accounts" in the header
- **THEN** the account list is shown

#### Scenario: Reader has no accounts entry
- **WHEN** a user holding only `READER` is logged in to the admin app
- **THEN** the header shows no "Accounts" entry

### Requirement: Account list
The accounts screen SHALL list the accounts from `GET /api/accounts` in the server's order, each
with username, first and last name, its localized role labels (or a "no role" label) and a marker
for disabled accounts. It SHALL offer "New account".

#### Scenario: List after login
- **WHEN** the publisher opens the accounts screen of the dev realm
- **THEN** `chief` is listed with "Chefredakteur" (German browser) and `nogroups` with the "no role" label

### Requirement: Create an account in the admin app
"New account" SHALL open a form with first name (required), last name (optional), username and the
roles from `assignableRoles` as individual choices, none preselected. When the first name changes
and the user has not edited the username, the form SHALL fill the username from
`GET /api/accounts/username-suggestion`. "Create" SHALL be possible only with a first name, a
username and at least one role, and SHALL send `POST /api/accounts`. Server errors (`400`, `403`,
`409`) SHALL be shown next to the named fields, other errors as a message; the form SHALL keep the
input.

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
- **THEN** the role choices are "Chefredakteur" and "Leser" only (German browser)

### Requirement: Printable account slip
After a successful creation the app SHALL show the account slip with the newspaper name, the web
address of the reader, the username and the generated password, together with a note that the
password is shown only now. "Print" SHALL open the browser's print dialog for the slip alone, laid
out to fit on one A4 page without the app's navigation. "Done" SHALL return to the account list,
which then contains the new account; the password SHALL NOT be shown anywhere else.

#### Scenario: Slip after creation
- **WHEN** the publisher creates the account `lena`
- **THEN** the slip shows the newspaper name, the reader address, `lena` and the four-word password

#### Scenario: Print the slip
- **WHEN** the publisher chooses "Print" on the slip
- **THEN** the print preview shows only the slip's content on one page

#### Scenario: Back to the list
- **WHEN** the publisher chooses "Done"
- **THEN** the account list is shown including `lena`, without any password

### Requirement: Account texts are localized
All texts of the accounts screens and the slip SHALL come from the German and English resources
of the admin app, following the app's language choice.

#### Scenario: English slip
- **WHEN** a user whose browser prefers English creates an account
- **THEN** the slip's labels read "Username" and "Password"
