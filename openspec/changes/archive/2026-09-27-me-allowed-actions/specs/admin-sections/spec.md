## MODIFIED Requirements

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
