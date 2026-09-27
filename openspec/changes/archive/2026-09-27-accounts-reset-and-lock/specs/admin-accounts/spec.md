## MODIFIED Requirements

### Requirement: Account list
The accounts screen SHALL list the accounts from `GET /api/accounts` in the server's order, each
with username, first and last name, its localized role labels together with its section roles as
"role label · section name" (or a "no role" label when it has neither) and a "locked" marker for
disabled accounts. Each account SHALL offer exactly the actions in its `allowedActions`: "Reset
password" for `RESET_PASSWORD`, "Lock" for `LOCK`, "Unlock" for `UNLOCK`. The screen SHALL offer
"New account".

#### Scenario: List after login
- **WHEN** the publisher opens the accounts screen of the dev realm
- **THEN** `chief` is listed with "Chefredakteur" (German browser) and `nogroups` with the "no role" label

#### Scenario: Section role in the list
- **WHEN** `reader` is `REPORTER` in `Sport` and the publisher opens the accounts screen (German browser)
- **THEN** `reader` is listed with "Leser" and "Redakteur · Sport"

#### Scenario: Actions follow allowedActions
- **WHEN** `chief` opens the accounts screen
- **THEN** `reader` offers "Reset password" but neither "Lock" nor "Unlock", and `chief`'s own row offers no action

#### Scenario: Locked account
- **WHEN** `reader` is locked and the publisher opens the accounts screen
- **THEN** `reader` is marked "locked" and offers "Unlock" instead of "Lock"

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

## ADDED Requirements

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
