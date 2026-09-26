## Why

The concept written down in `docs/` (commit 825b8d7) no longer matches the decisions taken on
2026-09-26: the role model was renamed and restructured, the review rule was replaced by an
approval chain with per-person trust, accounts work without e-mail, and the Vue SPA was
replaced by a Qute-rendered reader plus a Compose Multiplatform administration app. The plan
has to be corrected before M0 scaffolds anything against the old one.

## What Changes

- **Roles renamed and restructured** (German UI label → enum):
  - *Herausgeber* → `PUBLISHER`: parents, the administrator (accounts, theme, backups,
    emergency brake). Replaces the former *Betreiber/Operator*. **BREAKING** (concept):
    `OPERATOR` is removed.
  - *Chefredakteur* → `EDITOR_IN_CHIEF`: the child who owns the newspaper; several allowed;
    the only role that creates sections. (Formerly *Herausgeber/Publisher*.)
  - *Ressortleiter* → `SECTION_EDITOR`: leads 1..n sections. (Formerly *Chefredakteur*,
    exactly one section.)
  - *Redakteur* → `REPORTER`: writes for 1..n sections.
  - *Leser* → `READER`: reads a private newspaper.
  - Each role includes every role below it. Anyone may create accounts and assign roles at or
    below their own level. New accounts need no confirmation.
  - Newspaper-wide roles are Keycloak groups; per-section roles live in the Presserl database.
    The backend manages Keycloak through a service account.
- **Approval chain replaces the review rule**: Reporter → Section editor → Editor-in-chief →
  Publisher. One person per level suffices; levels whose role the author holds are skipped;
  a per-person *trust* switch (default off) removes one level's check for one person. Taking
  offline never needs approval; a publisher's emergency brake can only be lifted by a
  publisher. **BREAKING** (concept): `presserl.review.mode` and
  `presserl.review.chief-needs-publisher` are removed.
- **One newspaper per server**; the newspaper is a singleton. Default visibility becomes
  **public**.
- **Setup**: the installation creates the first account as publisher (holding all roles, so a
  solo user publishes immediately). The publisher logs in and creates an editor-in-chief.
- **Accounts without e-mail**: username = first name, default password = generated German
  word phrase joined by dashes, handed over on a printed slip (web address, username,
  password). QR invitations come later.
- **Frontend split**: the *reader* (front page, articles, print views, fork theming) is plain
  web, server-rendered by Quarkus with Qute; the *administration app* (writing, approving,
  accounts, sections) is Compose Multiplatform, web (Wasm) target first, Android/iOS later.
  **BREAKING** (concept): Vue 3, Pinia, keycloak-js, Tiptap and Vitest are dropped.
- **Repositories**: everything lives in this monorepo except the real fork
  `../presserl-deployment`, which contains only name, `.env` and theme. `deploy/` in the
  monorepo carries a step-by-step installation guide plus template files.
- **Milestones re-cut** in `docs/vision.md` and `ai/open-proposals.md`.
- Project config (`.claude/CLAUDE.md`, `openspec/config.yaml`) and `README.md` follow the new
  stack and layout.

## Capabilities

### New Capabilities
_None._ This change revises planning documents only. Behavioural specs (roles, approval
chain, accounts, reader) are written by the changes that implement them (M0 onwards), so
`openspec/specs/` never describes behaviour that does not exist yet. `skip_specs: true` is
set.

### Modified Capabilities
_None._

## Non-goals

- No code, no scaffolding — `backend/`, `admin/`, `deploy/` and the fork repository are
  created in M0.
- No REST contract: the API sketch in `docs/architecture.md` stays a sketch;
  `ai/primer/endpoints.md` is untouched.
- No choice of the Compose rich-text editor or the article body format beyond "structured
  JSON validated against an allowlist" — decided in the milestone that builds the editor.
- No QR invitations, mobile targets or store publishing — planned as a later milestone only.
- Memory files under `ai/memory/` are updated alongside, but outside this change.

## Impact

- Platforms: **none at runtime** — documentation and project configuration only (affects the
  future backend and admin app by changing the plan they are built against).
- Files: `docs/vision.md`, `docs/roles-and-workflow.md`, `docs/architecture.md`,
  `docs/design-guidelines.md`, `docs/diagrams/*.puml` and rendered `*.svg`, `README.md`,
  `.claude/CLAUDE.md`, `openspec/config.yaml`, `ai/open-proposals.md`.
- Dependencies dropped from the plan: Vue 3, Vite, Pinia, keycloak-js, Tiptap, Vitest, the
  nginx `web` container. Added: Compose Multiplatform (Kotlin/Wasm, Gradle), Qute, Keycloak
  Admin API client.
