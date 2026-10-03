## MODIFIED Requirements

### Requirement: Reader session cookie
The reader session SHALL be kept in a cookie on the reader origin that is `HttpOnly`,
`SameSite=Lax`, scoped to path `/`, carries no readable token (its content is encrypted), and is
`Secure` in production. The session SHALL NOT authenticate requests to `/api`; `/api` keeps
accepting bearer tokens only. The cookie SHALL be persistent (it survives closing the browser) and
SHALL NOT expire before the Keycloak session can no longer be refreshed, so that a reader who
returns within the realm's SSO session lifetime (180 days in the realm template) is still logged
in without entering their password; expired access tokens SHALL be refreshed silently. When the
session can no longer be refreshed, the cookie SHALL be dropped and the visitor treated as
anonymous rather than being forced into a new login.

#### Scenario: Cookie attributes
- **WHEN** a login completes
- **THEN** the session cookie set on the reader origin has the `HttpOnly` and `SameSite=Lax` attributes and path `/`

#### Scenario: Cookie outlives the access token
- **WHEN** a login completes against the realm template's session settings
- **THEN** the session cookie carries an expiry at least 179 days in the future

#### Scenario: Session is not an API credential
- **WHEN** a logged-in reader's browser calls `GET /api/me` with the session cookie but without an `Authorization` header
- **THEN** the response is `401`
