# deployment Specification

## Purpose

Defines how an operator runs Presserl: a docker compose deployment of the Presserl image and
PostgreSQL, wired to an operator-provided Keycloak and reverse proxy, documented step by step.

## Requirements

### Requirement: Compose deployment with presserl and postgres only
The reference deployment SHALL be a docker compose file that runs exactly two services,
`presserl` (the backend image including reader and admin app) and `postgres`, with the database
on a named volume. It SHALL NOT contain a reverse proxy or a Keycloak service. `presserl` SHALL
start only after `postgres` is healthy and SHALL expose a health check based on its readiness.

#### Scenario: Starting the reference deployment
- **WHEN** an operator fills `.env` from `.env.example` and runs `docker compose up -d`
- **THEN** `postgres` and `presserl` start, `presserl` becomes healthy once the database and Keycloak are reachable, and `GET /api/newspaper` answers on the published HTTP port

#### Scenario: Data survives a restart
- **WHEN** the operator runs `docker compose down` and `docker compose up -d` again
- **THEN** the database content is preserved

### Requirement: Mandatory configuration only
`.env.example` SHALL list exactly the values a fresh installation needs — public base URL,
database password, OIDC issuer URL, backend client secret, admin client id, first publisher's
username and password — each with an explanatory comment and no default for secrets. Every other
setting SHALL have a working default.

#### Scenario: Operator reads the template
- **WHEN** an operator opens `.env.example`
- **THEN** every variable is commented, every password or secret is empty, and optional settings such as `PRESSERL_NEWSPAPER_NAME` are mentioned only as commented-out examples

### Requirement: Realm template for an existing Keycloak
The deployment SHALL provide a realm template that an operator imports into their own Keycloak.
It SHALL define the groups `publisher`, `editor-in-chief` and `reader`; a public admin client
(authorization code with PKCE `S256` only, no direct access grants) with the backend audience and
a groups claim in its tokens; a confidential backend client whose service account may only manage
and query users and groups of that realm; self-registration, e-mail login and password reset
disabled; and brute-force detection enabled.

#### Scenario: Import into an existing Keycloak
- **WHEN** an operator imports the template (after setting their hostname as documented) into their Keycloak
- **THEN** the realm contains the three groups and two clients as described, and the backend can bootstrap the publisher with the backend client's secret

### Requirement: Installation guide
`deploy/INSTALL.md` SHALL describe, step by step: prerequisites (Docker with compose, an existing
Keycloak, a TLS-terminating reverse proxy, a DNS name), importing the realm template and copying
the backend client secret, filling `.env`, starting with `docker compose`, attaching the service to
Traefik via labels, an optional Caddy alternative, first login as publisher, and updating by
changing the image tag.

#### Scenario: Following the guide on a fresh host
- **WHEN** an operator with Docker, Keycloak and Traefik follows `INSTALL.md` from top to bottom
- **THEN** they can open the reader over HTTPS and log in to `/admin/` as the first publisher without steps not described in the guide
