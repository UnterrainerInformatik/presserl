## MODIFIED Requirements

### Requirement: Login with authorization code and PKCE
The admin app SHALL obtain the OIDC configuration from `GET /api/client-config`, redirect an
unauthenticated user to the issuer with the authorization code flow and PKCE (`S256`), exchange
the code for tokens, and keep tokens only in memory. After login it SHALL show a header with the
newspaper name, the user's display name and roles as returned by `GET /api/me`, and SHALL open
the article list below it.

#### Scenario: Publisher logs in
- **WHEN** the bootstrapped publisher opens `/admin/`, is sent to Keycloak and enters valid credentials
- **THEN** they return to `/admin/`, see the newspaper name, their display name and the role `PUBLISHER` in the header, and the list "My articles"

#### Scenario: Login cancelled or failed
- **WHEN** Keycloak redirects back with an `error` parameter
- **THEN** the app shows a message that login failed and offers to try again

## ADDED Requirements

### Requirement: Localized user interface
All texts of the admin app SHALL come from localized resources in German and English. The app
SHALL use English when the browser's preferred language is English and German otherwise. Role and
status names SHALL be shown with their localized labels.

#### Scenario: German browser
- **WHEN** a user whose browser prefers `de-AT` opens the admin app
- **THEN** labels read e.g. "Schlagzeile", "Dachzeile" and "Abmelden"

#### Scenario: English browser
- **WHEN** a user whose browser prefers `en-GB` opens the admin app
- **THEN** labels read e.g. "Headline", "Kicker" and "Log out"
