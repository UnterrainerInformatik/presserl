## 1. Contract/Docs — product documents

- [x] 1.1 `docs/roles-and-workflow.md`: new role table (D1), delegation rule, approval chain with skip/trust rules (D2), growth stages with the family example (solo → parents supervise → second chief / section editor → reporters), emergency-brake lock; remove `review.mode`/`chief-needs-publisher` overrides
- [x] 1.2 `docs/architecture.md`: stack (Qute reader, Compose MP admin, Keycloak groups + service account, no nginx), repository layout incl. `admin/` and `../presserl-deployment`, config table (visibility `public`, removed review keys, bootstrap publisher env vars), data model (singleton newspaper, section roles, trust), API sketch (roles, `/admin`, reader routes), views split reader/admin, security checklist (no e-mail, pass-phrases, brute-force detection)
- [x] 1.3 `docs/vision.md`: principles (grows with complexity via roles/trust, safe by design with parents as publishers, public by default, forkable via `presserl-deployment`), target audience, milestone table per D7
- [x] 1.4 `docs/design-guidelines.md`: theming and print apply to the reader; editor section refers to the admin app (Compose) and drops Tiptap; add account slip (printable) guideline
- [x] 1.5 `README.md`: highlights, stack table, status line, repository/fork note

## 2. Contract/Docs — diagrams

- [x] 2.1 Rewrite `docs/diagrams/roles.puml` (five roles, KC vs DB, cardinalities, delegation)
- [x] 2.2 Rewrite `docs/diagrams/review-decision.puml` as the approval chain with skip/trust
- [x] 2.3 Update `docs/diagrams/article-lifecycle.puml` (approval levels, emergency-brake lock)
- [x] 2.4 Update `docs/diagrams/architecture.puml` (browser → reader HTML + admin Wasm, single `presserl` container, service account to Keycloak)
- [x] 2.5 Render all four via `https://plantuml.unterrainer.info/plantuml` and commit the SVGs next to the sources

## 3. Project configuration and backlog

- [x] 3.1 `.claude/CLAUDE.md`: repository layout (`admin/` Compose MP/Gradle with Kotlin tests; reader in `backend/` via Qute), deployment repo `../presserl-deployment`, OpenSpec for docs too
- [x] 3.2 `openspec/config.yaml`: context (admin = Compose MP, reader = Qute, Kotlin tests), rules (admin API client instead of frontend client/types; Kotlin tests instead of Vitest)
- [x] 3.3 `ai/open-proposals.md`: replace M0–M6 by the D7 milestones M0–M8 with scope notes

## 4. Memory (outside the change, same session)

- [x] 4.1 Update `feedback_tests_welcome`, `feedback_endpoints_primer`, `feedback_english_only` (i18n wording), `project_vision`, `reference_machine_jdk` (default JDK is now 21)
- [x] 4.2 Add `project_deployment_repo` (fork at `../presserl-deployment`, owned by Claude) and link it from `MEMORY.md`

## 5. Verification

- [x] 5.1 `grep` the repo (excluding `openspec/changes/` and git history) for stale terms: `OPERATOR`, `Operator`, `Betreiber`, `Vue`, `Vitest`, `Tiptap`, `keycloak-js`, `Pinia`, `review.mode`, `chief-needs-publisher`, `frontend/`, `private by default`
- [x] 5.2 Cross-check each document against D1–D7 (role names, chain order, defaults, milestones identical everywhere)
- [x] 5.3 `openspec validate revise-product-plan` passes
