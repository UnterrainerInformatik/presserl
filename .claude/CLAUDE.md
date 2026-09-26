# CLAUDE Project — presserl

## Working rules
- Memory is in `./ai/memory/MEMORY.md` (index) with one `.md` file per entry under `./ai/memory/`. Treat that index as the auto-memory; keep it in sync the same way you would the per-machine `~/.claude/projects/<cwd>/memory/MEMORY.md`. **Read it at the start of every session.**
- **Conversation language is German.** Always reply to the user in German — every response, from the first message of a session on, without waiting to be reminded. Everything written into the repo (identifiers, comments, log messages, docs, commit messages, OpenSpec artifacts) stays English.
- **No code change outside an OpenSpec change.** Announce first, then `/opsx:propose`, implement via `/opsx:apply`. A diagnosis request means diagnose only. See `ai/memory/feedback_announce_changes_first.md` and `ai/memory/feedback_openspec_only_changes.md`.
- Backlog of drafted-but-not-proposed work lives in `./ai/open-proposals.md`.
- Endpoints primer lives in `./ai/primer/endpoints.md`; keep it synchronised when REST endpoints change — it is the contract the frontend is built against.
- Secrets go to `./ai/secrets/` (git-ignored), never into tracked files.

## Repository layout (multi-platform monorepo)
- `backend/` — Java, Quarkus (Maven). Tests: JUnit 5 + AssertJ (`@QuarkusTest` for integration).
- `frontend/` — Vue 3 + TypeScript (Vite). Tests: Vitest (+ Vue Test Utils).
- `openspec/` — specs and changes; one spec tree for both platforms. A change that touches the REST contract covers backend, frontend and `ai/primer/endpoints.md` together.
- `ai/` — memory, primer, open proposals, captures. Not shipped.
- `http/` — `.http` request files exercising the backend REST API.

Build/test commands are recorded in memory (`reference_build_and_test.md`) once the projects are scaffolded.
