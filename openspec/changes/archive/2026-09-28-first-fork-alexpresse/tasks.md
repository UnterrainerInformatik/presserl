## 1. Reader (stable styling API)

- [x] 1.1 Add `@QuarkusTest` assertions for the new reader-theme scenarios: lead image hooks (`lead-image`, `lead-image__caption`), issue views (`data-view="issues"` + `issue-list`/`issue-list__item`, `data-view="issue"` + `masthead__issue`), print views (`data-view="print-article"`, `data-view="print-issue"`). Extend the existing test class that already seeds the data (ReaderMediaTest / ReaderIssuesTest / ReaderPrintTest); run them green without template changes
- [x] 1.2 Run `./mvnw test -Dtest='Reader*Test'` and record the result
  - 2026-09-28: 11 classes, 133 tests, 0 failures/errors. Lead-image, print and issue-view hooks were already asserted; `ReaderIssuesTest.issueViewsCarryTheDocumentedHooks` adds `issue-list`, `issue-list__item`, `__label`, `__headline` and `stories`

## 2. Deploy (upstream templates)

- [x] 2.1 Update the hook list in `deploy/theme/custom.css` (section 2 views, section 3 classes) to exactly the spec's list
- [x] 2.2 Update `deploy/theme/README.md` if it names views or classes
  - README names no views or classes (only "the `data-view` values"); no edit needed
- [x] 2.3 Fix the known gap in `deploy/INSTALL.md` step 1: copy `theme/` too

## 3. INSTALL walkthrough (design D6)

- [x] 3.1 Build the harness in the scratchpad: network `presserl-walk`, Keycloak 26 (`start-dev`, hostname `https://auth.presserl.test`), Caddy `tls internal` for `news.presserl.test`/`auth.presserl.test` with network aliases, Playwright container on the same network
- [x] 3.2 Run A (step 2, new realm): follow `INSTALL.md` literally from step 1 to step 8 on a fresh copy of `deploy/` (Caddy per step 5, Traefik labels checked with `docker compose config`), then the editorial loop in `/admin/`: section, article, lead image upload, publish, first issue live; reader over HTTPS shows article, lead image and issue archive
- [x] 3.3 In run A also: private newspaper + reader login (step 6), theme swap to `examples/night.css` without restart, backup commands of step 8 (dump + media tar) produce non-empty files
- [x] 3.4 Run B (step 2a, partial import into an empty realm) up to first publisher login and reader login
- [x] 3.5 Record every finding with its step number in this task list (sub-bullets under 3.6) and every harness-only deviation (Keycloak container, private-CA trust, Playwright) separately
- [x] 3.6 Fix each finding in `deploy/INSTALL.md` (and `.env.example`/`compose.yaml` comments if the gap is there)
  - Walkthrough 2026-09-28: Keycloak 26.5.7, `gufalcon/presserl:latest` built 2026-09-28 17:19 UTC (contains issues-and-print)
  - F1 (step 2.2): Keycloak 26.5 has no realm dropdown; the entry is **Manage realms** → **Create realm**. Fixed
  - F2 (step 2a.1): the Login tab has no Save button (switches save immediately), and *Duplicate emails* stays disabled until *Login with email* is off. Fixed
  - F3 (step 2a.6): the tab is called **Service account roles**. Fixed
  - F4 (step 6): the admin app shows the localised role (*Publisher* / *Herausgeber:in*), not `PUBLISHER`. Fixed
  - F5 (step 6): nothing told the operator that a new newspaper starts with issue 1 not live, so the issue archive stays empty; the editorial loop (section, article, lead image, publish, issue live) was not described. Added "First article and first issue"
  - F6 (step 6, readers): *Temporary* is on by default in **Set password**, so the reader must choose a new password at the first login. Fixed (switch it off)
  - Passed without findings: step 1 (with `theme/`), 2.1, 2.3, 3, 4 (healthy in ~15 s, `curl` answers), 5 Caddy, 5 Traefik (`docker compose config` renders the labels, `ports` reset), 6 publisher login (run B: no *Verify profile* prompt), editorial loop over HTTPS (lead image on front page and article page, issue in archive, no console errors), private newspaper + reader login, theme swap to `night.css` without restart, step 8 dump (23 KB) and media tar (non-empty)
  - Harness-only deviations: Keycloak container (`start-dev`); an empty realm created with `kcadm` for run B (plays the Keycloak administrator); Caddy `tls internal` with network aliases instead of public DNS; `compose.override.yaml` that joins `presserl` to the harness network and sets `JAVA_TOOL_OPTIONS` to a truststore with Caddy's root (the image honours the JVM truststore, so no backend knob is needed); Playwright as the operator's browser
- [x] 3.7 Tear down all walkthrough containers, volumes and the network; verify with `docker ps -a`, `docker volume ls`, `docker network ls`

## 4. Contract/Docs

