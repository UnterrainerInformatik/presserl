# Presserl

> *"Every child has a story. Presserl turns it into a newspaper."*

**Presserl** (Austrian diminutive of *Presse* — "the little press") is a self-hosted, multi-user newspaper platform for children aged 6–16. Kids write, photograph and report; the result looks like a real newspaper — front page, sections, issues, bylines and print views. It is safe by design and simple enough for a first-grader, yet grows with the newsroom.

Status: **concept / pre-scaffolding** — no code yet. Work starts via OpenSpec changes (see `ai/open-proposals.md`).

## Highlights

- **Grows with complexity.** Alone you are the publisher: write → *Publish* → online, and *Take offline* again whenever you like. As soon as reporters join, reviews switch on automatically — only where more than one person is involved.
- **Safe by design.** Invitation-only accounts via Keycloak, parents as operators with an emergency brake, no comments from strangers, no tracking, no third-party CDNs, **no advertising**, EXIF/GPS stripped from uploads, private by default.
- **Made for 6–16.** Age-appropriate typography, a reader text-size switch and an editor with three levels (`starter`, `standard`, `profi`).
- **Real newspaper feel.** Masthead, lead story, modular grid, print views for single articles and whole issues.
- **Minimal configuration.** Sensible defaults everywhere; `docker compose up` is enough. Everything is overridable — via environment, per newspaper, per section.
- **Forkable & themeable.** Forks only touch `deploy/` — name via `.env`, look via `deploy/theme/custom.css`. Upstream images stay untouched, so updates are an image-tag bump.

## Stack

| Part | Technology |
|---|---|
| Backend | Quarkus, Quarkus REST (reactive), Hibernate Reactive with Panache, reactive PostgreSQL client, quarkus-oidc, Flyway |
| Database | PostgreSQL |
| Identity | Keycloak |
| Frontend | Vue 3 + TypeScript, Vite, Pinia, keycloak-js, Tiptap |
| Operations | docker compose |

## Documentation

| Document | Content |
|---|---|
| [docs/vision.md](docs/vision.md) | Pitch, principles, target audience, milestones |
| [docs/roles-and-workflow.md](docs/roles-and-workflow.md) | Roles, editorial workflow, review rules |
| [docs/architecture.md](docs/architecture.md) | Architecture, repository layout, configuration layers, data model, API sketch, security |
| [docs/design-guidelines.md](docs/design-guidelines.md) | Newspaper layout, readability for 6–16, editor, print views, theming |
| [docs/naming.md](docs/naming.md) | Name research |
| [docs/diagrams/](docs/diagrams/) | PlantUML sources + rendered SVGs |

## License

Public domain — see [LICENSE](LICENSE).
