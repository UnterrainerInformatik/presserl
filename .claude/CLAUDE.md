# CLAUDE Project — presserl

## Working rules
- Memory is in `./ai/memory/MEMORY.md` (index) with one `.md` file per entry under `./ai/memory/`. Treat that index as the auto-memory; keep it in sync the same way you would the per-machine `~/.claude/projects/<cwd>/memory/MEMORY.md`. **Read it at the start of every session.**
- **Conversation language is German.** Always reply to the user in German — every response, from the first message of a session on, without waiting to be reminded. Everything written into the repo (identifiers, comments, log messages, docs, commit messages, OpenSpec artifacts) stays English.
- **No change outside an OpenSpec change** — code, docs and project configuration alike. Announce first, then `/opsx:propose`, implement via `/opsx:apply`. A diagnosis request means diagnose only. See `ai/memory/feedback_announce_changes_first.md` and `ai/memory/feedback_openspec_only_changes.md`.
- Backlog of drafted-but-not-proposed work lives in `./ai/open-proposals.md`.
- Endpoints primer lives in `./ai/primer/endpoints.md`; keep it synchronised when REST endpoints change — it is the contract the admin app is built against.
- Secrets go to `./ai/secrets/` (git-ignored), never into tracked files.

## Repository layout (multi-platform monorepo)
- `backend/` — Java, Quarkus (Maven): REST API **and the reader** (server-rendered HTML via Qute). Tests: JUnit 5 + AssertJ (`@QuarkusTest` for integration).
- `admin/` — administration app, Compose Multiplatform (Kotlin, Gradle), Wasm web target first, Android/iOS later. Tests: Kotlin (`kotlin.test`, Compose UI tests where useful).
- `openspec/` — specs and changes; one spec tree for all platforms. A change that touches the REST contract covers backend, admin API client and `ai/primer/endpoints.md` together.
- `deploy/` — reference deployment and templates: `INSTALL.md`, docker compose, Keycloak realm, `.env.example`, `theme/`.
- `docs/` — product vision, roles/workflow, architecture, design guidelines, diagrams (PlantUML + SVG).
- `ai/` — memory, primer, open proposals, captures. Not shipped.
- `http/` — `.http` request files exercising the backend REST API.

Two deployment repositories hold name, `.env` and theme only: `../presserl-deployment` is staging (`presserl.unterrainer.info`, LAN/VPN, redeployed on every upstream image), `../alexpresse` is the first public fork (`alexpresse.net`, remote `upstream` = presserl-deployment, updated by merging upstream). Claude maintains both; their changes are recorded in this repo's OpenSpec changes.

Build/test commands are recorded in memory (`reference_build_and_test.md`) once the projects are scaffolded.
