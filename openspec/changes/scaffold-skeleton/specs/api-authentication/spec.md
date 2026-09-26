## Purpose

Protects the REST API with bearer tokens issued by the operator's Keycloak, tells the
administration app where to log in, and lets a client ask who it is logged in as.

## ADDED Requirements

### Requirement: Bearer tokens protect non-public API endpoints
The system SHALL accept on `/api` only access tokens that are signed by the configured OIDC
issuer, are not expired, and carry the backend's audience. Requests to a non-public `/api`
endpoint without such a token SHALL be answered with `401`. `GET /api/newspaper` and
`GET /api/client-config` SHALL be public.

#### Scenario: Missing token
- **WHEN** a client calls `GET /api/me` without an `Authorization` header
- **THEN** the response is `401`

#### Scenario: Token from a foreign issuer
- **WHEN** a client calls `GET /api/me` with a token signed by a different issuer
- **THEN** the response is `401`

#### Scenario: Token without the backend audience
- **WHEN** a client calls `GET /api/me` with a token from the configured issuer whose audience does not include the backend
- **THEN** the response is `401`

### Requirement: Public client configuration for the admin app
The system SHALL expose `GET /api/client-config` without authentication, returning the OIDC
issuer URL, the admin client id and the scopes the admin app requests, all taken from the
deployment configuration.

#### Scenario: Admin app fetches its login configuration
- **WHEN** an anonymous client calls `GET /api/client-config`
- **THEN** the response is `200` with `oidc.issuer` equal to the configured issuer URL, `oidc.clientId` equal to the configured admin client id and `oidc.scopes` containing `openid`

### Requirement: Current user endpoint
The system SHALL expose `GET /api/me` for authenticated users, returning the username, the
display name (falling back to the username when the token has no name) and the newspaper-wide
roles derived from the user's Keycloak groups: `publisher` → `PUBLISHER`,
`editor-in-chief` → `EDITOR_IN_CHIEF`, `reader` → `READER`. Groups outside this set SHALL be
ignored.

#### Scenario: Bootstrapped publisher asks who they are
- **WHEN** the bootstrapped publisher calls `GET /api/me` with a valid token
- **THEN** the response is `200` with their `username` and `roles` equal to `["PUBLISHER"]`

#### Scenario: User without newspaper groups
- **WHEN** a valid user who belongs to no newspaper group calls `GET /api/me`
- **THEN** the response is `200` with `roles` equal to `[]`
