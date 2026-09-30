## MODIFIED Requirements

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
