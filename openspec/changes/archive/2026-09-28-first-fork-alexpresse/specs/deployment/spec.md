## MODIFIED Requirements

### Requirement: Installation guide
`deploy/INSTALL.md` SHALL describe, step by step: prerequisites (Docker with compose, an existing
Keycloak, a TLS-terminating reverse proxy, a DNS name), which files to copy to the host
(`compose.yaml`, `.env.example`, `keycloak/` and `theme/`), importing the realm template and
copying the backend and reader client secrets, filling `.env`, starting with `docker compose`,
attaching the service to Traefik via labels, an optional Caddy alternative, first login as
publisher, making the newspaper private and letting readers log in, and updating by changing the
image tag.

#### Scenario: Following the guide on a fresh host
- **WHEN** an operator with Docker, Keycloak and Traefik follows `INSTALL.md` from top to bottom
- **THEN** they can open the reader over HTTPS and log in to `/admin/` as the first publisher without steps not described in the guide

#### Scenario: First article with a lead image after following the guide
- **WHEN** the operator, logged in to `/admin/` as the first publisher after following the guide, creates a section, writes an article, uploads an image as its lead image, publishes the article and switches the first issue live
- **THEN** the reader over HTTPS shows the article with its lead image on the front page and on the article page, and the issue in the issue archive, without steps not described in the guide

#### Scenario: Theme directory copied with the deployment
- **WHEN** an operator copies the files the guide names in its first step and starts the deployment
- **THEN** `theme/` exists on the host, the reader links `/theme/custom.css` and the guide's theming steps work without first creating the directory by hand

#### Scenario: Private newspaper after following the guide
- **WHEN** the operator sets `PRESSERL_NEWSPAPER_VISIBILITY=private` as the guide describes and a user of group `reader` opens the reader
- **THEN** that user can log in via the reader's login link and read the published articles
