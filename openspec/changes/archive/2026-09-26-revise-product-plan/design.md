## Context

`docs/` describes the concept agreed on 2026-09-26 in the morning (Vue SPA, operator +
publisher + section editor + reporter, `review.mode`). The decisions listed in proposal.md
supersede it. No code exists yet, so only documents and project configuration change. This
design fixes the target picture the documents must describe, so that M0 can scaffold against
it.

## Goals / Non-Goals

**Goals:**
- One consistent, conflict-free description of roles, approval, accounts, frontend split,
  deployment and milestones across all documents and diagrams.
- Project configuration (`.claude/CLAUDE.md`, `openspec/config.yaml`) that steers future
  changes towards the new stack (Kotlin/Compose tests instead of Vitest, `admin/` instead of
  `frontend/`).

**Non-Goals:**
- Exact REST shapes, DB schema, Keycloak realm JSON — those come with the implementing
  changes.

## Decisions

### D1 — Role model

| Role | Enum | Stored in | Scope | Adds to the role below |
|---|---|---|---|---|
| Herausgeber | `PUBLISHER` | Keycloak group `publisher` | newspaper + technology | administration (accounts: create, lock, reset password; backups), emergency brake, final approval level |
| Chefredakteur | `EDITOR_IN_CHIEF` | Keycloak group `editor-in-chief` | whole newspaper | creates sections, approves section editors' articles, stands in for sections without a section editor |
| Ressortleiter | `SECTION_EDITOR` | Presserl DB (per section) | 1..n sections | approves reporters of their sections |
| Redakteur | `REPORTER` | Presserl DB (per section) | 1..n sections | writes, submits, takes own articles offline |
| Leser | `READER` | Keycloak group `reader` | newspaper | reads a private newspaper |

- Roles are cumulative: a role includes all roles below it (an editor-in-chief may write in
  every section; a section editor writes in their sections).
- A person can hold roles on several levels; the highest role relevant to the article's
  section counts.
- Several people may hold the same role (two publishers, two chiefs, several section editors
  per section).
- **Delegation**: anyone may create accounts and assign roles at or below their own level
  within their own scope (a section editor assigns reporters only to their own sections).
  No confirmation step.
- Why Keycloak groups for newspaper-wide roles and DB rows for section roles: group
  membership fits Keycloak's model and ends up in the token; per-section lists do not map
  cleanly onto Keycloak and change often. Alternative "everything in the DB" was rejected
  because Keycloak groups give the admin console a truthful picture of who is publisher.

### D2 — Approval chain

