## ADDED Requirements

### Requirement: Header entries follow allowed actions, including images
The admin app SHALL decide which header entries it offers from `allowedActions` of `GET /api/me`
only, never from `roles` or `sectionRoles`: "Articles" with `WRITE_ARTICLES`, "Images" with
`WRITE_ARTICLES`, "Sections" with `MANAGE_SECTIONS` or `ASSIGN_SECTION_ROLES`, "Issues" with
`MANAGE_ISSUES`, "Accounts" with `ADMINISTER_ACCOUNTS`, "Newspaper" with `CONFIGURE_NEWSPAPER`, in
this order. When fewer than two entries remain, the header SHALL show no entries. Values of
`allowedActions` the app does not know SHALL be ignored. The entries are only visibility; the
server enforces access.

#### Scenario: Publisher sees every entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"]` is logged in
- **THEN** the header shows "Articles", "Images", "Sections", "Issues", "Accounts" and "Newspaper"

#### Scenario: Section editor has no issues entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]` is logged in
- **THEN** the header shows "Articles", "Images", "Sections" and "Accounts" and no "Issues"

#### Scenario: Reporter sees articles and images
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES"]` is logged in
- **THEN** the header shows "Articles" and "Images" and the article list is shown

#### Scenario: Unknown actions are ignored
- **WHEN** `GET /api/me` answers `allowedActions` `["WRITE_ARTICLES", "REVIEW"]`
- **THEN** the app starts normally and the header shows "Articles" and "Images"

## REMOVED Requirements

### Requirement: Header entries follow allowed actions
**Reason**: Replaced by "Header entries follow allowed actions, including images", which adds the
"Images" entry; a reporter now has two entries, so the scenario "Reporter sees no entries" no
longer holds.
**Migration**: None for users; the admin navigation tests follow the new requirement.
