## MODIFIED Requirements

### Requirement: Current user endpoint
The system SHALL expose `GET /api/me` for authenticated users, returning the username, the
display name (falling back to the username when the token has no name), the newspaper-wide
roles derived from the user's Keycloak groups: `publisher` → `PUBLISHER`,
`editor-in-chief` → `EDITOR_IN_CHIEF`, `reader` → `READER`, and the user's section roles
(`sectionRoles`: `sectionId`, `sectionName`, `role`, ordered by section position). Groups outside
this set SHALL be ignored.

#### Scenario: Bootstrapped publisher asks who they are
- **WHEN** the bootstrapped publisher calls `GET /api/me` with a valid token
- **THEN** the response is `200` with their `username` and `roles` equal to `["PUBLISHER"]`

#### Scenario: User without newspaper groups
- **WHEN** a valid user who belongs to no newspaper group and holds no section role calls `GET /api/me`
- **THEN** the response is `200` with `roles` equal to `[]` and `sectionRoles` equal to `[]`

#### Scenario: Section editor asks who they are
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and calls `GET /api/me`
- **THEN** `sectionRoles` is `[{"sectionId": <Sport>, "sectionName": "Sport", "role": "SECTION_EDITOR"}]`
