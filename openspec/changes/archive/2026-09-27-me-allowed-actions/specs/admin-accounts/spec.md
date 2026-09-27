## MODIFIED Requirements

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
