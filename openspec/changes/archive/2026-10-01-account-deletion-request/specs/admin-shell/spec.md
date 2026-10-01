## ADDED Requirements

### Requirement: User line opens My account
The header's user line (display name and roles) SHALL be a button that opens the "My account"
screen, for every logged-in user, independent of `allowedActions` and of the number of navigation
entries. A pending deletion request (`deletionRequestedAt` of `GET /api/me`) SHALL be marked on the
user line.

#### Scenario: Sectionless reporter opens My account
- **WHEN** a user whose `allowedActions` are `["USE_MEDIA"]` taps the user line
- **THEN** the "My account" screen is shown

#### Scenario: Pending request marked
- **WHEN** a user with a pending deletion request is logged in
- **THEN** the user line shows the "deletion requested" marker
