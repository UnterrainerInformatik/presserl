## Purpose

Lets the people a private newspaper is written for log in to the reader with their Keycloak
account and read it, while everyone else sees nothing but the masthead.

## ADDED Requirements

### Requirement: Reader login via the authorization code flow
The system SHALL offer `GET /login` on the reader origin. For a visitor without a reader session
it SHALL redirect to the configured Keycloak realm using the OIDC authorization code flow with a
confidential reader client. After a successful login the visitor SHALL be redirected to the path
given in the optional `next` query parameter, or to `/` when `next` is missing or is not a
same-origin path (it MUST start with a single `/`; values starting with `//` or `/\`, and absolute
URLs, fall back to `/`). A visitor who already has a reader session SHALL be redirected to the
target at once, without contacting Keycloak.

#### Scenario: Anonymous visitor starts the login
- **WHEN** an anonymous visitor requests `GET /login`
- **THEN** the response redirects to the realm's authorization endpoint with the reader client id and `response_type=code`

#### Scenario: Back to the article after login
- **WHEN** the reader `oma` (group `reader`) requests `GET /login?next=/articles/7` and logs in successfully in Keycloak
- **THEN** the browser ends up on `/articles/7`

#### Scenario: Foreign target is ignored
- **WHEN** a visitor logs in via `GET /login?next=//evil.example/x`
- **THEN** the browser ends up on `/`

#### Scenario: Already logged in
- **WHEN** a visitor with a valid reader session requests `GET /login?next=/articles/7`
- **THEN** the response redirects to `/articles/7`

### Requirement: Reader session cookie
The reader session SHALL be kept in a cookie on the reader origin that is `HttpOnly`,
`SameSite=Lax`, scoped to path `/`, carries no readable token (its content is encrypted), and is
`Secure` in production. The session SHALL NOT authenticate requests to `/api`; `/api` keeps
accepting bearer tokens only. When the session can no longer be refreshed, the cookie SHALL be
dropped and the visitor treated as anonymous rather than being forced into a new login.

#### Scenario: Cookie attributes
- **WHEN** a login completes
- **THEN** the session cookie set on the reader origin has the `HttpOnly` and `SameSite=Lax` attributes and path `/`

#### Scenario: Session is not an API credential
- **WHEN** a logged-in reader's browser calls `GET /api/me` with the session cookie but without an `Authorization` header
- **THEN** the response is `401`

### Requirement: Reader logout
The system SHALL offer `GET /logout`. For a logged-in visitor it SHALL end the reader session
(remove the session cookie and end the Keycloak session via RP-initiated logout) and finally
return the visitor to `/`. For an anonymous visitor it SHALL redirect to `/`.

#### Scenario: Logout
- **WHEN** a logged-in reader requests `GET /logout`
- **THEN** the session cookie is removed, the Keycloak session is ended, and the browser ends up on `/` as an anonymous visitor

#### Scenario: Logout without session
- **WHEN** an anonymous visitor requests `GET /logout`
- **THEN** the response redirects to `/`

### Requirement: Access to a private newspaper requires READER or higher
When the effective `visibility` is `private`, a visitor SHALL be entitled to read when their
account holds at least one of the newspaper-wide roles `READER`, `EDITOR_IN_CHIEF` or
`PUBLISHER` (derived from the Keycloak groups `reader`, `editor-in-chief`, `publisher`). The
decision SHALL be made on every request against the current effective `visibility`, so changing
the visibility takes effect without restart or re-login.

#### Scenario: Reader role
- **WHEN** the visibility is `private` and the reader `oma` (group `reader`) requests the front page
- **THEN** she is entitled and sees the published articles

#### Scenario: Higher role
- **WHEN** the visibility is `private` and a user in group `editor-in-chief` requests the front page
- **THEN** they are entitled and see the published articles

#### Scenario: No newspaper role
- **WHEN** the visibility is `private` and a logged-in user without any newspaper group requests the front page
- **THEN** they are not entitled and see no article

### Requirement: Logged-in visitor sees who they are
Every reader page rendered for a logged-in visitor SHALL show, near the masthead, the visitor's
display name (the username when the display name is empty) and a link to `/logout`. Pages for
anonymous visitors SHALL NOT show these.

#### Scenario: Name and logout link
- **WHEN** the logged-in reader `oma` with display name `Oma Resi` requests `GET /`
- **THEN** the page shows `Oma Resi` and a link to `/logout`

#### Scenario: Anonymous visitor
- **WHEN** an anonymous visitor requests `GET /` of a public newspaper
- **THEN** the page contains no link to `/logout` and no link to `/login`

### Requirement: Private and personal pages are not cached
Reader pages SHALL be sent with `Cache-Control: private, no-store` when the effective
`visibility` is `private` or the page is rendered for a logged-in visitor.

#### Scenario: Private newspaper
- **WHEN** an anonymous visitor requests `GET /` of a private newspaper
- **THEN** the response has `Cache-Control: private, no-store`

#### Scenario: Logged in on a public newspaper
- **WHEN** a logged-in reader requests `GET /` of a public newspaper
- **THEN** the response has `Cache-Control: private, no-store`
