# Presserl

> *"Every child has a story. Presserl turns it into a newspaper."*

**Presserl** (Austrian diminutive of *Presse* — "the little press") is a self-hosted, multi-user newspaper platform for children aged 6–16. Kids write, photograph and report; the result looks like a real newspaper — front page, sections, issues, bylines and print views. It is safe by design and simple enough for a first-grader, yet grows with the newsroom.

Status: **concept / pre-scaffolding** — no code yet; the plan was revised on 2026-09-26 (roles, approval chain, reader/admin split). Work starts with M0 via OpenSpec changes (see `ai/open-proposals.md`).

## Highlights

- **Grows with complexity.** Alone you hold every role: write → *Publish* → online, and *Take offline* again whenever you like. When parents, siblings and friends join, an approval chain (section editor → editor-in-chief → publisher) switches on — and each level drops out once it *trusts* the author.
- **Safe by design.** Parents as publishers who approve until they trust and hold an emergency brake; accounts without e-mail, handed over on a printed slip with a word pass-phrase; no comments from strangers, no tracking, no third-party CDNs, **no advertising**, EXIF/GPS stripped from uploads. Public by default, private by choice.
- **Made for 6–16.** Age-appropriate typography, a reader text-size switch and an editor with three levels (`starter`, `standard`, `profi`).
- **Reader and app.** Readers get plain, printable, themeable HTML; the newsroom works in an administration app (web first, Android/iOS later).
- **Real newspaper feel.** Masthead, lead story, modular grid, print views for single articles and whole issues.
- **Minimal configuration.** Sensible defaults everywhere; `docker compose up` is enough. Everything is overridable — via environment, per newspaper, per section.
- **Forkable & themeable.** A deployment repository (e.g. `presserl-deployment`) copies the templates from `deploy/` and sets only name, `.env` and `theme/custom.css`. Upstream images stay untouched, so updates are an image-tag bump.

## Stack

| Part | Technology |
|---|---|
| Backend | Quarkus, Quarkus REST (reactive), Hibernate Reactive with Panache, reactive PostgreSQL client, quarkus-oidc, Flyway |
| Reader | Qute templates rendered by the backend (plain HTML + CSS design tokens) |
| Administration app | Compose Multiplatform (Kotlin, Gradle) — Wasm web target first, Android/iOS later |
| Database | PostgreSQL |
| Identity | Keycloak (groups for newspaper-wide roles, managed by the backend via a service account) |
| Operations | docker compose: `presserl`, `keycloak`, `postgres` |

## Repositories

This monorepo holds everything upstream: `backend/`, `admin/`, `deploy/` (installation guide `INSTALL.md` and templates), `http/`, `docs/`, `openspec/`. A real newspaper runs from its own deployment repository — the first one is `presserl-deployment` — which contains only name, `.env` and theme.

## Documentation

| Document | Content |
|---|---|
| [docs/vision.md](docs/vision.md) | Pitch, principles, target audience, milestones |
| [docs/roles-and-workflow.md](docs/roles-and-workflow.md) | Roles, accounts, approval chain, trust, emergency brake |
| [docs/architecture.md](docs/architecture.md) | Architecture, repository layout, configuration layers, data model, API sketch, security |
| [docs/design-guidelines.md](docs/design-guidelines.md) | Newspaper layout, readability for 6–16, editor, print views, theming, account slip |
| [docs/naming.md](docs/naming.md) | Name research |
| [docs/diagrams/](docs/diagrams/) | PlantUML sources + rendered SVGs |

## License

Public domain — see [LICENSE](LICENSE).
