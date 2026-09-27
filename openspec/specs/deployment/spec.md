# deployment Specification

## Purpose

Defines how an operator runs Presserl: a docker compose deployment of the Presserl image and
PostgreSQL, wired to an operator-provided Keycloak and reverse proxy, documented step by step.

## Requirements

### Requirement: Compose deployment with presserl and postgres only
The reference deployment SHALL be a docker compose file that runs exactly three services,
`presserl` (the backend image including reader and admin app), `postgres` and `rustfs` (the
S3-compatible object store for media), with the database and the media on named volumes. It SHALL
NOT contain a reverse proxy or a Keycloak service, and `rustfs` SHALL NOT publish any port to the
host. `presserl` SHALL start only after `postgres` and `rustfs` are healthy and SHALL expose a
health check based on its readiness.

#### Scenario: Starting the reference deployment
- **WHEN** an operator fills `.env` from `.env.example` and runs `docker compose up -d`
- **THEN** `postgres`, `rustfs` and `presserl` start, `presserl` becomes healthy once the database, the object store and Keycloak are reachable, and `GET /api/newspaper` answers on the published HTTP port

#### Scenario: Data survives a restart
- **WHEN** the operator runs `docker compose down` and `docker compose up -d` again
- **THEN** the database content and the uploaded media are preserved

#### Scenario: Object store is not exposed
- **WHEN** the reference deployment is running
- **THEN** no port of `rustfs` is published on the host; only `presserl` reaches it on the compose network

### Requirement: Mandatory configuration only
`.env.example` SHALL list exactly the values a fresh installation needs — public base URL,
database password, OIDC issuer URL, backend client secret, reader client secret, admin client id,
first publisher's username and password, object-store access key and secret key — each with an
explanatory comment and no default for secrets. Every other setting SHALL have a working default.

#### Scenario: Operator reads the template
- **WHEN** an operator opens `.env.example`
- **THEN** every variable is commented, every password or secret is empty, and optional settings such as `PRESSERL_NEWSPAPER_NAME` are mentioned only as commented-out examples

#### Scenario: Reader secret is listed
- **WHEN** an operator opens `.env.example`
- **THEN** it contains an empty `PRESSERL_OIDC_READER_SECRET` whose comment names the Keycloak client `presserl-reader` and where to copy the secret from

#### Scenario: Object-store credentials are listed
- **WHEN** an operator opens `.env.example`
- **THEN** it contains empty `PRESSERL_MEDIA_S3_ACCESS_KEY` and `PRESSERL_MEDIA_S3_SECRET_KEY`, whose comment says they are chosen freely and used by both `rustfs` and `presserl`

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

### Requirement: Theme directory in the reference deployment
The reference deployment SHALL contain a `theme/` directory next to the compose file that the
compose file mounts read-only as the theme directory of `presserl`. It SHALL contain a
`custom.css` starter that changes nothing and explains in comments the public tokens, `data-view`
values and documented classes; a README on how to theme and where fonts and images go; and the
example themes *Classic*, *Colourful* and *Night* as copy templates. `INSTALL.md` SHALL describe
how to theme the newspaper by editing `theme/custom.css`, how to start from an example theme, and
that a changed theme applies on the next page load without restarting.

#### Scenario: Starting with the unchanged starter
- **WHEN** an operator starts the reference deployment without touching `theme/`
- **THEN** the reader links `/theme/custom.css` and looks exactly like the built-in default theme

#### Scenario: Using an example theme
- **WHEN** the operator copies `theme/examples/night.css` over `theme/custom.css` and reloads a reader page
- **THEN** the reader shows the Night theme without a restart of `presserl`

### Requirement: Media backup in the installation guide
`deploy/INSTALL.md` SHALL name the media volume next to the database as data to back up, explain
that `PRESSERL_MEDIA_S3_*` may point to another S3-compatible store instead of the bundled
`rustfs`, and state which variables that requires.

#### Scenario: Operator plans backups
- **WHEN** an operator reads the backup section of `INSTALL.md`
- **THEN** it names both the PostgreSQL volume and the media volume (`presserl-media`) as data to back up
