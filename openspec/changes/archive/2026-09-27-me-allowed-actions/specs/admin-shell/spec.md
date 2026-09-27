## MODIFIED Requirements

### Requirement: Login with authorization code and PKCE
The admin app SHALL obtain the OIDC configuration from `GET /api/client-config`, redirect an
unauthenticated user to the issuer with the authorization code flow and PKCE (`S256`), exchange
the code for tokens, and keep tokens only in memory. After login it SHALL show a header with the
newspaper name, the user's display name and roles as returned by `GET /api/me`, and SHALL open
the article list below it when `allowedActions` of `GET /api/me` contains `WRITE_ARTICLES`;
otherwise it SHALL show a notice that the account holds no role for writing articles, together
with the logout.

#### Scenario: Publisher logs in
- **WHEN** the bootstrapped publisher opens `/admin/`, is sent to Keycloak and enters valid credentials
- **THEN** they return to `/admin/`, see the newspaper name, their display name and the role `PUBLISHER` in the header, and the list "My articles"

#### Scenario: Reader logs in
- **WHEN** `reader`, who holds only `READER` and no section role, logs in to the admin app
- **THEN** the header shows their display name and the role `READER`, no navigation entries, and instead of an article list the notice that the account holds no role for writing articles; no request to `/api/articles` is made

#### Scenario: Login cancelled or failed
- **WHEN** Keycloak redirects back with an `error` parameter
- **THEN** the app shows a message that login failed and offers to try again

## ADDED Requirements

### Requirement: Header entries follow allowed actions
The admin app SHALL decide which header entries it offers from `allowedActions` of `GET /api/me`
only, never from `roles` or `sectionRoles`: "Articles" with `WRITE_ARTICLES`, "Sections" with
`MANAGE_SECTIONS` or `ASSIGN_SECTION_ROLES`, "Accounts" with `ADMINISTER_ACCOUNTS`. When fewer
than two entries remain, the header SHALL show no entries. Values of `allowedActions` the app does
not know SHALL be ignored. The entries are only visibility; the server enforces access.

#### Scenario: Publisher sees every entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]` is logged in
- **THEN** the header shows "Articles", "Sections" and "Accounts"

#### Scenario: Reporter sees no entries
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES"]` is logged in
- **THEN** the header shows no entries and the article list is shown

#### Scenario: Unknown actions are ignored
- **WHEN** `GET /api/me` answers `allowedActions` `["WRITE_ARTICLES", "REVIEW"]`
- **THEN** the app starts normally and the header shows no entries
