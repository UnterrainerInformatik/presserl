## MODIFIED Requirements

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

## ADDED Requirements

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
