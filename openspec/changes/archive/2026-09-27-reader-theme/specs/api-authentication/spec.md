## MODIFIED Requirements

### Requirement: Current user endpoint
The system SHALL expose `GET /api/me` for authenticated users, returning the username, the
display name (falling back to the username when the token has no name), the newspaper-wide
roles derived from the user's Keycloak groups: `publisher` → `PUBLISHER`,
`editor-in-chief` → `EDITOR_IN_CHIEF`, `reader` → `READER`, and the user's section roles
(`sectionRoles`: `sectionId`, `sectionName`, `role`, ordered by section position). Groups outside
this set SHALL be ignored.

The response SHALL also contain `allowedActions`: the newspaper-wide actions the user may perform
at the time of the request, in the order `WRITE_ARTICLES`, `MANAGE_SECTIONS`,
`ASSIGN_SECTION_ROLES`, `ADMINISTER_ACCOUNTS`, `CONFIGURE_NEWSPAPER`, `[]` for none. Each action
SHALL be listed exactly when the endpoints it stands for admit the user:

| Action | Listed when the user holds | Stands for |
|---|---|---|
| `WRITE_ARTICLES` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or a section role in any section | the article endpoints |
| `MANAGE_SECTIONS` | `PUBLISHER` or `EDITOR_IN_CHIEF` | creating, changing and reordering sections |
| `ASSIGN_SECTION_ROLES` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or `SECTION_EDITOR` in any section | section members of at least one section |
| `ADMINISTER_ACCOUNTS` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or `SECTION_EDITOR` in any section | listing and creating accounts |
| `CONFIGURE_NEWSPAPER` | `PUBLISHER` or `EDITOR_IN_CHIEF` | changing the newspaper settings |

Section roles SHALL be read at the time of the request, so a changed section role is reflected by
the next call; newspaper roles follow the token.

#### Scenario: Bootstrapped publisher asks who they are
- **WHEN** the bootstrapped publisher calls `GET /api/me` with a valid token
- **THEN** the response is `200` with their `username`, `roles` equal to `["PUBLISHER"]` and `allowedActions` equal to `["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"]`

#### Scenario: Editor-in-chief may configure the newspaper
- **WHEN** a user holding only `EDITOR_IN_CHIEF` calls `GET /api/me`
- **THEN** `allowedActions` is `["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"]`

#### Scenario: User without newspaper groups
- **WHEN** a valid user who belongs to no newspaper group and holds no section role calls `GET /api/me`
- **THEN** the response is `200` with `roles` equal to `[]`, `sectionRoles` equal to `[]` and `allowedActions` equal to `[]`

#### Scenario: Reader has no actions
- **WHEN** `reader`, who holds only `READER` and no section role, calls `GET /api/me`
- **THEN** `allowedActions` is `[]`

#### Scenario: Section editor asks who they are
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and calls `GET /api/me`
- **THEN** `sectionRoles` is `[{"sectionId": <Sport>, "sectionName": "Sport", "role": "SECTION_EDITOR"}]` and `allowedActions` is `["WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]`

#### Scenario: Reporter may only write
- **WHEN** `nogroups` is `REPORTER` in `Sport` and calls `GET /api/me`
- **THEN** `allowedActions` is `["WRITE_ARTICLES"]`

#### Scenario: Section role change applies at the next call
- **WHEN** `nogroups` is `REPORTER` in `Sport`, is then made `SECTION_EDITOR` in `Sport` and calls `GET /api/me` again with the same token
- **THEN** `allowedActions` is `["WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]`