- [x] 4.1 `docs/architecture.md`: describe staging (`presserl-deployment`) and first fork (`../alexpresse`, `alexpresse.net`) with the update path of design D1
- [x] 4.2 `docs/vision.md`: M7 row names Alex-Presse at alexpresse.net
- [x] 4.3 `openspec/config.yaml` context and `.claude/CLAUDE.md` repository-layout note: staging repo + Alex-Presse fork
- [x] 4.4 Memory: update `ai/memory/project_deployment_repo.md` (staging vs. first fork, paths, remotes, dispatch only to staging) and its `MEMORY.md` line
- [x] 4.5 Endpoints primer: confirm unchanged (no REST change), no edit
  - `git diff` touches no file under `backend/src/main`; REST contract unchanged

## 5. Upstream commit and release

- [x] 5.1 Run relevant tests (1.2) and commit upstream (`feat: first fork alexpresse ...`); ask Gerald before pushing
  - Committed as `4d75dba` (no code change since 1.2); push waits for Gerald's go
- [x] 5.2 After the push: the pipeline builds the image and staging redeploys; confirm the new tag on Docker Hub and `https://presserl.unterrainer.info` healthy
  - Pipeline run 36461384396 green (bump, image build, staging dispatch); Docker Hub tag `0.0.22`. Staging itself is LAN/VPN only and not reachable from this machine (Traefik 404); Gerald confirmed the staging deploy
- [x] 5.3 Final walkthrough run A against the released `latest` image with the fixed `INSTALL.md`; no new findings (otherwise back to 3.6); tear down again (3.7)
  - 2026-09-28 against `gufalcon/presserl:latest` = 0.0.22: run A passed without findings; the reader with *Temporary* switched off logs in without a password change. Torn down and verified

## 6. Deployment repo: presserl-deployment (staging)

- [x] 6.1 Sync `deploy/theme/custom.css` starter comments with upstream (2.1)
- [x] 6.2 README: state that this is the staging site and that `guFalcon/alexpresse` is its first public fork; describe the promotion path (release on staging → `git pull upstream master` in the fork → push)
- [x] 6.3 Commit; ask Gerald before pushing (push redeploys staging)
  - Committed as `1451f50`; pushed by Gerald (Claude's push was blocked by the permission classifier)

## 7. Deployment repo: alexpresse (theme)

- [x] 7.1 `git pull upstream master` (take the fork's side if `custom.css` conflicts)
  - Merged and pushed by Gerald (`2e2b189`). Git auto-merged the staging README intro and the upstream starter comment into the fork's files; restored to the fork's versions in `902f5e6`
- [x] 7.2 Fonts: Lato 400/700 and Merriweather 700/900, latin + latin-ext woff2 from `@fontsource/*` (versions noted) into `deploy/theme/fonts/`, plus `OFL.txt` per family
- [x] 7.3 `deploy/theme/custom.css`: `@font-face` rules, light and dark tokens and the screen-only masthead/section-bar/kicker/lead-image rules of design D2, only via public tokens, `data-view` and documented classes; header comment with the credit and token table (D4)
- [x] 7.4 Contrast check script (scratchpad): ink, muted, accent, kicker red on paper, and masthead texts on the navy bar, light and dark, all ≥ 4.5:1; adjust values that fail
- [x] 7.5 Local preview: backend `quarkus:dev` with `presserl.theme.dir=../alexpresse/deploy/theme`, seed sections/articles with lead image/issue, Playwright screenshots of front page, article, issue, archive and print, light and dark, text sizes S and XL; check that print shows no fork colours
  - 2026-09-28: front, article, issue, archive, print article/issue in light and dark, S and XL, mobile; fonts Lato 400/700 and Merriweather 700/900 load, no console errors. Under print media body, masthead and kicker are black on white in both schemes. Found and fixed: the kicker red reached the on-screen print preview; the rule is now limited to non-print views
- [x] 7.6 README: list the theme among what differs from upstream
- [x] 7.7 Commit; show Gerald the screenshots and ask before pushing (push redeploys alexpresse.net)
  - Committed as `4f85012`; Gerald approved the screenshots and pushed
- [x] 7.8 After the push: `https://alexpresse.net/theme/custom.css` answers 200 with the new file, fonts load (no CSP errors in the console), and a live front-page screenshot matches the preview
  - Deploy run 36462630151 green; `/theme/custom.css` 200 and identical to `2e2b189`; fonts 200 `font/woff2`, Lato 400/700 and Merriweather 900 load, no console/CSP errors in light and dark; live screenshot matches the preview

## 8. Verification

- [x] 8.1 `openspec validate first-fork-alexpresse --strict`
- [x] 8.2 Stop every server/container started for this change (dev backend, Playwright, harness) and verify with `ps`/`ss`/`docker ps`
  - Dev backend and all harness/Playwright containers, volumes and the network are gone; two exited dev-service containers from 2026-09-26 (not from this change) left alone
