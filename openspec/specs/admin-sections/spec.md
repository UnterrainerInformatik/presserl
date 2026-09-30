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
with a marker in its colour and its name. When a section's `articleCounts` is present, the entry
SHALL also show the number of live articles and the total, e.g. "2 online · 4 gesamt". When
`issues` is not empty, it SHALL also show the count per issue, e.g. "Ausgabe 2: 2 · Ausgabe 1: 1".
When `canManage` is true, the screen SHALL offer "New section", "Edit", "Delete" and moving each
section one place up or down (sending `PUT /api/sections/order`). A section whose `assignableRoles`
is not empty SHALL open its members screen; other sections SHALL not be selectable. With no
sections the screen SHALL show a hint that there are none yet. All texts SHALL be available in
German and English.

#### Scenario: Move a section up
- **WHEN** `chief` moves `Kultur` (second) one place up
- **THEN** the list shows `Kultur` before `Sport`, also after reloading

#### Scenario: Section editor sees the list
- **WHEN** a user who is `SECTION_EDITOR` in `Sport` only opens the sections screen
- **THEN** all sections are listed, only `Sport` can be opened, and there is no "New section", "Edit", "Delete" or moving

#### Scenario: Counts in the list
- **WHEN** `Sport` has `articleCounts` `{"live": 2, "total": 4, "issues": [{"number": 2, "count": 2}, {"number": 1, "count": 1}]}` and the publisher opens the sections screen (German browser)
- **THEN** the `Sport` entry shows "2 online · 4 gesamt" and "Ausgabe 2: 2 · Ausgabe 1: 1"

#### Scenario: Empty section
- **WHEN** `Kultur` has `articleCounts` `{"live": 0, "total": 0, "issues": []}`
- **THEN** the `Kultur` entry shows "0 online · 0 gesamt" and no issue line

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

### Requirement: Section colour markers follow the colour scheme
The admin app SHALL draw each of the eight section palette colours (markers and swatches) in a
variant that stays clearly visible on the current background: the existing tones on the light
scheme and brighter tones on the dark scheme. Both variants of one palette key SHALL be
recognisably the same hue, and the eight colours SHALL stay distinguishable from one another in
both schemes. The stored palette key SHALL NOT depend on the scheme.

#### Scenario: Markers on a dark background
- **WHEN** a user with a dark system preference opens the sections screen
- **THEN** every section marker is drawn in the brighter dark-scheme tone of its colour

#### Scenario: Picking a colour in dark
- **WHEN** a user with a dark system preference picks the green swatch and saves the section
- **THEN** the section is stored with the colour `green`, and a user with a light preference sees it with the light-scheme green marker
