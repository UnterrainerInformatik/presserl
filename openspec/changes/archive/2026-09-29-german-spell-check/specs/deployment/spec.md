## MODIFIED Requirements

### Requirement: Compose deployment with presserl and postgres only
The reference deployment SHALL be a docker compose file that runs exactly four services,
`presserl` (the backend image including reader and admin app), `postgres`, `rustfs` (the
S3-compatible object store for media) and `languagetool` (the spell checker), with the database
and the media on named volumes. It SHALL NOT contain a reverse proxy or a Keycloak service, and
neither `rustfs` nor `languagetool` SHALL publish any port to the host. `presserl` SHALL start
only after `postgres` and `rustfs` are healthy, SHALL NOT wait for `languagetool`, and SHALL
expose a health check based on its readiness.

#### Scenario: Starting the reference deployment
- **WHEN** an operator fills `.env` from `.env.example` and runs `docker compose up -d`
- **THEN** `postgres`, `rustfs`, `languagetool` and `presserl` start, `presserl` becomes healthy once the database, the object store and Keycloak are reachable, and `GET /api/newspaper` answers on the published HTTP port

#### Scenario: Data survives a restart
- **WHEN** the operator runs `docker compose down` and `docker compose up -d` again
- **THEN** the database content and the uploaded media are preserved

#### Scenario: Object store is not exposed
- **WHEN** the reference deployment is running
- **THEN** no port of `rustfs` is published on the host; only `presserl` reaches it on the compose network

#### Scenario: Spell checker is internal
- **WHEN** the reference deployment is running
- **THEN** no port of `languagetool` is published on the host, and `POST /api/spell-check` returns findings through `presserl`

#### Scenario: Spell checker still starting
- **WHEN** `languagetool` has not finished starting while `presserl` is already healthy
- **THEN** articles can be written and saved, and spell-check requests answer `503` until `languagetool` is up
