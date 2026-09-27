# admin-sections Specification

## Purpose

Lets editors-in-chief and publishers manage the newspaper's sections in the administration app,
and lets everyone from section editor up manage the section roles of their sections.

## Requirements

### Requirement: Sections screen entry point
The admin app header SHALL offer "Sections" to users whose `allowedActions` of `GET /api/me`
contain `MANAGE_SECTIONS` or `ASSIGN_SECTION_ROLES` (the server lists them for `PUBLISHER`,
`EDITOR_IN_CHIEF` and holders of `SECTION_EDITOR` in at least one section), and SHALL NOT show it
to other users.

#### Scenario: Editor-in-chief opens sections
- **WHEN** `chief` chooses "Sections" in the header
- **THEN** the section list is shown

#### Scenario: Section editor has a sections entry
- **WHEN** a user who is `SECTION_EDITOR` in `Sport` and holds no newspaper role is logged in
- **THEN** the header shows "Sections"

#### Scenario: Reporter has no sections entry
- **WHEN** a user who is only `REPORTER` in `Sport` is logged in
- **THEN** the header shows no "Sections" entry

### Requirement: Section list
The sections screen SHALL list the sections from `GET /api/sections` in the server's order, each
with a marker in its colour and its name. When `canManage` is true it SHALL offer "New section",
"Edit" and moving each section one place up or down (sending `PUT /api/sections/order`). A section
whose `assignableRoles` is not empty SHALL open its members screen; other sections SHALL not be
selectable. With no sections the screen SHALL show a hint that there are none yet.

#### Scenario: Move a section up
- **WHEN** `chief` moves `Kultur` (second) one place up
- **THEN** the list shows `Kultur` before `Sport`, also after reloading

#### Scenario: Section editor sees the list
- **WHEN** a user who is `SECTION_EDITOR` in `Sport` only opens the sections screen
- **THEN** all sections are listed, only `Sport` can be opened, and there is no "New section", "Edit" or moving

### Requirement: Create and edit a section in the admin app
"New section" and "Edit" SHALL open a form with the name and the eight palette colours as
swatches with localized colour names; "New section" SHALL preselect the colour the server would
pick. "Save" SHALL be possible only with a non-blank name and SHALL send `POST /api/sections` or
`PUT /api/sections/{id}`. Server errors SHALL be shown at the named field, other errors as a
message; the form SHALL keep the input.

#### Scenario: Create a section
- **WHEN** `chief` enters `Sport`, picks the green swatch and chooses "Save"
- **THEN** the section list contains `Sport` with a green marker

#### Scenario: Duplicate name
- **WHEN** "Save" is answered with `409` for `name`
- **THEN** the form shows the message at the name field and keeps the input

### Requirement: Section members in the admin app
The members screen of a section SHALL list the members from `GET /api/sections/{id}/members` with
username, names and localized role label. It SHALL offer "Add member": choosing an account from
`GET /api/accounts` that holds no role in the section and one of the section's `assignableRoles`,
sent as `PUT /api/sections/{id}/members/{accountId}`. For each member whose role is in
`assignableRoles` it SHALL offer changing the role to another assignable role and "Remove"
(`DELETE`), the latter after a confirmation inside the app. Errors SHALL be shown as a message
and the list reloaded.

#### Scenario: Add a reporter
- **WHEN** the publisher adds `reader` as "Redakteur" to `Sport` (German browser)
- **THEN** the members screen lists `reader` with "Redakteur"

#### Scenario: Remove a member
- **WHEN** the publisher removes `reader` from `Sport` and confirms
- **THEN** `reader` is no longer listed

### Requirement: Section texts are localized
All texts of the sections and members screens, including colour names and the labels
"Ressortleiter" / "Section editor" and "Redakteur" / "Reporter", SHALL come from the German and
English resources of the admin app, following the app's language choice.

#### Scenario: English labels
- **WHEN** a user whose browser prefers English opens the sections screen
- **THEN** the header entry reads "Sections" and the colour names are English
