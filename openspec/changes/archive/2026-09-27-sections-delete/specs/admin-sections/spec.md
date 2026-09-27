## MODIFIED Requirements

### Requirement: Section list
The sections screen SHALL list the sections from `GET /api/sections` in the server's order, each
with a marker in its colour and its name. When `canManage` is true it SHALL offer "New section",
"Edit", "Delete" and moving each section one place up or down (sending `PUT /api/sections/order`).
A section whose `assignableRoles` is not empty SHALL open its members screen; other sections SHALL
not be selectable. With no sections the screen SHALL show a hint that there are none yet.

#### Scenario: Move a section up
- **WHEN** `chief` moves `Kultur` (second) one place up
- **THEN** the list shows `Kultur` before `Sport`, also after reloading

#### Scenario: Section editor sees the list
- **WHEN** a user who is `SECTION_EDITOR` in `Sport` only opens the sections screen
- **THEN** all sections are listed, only `Sport` can be opened, and there is no "New section", "Edit", "Delete" or moving

## ADDED Requirements

### Requirement: Delete a section in the admin app
"Delete" SHALL ask for confirmation inside the app, naming the section, and then send
`DELETE /api/sections/{id}`. On success the section list SHALL be reloaded. A `409` SHALL be shown
as a localized message that the section still contains articles and these have to be moved to
another section first; other errors SHALL be shown as a message. In both cases the list SHALL be
reloaded.

#### Scenario: Delete an empty section
- **WHEN** `chief` chooses "Delete" at `Wetter`, which has no articles, and confirms
- **THEN** `Wetter` is no longer listed

#### Scenario: Cancel the confirmation
- **WHEN** `chief` chooses "Delete" at `Wetter` and cancels
- **THEN** no request is sent and `Wetter` is still listed

#### Scenario: Section with articles
- **WHEN** `chief` deletes `Sport`, which contains articles, and confirms
- **THEN** the screen shows that `Sport` still contains articles that must be moved first, and `Sport` is still listed