Levels, bottom to top: **section editor (of the article's section) → editor-in-chief →
publisher.** For an article by author *A* in section *S*:

1. Start at the level directly above *A*'s highest role in *S*.
2. A level is **skipped** when *A* holds that level's role, when any person of that level
   has set *trust* on *A*, or — for the section-editor level — when *S* has no section editor
   (the editor-in-chief level then takes over).
3. Each remaining level needs the approval of **one** person holding that role (other than
   *A*). Rejection sends the article back to *Draft* with a note.
4. When no level remains, the article is published.

Consequences: a solo first account (holds all roles) publishes immediately. Parents as
publishers check every article until they trust the child. A section editor's article goes
chief → publisher.

- **Trust** is a per-person switch set by a holder of the approving level on a specific
  person below, default off (e.g. trust a 16-year-old, keep checking a 7-year-old). Trust by
  one holder of a level applies to the whole level. Trust is stored in the Presserl DB.
- **Taking offline** never needs approval (author, section editors of the section, chiefs,
  publishers). **Emergency brake**: an article taken offline by a publisher is locked; only
  a publisher can put it back online. Back online otherwise follows the approval chain.
- Editing a published article creates a revision; the live revision stays until the new one
  passes the chain.
- The server decides; responses carry `allowedActions`.
- Config keys `presserl.review.mode` and `presserl.review.chief-needs-publisher` are
  removed. `presserl.retract.author-can-retract` stays.

### D3 — One newspaper per server, public by default

The newspaper is a singleton row; `Membership` loses its newspaper dimension. A second
newspaper is a second deployment. `presserl.newspaper.visibility` defaults to `public`. The
configuration layers stay code → deployment → newspaper → section (the section layer keeps
future per-section overrides possible).

### D4 — Accounts and setup

- No e-mail anywhere; Keycloak users without e-mail, self-registration and "forgot password"
  off. Password reset by anyone above the person (delegation rule).
- Username = first name, lowercase, ASCII-folded; collision → suffix (`anna`, `anna-2`,
  editable).
- Default password = four words from a curated, kid-friendly German word list, no umlauts/ß,
  joined by dashes (`tiger-wolke-apfel-leiter`); Keycloak brute-force detection on.
- Hand-over = printable slip: newspaper name, web address, username, password.
- **Bootstrap**: on first start, if no publisher exists, the backend creates one from
  `PRESSERL_PUBLISHER_USERNAME` / `PRESSERL_PUBLISHER_PASSWORD` in `.env`. That account holds
  all roles. Setup flow in the guide: log in → create the child as editor-in-chief → (it
  starts writing).
- Later milestone: QR code on the slip carrying server URL + one-time token, exchanged by the
  backend for a Keycloak login.

### D5 — Frontend split

- **Reader** (`/`): server-rendered HTML from Quarkus Qute templates — front page, sections,
  article pages, print views. Owns the design tokens, `data-view` hooks and the fork theme
  (`/theme/custom.css`). Private newspapers: Quarkus OIDC code flow with a session cookie.
  Why: print (`@page`), CSS theming, accessibility, search engines and link sharing need a
  real DOM; no second frontend build.
- **Administration app** (`/admin`): Compose Multiplatform, Kotlin/Wasm web target first;
  Android/iOS targets later from the same code base. Writing, approving, accounts, sections,
  trust, settings. Talks to `/api` with bearer tokens (OIDC auth code + PKCE; KMP OIDC
  library chosen in M0). Preview links to the reader.
- Why Compose: one UI for web, Android and iOS (store release, QR scanning later). Rejected:
  Vue + Capacitor (two paradigms once native features grow), Compose for the reader (canvas
  rendering breaks print, CSS theming, accessibility).
- The article body is structured JSON validated server-side against an allowlist; the
  concrete format and the Compose rich-text editor are chosen in the milestone that builds
  the editor.

### D6 — Deployment and repositories

- Containers: `presserl` (Quarkus: API + reader + static admin bundle — one image, same
  origin, CSP `self`), `keycloak`, `postgres`. The nginx `web` container is dropped.
- Monorepo layout: `backend/` (Maven), `admin/` (Gradle, Compose MP), `deploy/`, `http/`,
  `docs/`, `openspec/`, `ai/`.
- `deploy/` = reference deployment and templates: `INSTALL.md` (step-by-step), `compose.yaml`,
  `.env.example` (mandatory values only: hostname, passwords, first publisher),
  `keycloak/presserl-realm.json`, `theme/` template.
- `../presserl-deployment` = the real fork: copies the templates, sets name/`.env`/theme,
  runs upstream images. Maintained by Claude as well; changes to it are recorded in the
  monorepo's OpenSpec changes.

### D7 — Milestones (new cut)

| # | Goal | Outcome |
|---|---|---|
| M0 | Skeleton | backend + Qute reader stub + Compose web admin stub + `deploy/` (guide, templates) + publisher bootstrap + end-to-end login + CI images + fork repo wired |
| M1 | Solo newspaper | articles with revisions, editor, publish/offline, reader front page + article page |
| M2 | Accounts & sections | account creation with pass-phrase and printable slip, Keycloak service account, groups, sections, section roles, delegation |
| M3 | Approval chain | chain, trust switches, review queue and notes, emergency-brake lock, `allowedActions` |
| M4 | Look | reader theme tokens, fonts, `custom.css`, text size, dark mode |
| M5 | Images | upload, re-encoding, EXIF/GPS stripping, renditions, lead images |
| M6 | Print | issues, print views |
| M7 | First fork | `presserl-deployment` live for a real family newspaper |
| M8 | Mobile & QR | Android/iOS targets, QR invitation token exchange, store publishing (Google Play Families policy, Apple developer account) |

Accounts and approval move before *Look* because the family use case (parents approve the
child's articles) is the first real deployment.

## Risks / Trade-offs

- [Compose for Web (Wasm) is younger than Vue; browser support and bundle size] → the admin
  app is used by a few known people on current browsers; the reader, which the public sees,
  stays plain HTML.
- [No mature Compose rich-text editor] → evaluated before M1 commits to it; fallback is a
  block-based editor (one field per block), which also suits the `starter` level.
- [Backend holds Keycloak admin credentials (service account)] → client scoped to
  `manage-users` in the `presserl` realm only; never exposed to clients.
- [Children create accounts without confirmation] → deliberate (responsibility); publishers
  see every account and can lock it; approval chain still gates publishing.
- [Pass-phrase of four words ≈ 44 bits] → acceptable with brute-force detection and no
  e-mail recovery path; users may change it.

## Migration Plan

Docs-only; nothing deployed. Old statements are replaced, not kept side by side. The previous
concept remains in git history (825b8d7).
