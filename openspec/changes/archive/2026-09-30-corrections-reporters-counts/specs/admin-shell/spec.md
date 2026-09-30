## MODIFIED Requirements

### Requirement: Login with authorization code and PKCE
The admin app SHALL obtain the OIDC configuration from `GET /api/client-config`, redirect an
unauthenticated user to the issuer with the authorization code flow and PKCE (`S256`), exchange the
code for tokens, and keep tokens only in memory. After login it SHALL show a header with the
newspaper name, the user's display name and roles as returned by `GET /api/me` (including
"Redakteur (ohne Ressort)" when `sectionlessReporter` is true). Below the header it SHALL open:

- the article list when `allowedActions` contains `WRITE_ARTICLES`;
- otherwise the "Images" view when it contains `USE_MEDIA`;
- otherwise a notice that the account holds no role for writing articles, together with the
  logout.

#### Scenario: Publisher logs in
- **WHEN** the bootstrapped publisher opens `/admin/`, is sent to Keycloak and enters valid credentials
- **THEN** they return to `/admin/`, see the newspaper name, their display name and the role `PUBLISHER` in the header, and the list "My articles"

#### Scenario: Reader logs in
- **WHEN** `reader`, who holds only `READER` and no section role, logs in to the admin app
- **THEN** the header shows their display name and the role `READER`, no navigation entries, and instead of an article list the notice that the account holds no role for writing articles; no request to `/api/articles` is made

#### Scenario: Sectionless reporter logs in
- **WHEN** a user whose `allowedActions` are `["USE_MEDIA"]` logs in
- **THEN** the app opens the "Images" view, the header shows "Redakteur (ohne Ressort)" and no navigation entries, and no request to `/api/articles` is made

#### Scenario: Login cancelled or failed
- **WHEN** Keycloak redirects back with an `error` parameter
- **THEN** the app shows a message that login failed and offers to try again

### Requirement: Header entries follow allowed actions, including images
The admin app SHALL decide which header entries it offers from `allowedActions` of `GET /api/me`
only, never from `roles`, `sectionRoles` or `sectionlessReporter`. The entries, in this order, are:

| Entry | Offered with |
|---|---|
| "Articles" | `WRITE_ARTICLES` |
| "Images" | `USE_MEDIA` |
| "Sections" | `MANAGE_SECTIONS` or `ASSIGN_SECTION_ROLES` |
| "Issues" | `MANAGE_ISSUES` |
| "Accounts" | `ADMINISTER_ACCOUNTS` |
| "Newspaper" | `CONFIGURE_NEWSPAPER` |

When fewer than two entries remain, the header SHALL show no entries. Values of `allowedActions`
the app does not know SHALL be ignored. The entries are only visibility; the server enforces
access.

#### Scenario: Publisher sees every entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"]` is logged in
- **THEN** the header shows "Articles", "Images", "Sections", "Issues", "Accounts" and "Newspaper"

#### Scenario: Section editor has no issues entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "USE_MEDIA", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]` is logged in
- **THEN** the header shows "Articles", "Images", "Sections" and "Accounts" and no "Issues"

#### Scenario: Reporter sees articles and images
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "USE_MEDIA"]` is logged in
- **THEN** the header shows "Articles" and "Images" and the article list is shown

#### Scenario: Sectionless reporter gets no entries
- **WHEN** a user whose `allowedActions` are `["USE_MEDIA"]` is logged in
- **THEN** the header shows no entries and the "Images" view is shown

#### Scenario: Unknown actions are ignored
- **WHEN** `GET /api/me` answers `allowedActions` `["WRITE_ARTICLES", "USE_MEDIA", "REVIEW"]`
- **THEN** the app starts normally and the header shows "Articles" and "Images"
