## MODIFIED Requirements

### Requirement: Mandatory configuration only
`.env.example` SHALL list exactly the values a fresh installation needs — public base URL,
database password, OIDC issuer URL, backend client secret, reader client secret, admin client id,
first publisher's username and password — each with an explanatory comment and no default for
secrets. Every other setting SHALL have a working default.

#### Scenario: Operator reads the template
- **WHEN** an operator opens `.env.example`
- **THEN** every variable is commented, every password or secret is empty, and optional settings such as `PRESSERL_NEWSPAPER_NAME` are mentioned only as commented-out examples

#### Scenario: Reader secret is listed
- **WHEN** an operator opens `.env.example`
- **THEN** it contains an empty `PRESSERL_OIDC_READER_SECRET` whose comment names the Keycloak client `presserl-reader` and where to copy the secret from

### Requirement: Realm template for an existing Keycloak
The deployment SHALL provide a realm template that an operator imports into their own Keycloak.
It SHALL define the groups `publisher`, `editor-in-chief` and `reader`; a public admin client
(authorization code with PKCE `S256` only, no direct access grants) with the backend audience and
a groups claim in its tokens; a confidential reader client (authorization code only, no direct
access grants, no implicit flow, no service account) with a groups claim in its ID token, whose
redirect and post-logout redirect URIs are limited to the installation's hostname; a confidential
backend client whose service account may only manage and query users and groups of that realm;
self-registration, e-mail login and password reset disabled; and brute-force detection enabled.
The template SHALL contain no client secrets.

#### Scenario: Import into an existing Keycloak
- **WHEN** an operator imports the template (after setting their hostname as documented) into their Keycloak
- **THEN** the realm contains the three groups and three clients as described, the backend can bootstrap the publisher with the backend client's secret, and readers can log in to the reader with the reader client's secret

### Requirement: Installation guide
`deploy/INSTALL.md` SHALL describe, step by step: prerequisites (Docker with compose, an existing
Keycloak, a TLS-terminating reverse proxy, a DNS name), importing the realm template and copying
the backend and reader client secrets, filling `.env`, starting with `docker compose`, attaching
the service to Traefik via labels, an optional Caddy alternative, first login as publisher, making
the newspaper private and letting readers log in, and updating by changing the image tag.

#### Scenario: Following the guide on a fresh host
- **WHEN** an operator with Docker, Keycloak and Traefik follows `INSTALL.md` from top to bottom
- **THEN** they can open the reader over HTTPS and log in to `/admin/` as the first publisher without steps not described in the guide

#### Scenario: Private newspaper after following the guide
- **WHEN** the operator sets `PRESSERL_NEWSPAPER_VISIBILITY=private` as the guide describes and a user of group `reader` opens the reader
- **THEN** that user can log in via the reader's login link and read the published articles
