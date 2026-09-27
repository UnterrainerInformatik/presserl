## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Media backup in the installation guide
`deploy/INSTALL.md` SHALL name the media volume next to the database as data to back up, explain
that `PRESSERL_MEDIA_S3_*` may point to another S3-compatible store instead of the bundled
`rustfs`, and state which variables that requires.

#### Scenario: Operator plans backups
- **WHEN** an operator reads the backup section of `INSTALL.md`
- **THEN** it names both the PostgreSQL volume and the media volume (`presserl-media`) as data to back up
