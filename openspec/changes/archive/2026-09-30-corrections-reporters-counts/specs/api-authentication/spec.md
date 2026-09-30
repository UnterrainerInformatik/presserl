## MODIFIED Requirements

### Requirement: Current user endpoint
The system SHALL expose `GET /api/me` for authenticated users. The response SHALL contain:

- the username;
- the display name (falling back to the username when the token has no name);
- the newspaper-wide roles derived from the user's Keycloak groups: `publisher` → `PUBLISHER`,
  `editor-in-chief` → `EDITOR_IN_CHIEF`, `reader` → `READER`; groups outside this set SHALL be
  ignored;
- the user's section roles (`sectionRoles`: `sectionId`, `sectionName`, `role`, ordered by section
  position);
- whether the user carries the `sectionlessReporter` marker (see accounts).

The response SHALL also contain `allowedActions`: the newspaper-wide actions the user may perform
at the time of the request, in the order `WRITE_ARTICLES`, `USE_MEDIA`, `MANAGE_SECTIONS`,
`ASSIGN_SECTION_ROLES`, `MANAGE_ISSUES`, `ADMINISTER_ACCOUNTS`, `CONFIGURE_NEWSPAPER`,
`CONFIGURE_SPELL_CHECK`, `CONFIGURE_CORRECTIONS`, `[]` for none. Each action SHALL be listed exactly
when the endpoints it stands for admit the user:

| Action | Listed when the user holds | Stands for |
|---|---|---|
| `WRITE_ARTICLES` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or a section role in any section | the article endpoints |
| `USE_MEDIA` | anything that lists `WRITE_ARTICLES`, or the `sectionlessReporter` marker | the media endpoints |
| `MANAGE_SECTIONS` | `PUBLISHER` or `EDITOR_IN_CHIEF` | creating, changing and reordering sections |
| `ASSIGN_SECTION_ROLES` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or `SECTION_EDITOR` in any section | section members of at least one section |
| `MANAGE_ISSUES` | `PUBLISHER` or `EDITOR_IN_CHIEF` | the issue endpoints |
| `ADMINISTER_ACCOUNTS` | `PUBLISHER`, `EDITOR_IN_CHIEF`, or `SECTION_EDITOR` in any section | listing and creating accounts |
| `CONFIGURE_NEWSPAPER` | `PUBLISHER` or `EDITOR_IN_CHIEF` | changing the newspaper settings |
| `CONFIGURE_SPELL_CHECK` | `PUBLISHER` | changing the newspaper's spell-check help (`spell-check.help`) |
| `CONFIGURE_CORRECTIONS` | `PUBLISHER` | switching corrections by higher levels (`article.corrections`) |

Section roles and the marker SHALL be read at the time of the request, so a change is reflected by
the next call. Newspaper roles follow the token.

#### Scenario: Bootstrapped publisher asks who they are
- **WHEN** the bootstrapped publisher calls `GET /api/me` with a valid token
- **THEN** the response is `200` with their `username`, `roles` equal to `["PUBLISHER"]`, `sectionlessReporter` `false` and `allowedActions` equal to `["WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER", "CONFIGURE_SPELL_CHECK", "CONFIGURE_CORRECTIONS"]`

#### Scenario: Editor-in-chief may configure the newspaper
- **WHEN** a user holding only `EDITOR_IN_CHIEF` calls `GET /api/me`
- **THEN** `allowedActions` is `["WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"]`

#### Scenario: User without newspaper groups
- **WHEN** a valid user who belongs to no newspaper group, holds no section role and carries no marker calls `GET /api/me`
- **THEN** the response is `200` with `roles` equal to `[]`, `sectionRoles` equal to `[]`, `sectionlessReporter` `false` and `allowedActions` equal to `[]`

#### Scenario: Reader has no actions
- **WHEN** `reader`, who holds only `READER` and no section role, calls `GET /api/me`
- **THEN** `allowedActions` is `[]`

#### Scenario: Section editor asks who they are
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and calls `GET /api/me`
- **THEN** `sectionRoles` is `[{"sectionId": <Sport>, "sectionName": "Sport", "role": "SECTION_EDITOR"}]` and `allowedActions` is `["WRITE_ARTICLES", "USE_MEDIA", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]`

#### Scenario: Reporter may only write
- **WHEN** `nogroups` is `REPORTER` in `Sport` and calls `GET /api/me`
- **THEN** `allowedActions` is `["WRITE_ARTICLES", "USE_MEDIA"]`

#### Scenario: Sectionless reporter uses media only
- **WHEN** `nogroups` carries the `sectionlessReporter` marker, holds no section role and calls `GET /api/me`
- **THEN** `sectionlessReporter` is `true` and `allowedActions` is `["USE_MEDIA"]`

#### Scenario: Section role change applies at the next call
- **WHEN** `nogroups` is `REPORTER` in `Sport`, is then made `SECTION_EDITOR` in `Sport` and calls `GET /api/me` again with the same token
- **THEN** `allowedActions` is `["WRITE_ARTICLES", "USE_MEDIA", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]`
