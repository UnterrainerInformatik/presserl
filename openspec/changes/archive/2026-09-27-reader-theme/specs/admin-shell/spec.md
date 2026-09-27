## MODIFIED Requirements

### Requirement: Header entries follow allowed actions
The admin app SHALL decide which header entries it offers from `allowedActions` of `GET /api/me`
only, never from `roles` or `sectionRoles`: "Articles" with `WRITE_ARTICLES`, "Sections" with
`MANAGE_SECTIONS` or `ASSIGN_SECTION_ROLES`, "Accounts" with `ADMINISTER_ACCOUNTS`, "Newspaper"
with `CONFIGURE_NEWSPAPER`, in this order. When fewer than two entries remain, the header SHALL
show no entries. Values of `allowedActions` the app does not know SHALL be ignored. The entries
are only visibility; the server enforces access.

#### Scenario: Publisher sees every entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"]` is logged in
- **THEN** the header shows "Articles", "Sections", "Accounts" and "Newspaper"

#### Scenario: Reporter sees no entries
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES"]` is logged in
- **THEN** the header shows no entries and the article list is shown

#### Scenario: Unknown actions are ignored
- **WHEN** `GET /api/me` answers `allowedActions` `["WRITE_ARTICLES", "REVIEW"]`
- **THEN** the app starts normally and the header shows no entries
