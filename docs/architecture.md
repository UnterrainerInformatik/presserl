# Architecture

![Architecture](diagrams/architecture.svg)

## Stack

| Part | Choice |
|---|---|
| Backend | Quarkus (current LTS), **Quarkus REST** (reactive), **Hibernate Reactive with Panache**, reactive PostgreSQL client, `quarkus-oidc` (bearer tokens from Keycloak), Flyway (runs at startup via an additional JDBC datasource — Flyway is not reactive), SmallRye OpenAPI |
| Database | **PostgreSQL** |
| Identity | **Keycloak** (realm `presserl`, realm import in `deploy/`) |
| Frontend | Vue 3 + TypeScript, Vite, Pinia, `keycloak-js`, Tiptap editor |
| Development | **Quarkus Dev Services** start PostgreSQL and Keycloak automatically — `quarkus dev` needs no configuration |
| CI/CD | UnterrainerInformatik workflows (`docker-build-workflow`, `npm-build-workflow`, `deploy-workflow`, `bump-semver-workflow`) |
| Operations | docker compose (web, backend, keycloak, postgres) |

Java toolchain: pinned at scaffolding time (see `ai/memory/reference_machine_jdk.md` — Lombok constraints).

## Repository layout

```
presserl/
├── backend/            # Quarkus
├── frontend/           # Vue 3 + TS
├── deploy/             # the only folder a fork needs to touch
│   ├── compose.yaml
│   ├── .env.example    # only passwords and hostname are mandatory
│   ├── keycloak/presserl-realm.json
│   └── theme/          # optional; overrides the default theme
│       ├── custom.css
│       ├── logo.svg
│       └── fonts/
├── http/               # .http files for the REST API
├── docs/
├── openspec/
└── ai/
```

A fork uses the upstream container images and customises only `deploy/`. Updates are an image-tag bump.

## Configuration

Four layers; the later one wins:

1. **Code default** — `application.properties` (Quarkus / SmallRye Config)
2. **Deployment** — environment variable in `deploy/.env` (`PRESSERL_REVIEW_MODE=always` overrides `presserl.review.mode`)
3. **Newspaper** — setting changed in the app by the publisher (database)
4. **Section** — setting per section (database)

The database layers store only overrides, never copies of defaults. The backend exposes the *effective* value.

| Key | Default | Alternatives | Layers |
|---|---|---|---|
| `presserl.newspaper.name` | `My Newspaper` (i18n) | any | deployment, newspaper |
| `presserl.newspaper.subtitle` | empty | any | deployment, newspaper |
| `presserl.newspaper.visibility` | `private` | `public` | deployment, newspaper |
| `presserl.review.mode` | `auto` | `always`, `never` | all |
| `presserl.review.chief-needs-publisher` | `false` | `true` | all |
| `presserl.retract.author-can-retract` | `true` | `false` | deployment, newspaper |
| `presserl.section.default` | `General` (i18n) | any | deployment |
| `presserl.editor.level` | `standard` | `starter`, `profi` | deployment, newspaper, per user |
| `presserl.reader.text-size` | `m` | `s`, `l`, `xl` | deployment, newspaper, per reader |
| `presserl.media.max-size` | `10M` | any | deployment |
| `presserl.theme.css` | built-in theme | `/theme/custom.css` | deployment |

## Data model (MVP)

- **Newspaper** — name, subtitle, visibility, settings (JSON, overrides only)
- **Section** — name, colour, order, settings (JSON, overrides only)
- **Membership** — user × newspaper × role (× section for section editors and reporters)
- **Article** — kicker, headline, subheadline, lead, body (Tiptap JSON, validated server-side against an allowlist), author, section, status, lead image
- **ArticleRevision** — revisions; `liveRevision` points to the published one
- **Issue** — number and publication date; groups articles; basis for the issue print view
- **Media** — upload, EXIF-stripped, resized (thumbnail / web / print)
- **ReviewNote** — feedback to the author

## REST API sketch

Draft only — the contract becomes binding in `ai/primer/endpoints.md` once an OpenSpec change implements it.

```
GET    /api/newspaper                     name, subtitle, effective settings
GET    /api/sections
GET    /api/articles?status=PUBLISHED     front page / archive
GET    /api/articles/{id}
POST   /api/articles                      reporter+
PUT    /api/articles/{id}                 creates a new revision
POST   /api/articles/{id}/publish         → PUBLISHED or SUBMITTED (server decides, response says which)
POST   /api/articles/{id}/approve|reject  reviewing role
POST   /api/articles/{id}/offline         author, section editor, publisher, operator
GET    /api/review-queue                  what I have to review (empty for solo)
POST   /api/media                         reporter+ (size/type limit)
GET    /api/issues/{id}/print             data for the issue print view
GET    /api/me                            my roles and allowed actions
```

Article responses carry `allowedActions`; the frontend renders buttons from it and never re-implements the review rule.

## Frontend views

1. **Front page** — masthead, lead story, modular article cards
2. **Article page** — reading mode with byline
3. **Section / archive / issues**
4. **My articles** — cards by status, big "New article" button
5. **Editor** — age levels, live preview in the real newspaper look
6. **Newsroom** — review queue (only visible when there is something to review)
7. **Settings** — newspaper, sections, members/roles (publisher)
8. **Print views** — article and whole issue

Every view sets `data-view="…"` on `<main>` (e.g. `frontpage`, `article`, `editor`, `print-issue`) as a stable hook for custom CSS.

## Security checklist (MVP)

- Keycloak: self-registration off, invitation by publisher/operator, PKCE, short-lived access tokens
- Every action checked server-side against memberships; ownership checks
- Rich text only as Tiptap JSON with a node/mark allowlist — never raw HTML; strict CSP (`self` only)
- Uploads: MIME sniffing, size limit, re-encoding, EXIF/GPS removal
- Free choice of author name (nickname); no real-name requirement
- Backups of PostgreSQL and the media volume via cron
