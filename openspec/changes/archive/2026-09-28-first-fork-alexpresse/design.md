## Context

See proposal.md (Why). Current state:

- `../presserl-deployment` (GitHub `UnterrainerInformatik/presserl-deployment`) is the staging site
  `presserl.unterrainer.info` on babylon5. Its Traefik entrypoints are LAN/VPN only. Upstream's
  `dispatch-staging` job redeploys it after every image build.
- `../alexpresse` (GitHub `guFalcon/alexpresse`, remote `upstream` = presserl-deployment) serves
  `alexpresse.net` and `www.alexpresse.net` publicly from realm `alexpresse` on
  `auth.unterrainer.info`. It is merged up to upstream `157d5cc`. It differs only in `site.env`,
  the realm file and its README, and its `theme/custom.css` is still the no-op starter. A push
  to its `master` redeploys the latest upstream release tag.
- The reader loads `/theme/custom.css` after `reader.css`. Forks restyle through public tokens,
  `data-view` and the documented classes (reader-theme spec). The theme directory serves only
  css, fonts and images.
- `design-guidelines.md`: dark mode and fork colours never reach paper (print). The readability
  rules (contrast ≥ 4.5:1, tinted paper, no justification, no italics in body text) apply to forks
  too.

## Goals / Non-Goals

**Goals:**
- An Alex-Presse look that a reader of nachrichten.at recognises in its layout and colours,
  built only from the public styling API.
- A walkthrough of `INSTALL.md` that is literal enough to find real gaps, and reproducible.

**Non-Goals:**
- A reusable "OÖN" example theme upstream. The look belongs to the fork (`docs/naming.md`:
  newspaper-specific things live in forks).
- Committing the walkthrough harness to the repo. It lives in the scratchpad. Only its findings
  land in `INSTALL.md`.

## Decisions

### D1 — Topology stays: staging auto, Alex-Presse on merge

`presserl-deployment` remains staging and keeps receiving the dispatch. Alex-Presse is updated
by `git pull upstream master` + push (redeploys the latest release tag) or a manual workflow run.
This gives a natural promotion step: a release runs on staging first, then Gerald merges it
into the fork.
*Alternative:* add Alex-Presse to upstream's dispatch. Rejected: every image would go straight to
the public family newspaper without a staging look.

### D2 — Theme tokens (light)

Derived from nachrichten.at's stylesheet (`rf22-refresh.css`, fetched 2026-09-28):

| Token | Value | Source / note |
|---|---|---|
| `--presserl-font-headline` | `"Merriweather", "Georgia", serif` | OÖN headlines |
| `--presserl-font-body` | `"Lato", "Arial", sans-serif` | OÖN body/UI |
| `--presserl-color-paper` | `#f9fafc` | OÖN light background, a slight tint as the guidelines ask |
| `--presserl-color-ink` | `#152735` | OÖN navy-black |
| `--presserl-color-muted` | `#525252` | OÖN grey; ≥ 4.5:1 on paper |
| `--presserl-color-accent` | `#2a4b64` | darker OÖN blue for links/quote rule (`#41739a` is borderline on `#f9fafc`) |
| `--presserl-color-rule` | `#d0d4d7` | OÖN rule grey |

Plus fork-local rules on the stable classes, screen only (`@media screen`): full-width navy
(`#152735`) masthead bar with the name in white Merriweather 900, `.masthead__tools`,
`.masthead__viewer` and `.masthead__issue` in white/light grey on the bar; `.section-bar` as a
white navigation strip with a blue (`#41739a`) bottom border; `.kicker` in OÖN red (`#cb2036`,
≥ 4.5:1 on paper), uppercase allowed because kickers are not body text; headlines left-aligned in
Merriweather; `.lead-image__caption` muted and small. Contrast values are checked by a script in
the verification task, not by eye. Values that miss 4.5:1 get darkened, and the table is
updated in the fork's CSS comments.

Dark mode (`prefers-color-scheme: dark`): paper `#0f1a24`, ink `#e6ebf0`, muted `#aab4bd`, accent
`#8fb6d6`, rule `#2a3a48`, kicker red `#ff5268` (OÖN's light red). Masthead bar stays navy, one step
darker (`#0b141c`), with a bottom rule.

Section colours keep the default palette. They are editorial choices and already contrast-checked.

*Alternative:* OÖN's exact link blue `#41739a` as accent. Rejected: about 4.6:1 on the tinted
paper leaves no margin for the smallest text size.

### D3 — Fonts: self-hosted OFL woff2

Lato (400, 700) and Merriweather (700, 900), `latin` + `latin-ext` subsets as woff2, taken from
the `@fontsource/lato` and `@fontsource/merriweather` npm packages (exact versions recorded in the
CSS header), placed in `../alexpresse/deploy/theme/fonts/` with their `OFL.txt`. The OFL files are
not served, because `.txt` is outside the allow-list, but they ship in the repo, which the licence
requires. Using Google Fonts' CDN is not an option: the reader's CSP is `default-src 'self'`.

### D4 — Only the look is borrowed

No OÖN name, logo, "OÖN" wordmark, icons or images. The masthead shows `PRESSERL_NEWSPAPER_NAME`
("Alex-Presse") as text. The CSS header comment credits "layout inspired by nachrichten.at".

### D5 — Stable styling API: document what already exists

The hooks added to the spec are already rendered today (templates `issue.html`, `issues.html`,
`print*.html`, `tags/masthead.html`, `tags/issueLine.html`, `tags/leadImage.html`). This change
only makes them a contract: spec, starter comments, theme README and a `@QuarkusTest` per new
scenario (extending `ReaderIssuesTest`, `ReaderPrintTest`, `ReaderMediaTest` or `ReaderThemeTest`,
whichever already sets up the data). Print-internal classes (`print-sheet`, `print-columns`, …)
stay internal. Print is steered through `data-view` and `--presserl-grid-columns` as the
reader-print spec already says.

### D6 — INSTALL walkthrough harness

Everything runs in one Docker network `presserl-walk`, in a fresh scratch directory:

- **Keycloak 26** (`quay.io/keycloak/keycloak:26.x start-dev`, `KC_HOSTNAME=https://auth.presserl.test`,
  `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true`). It plays the operator's existing
  Keycloak and is not part of the guide.
- **Caddy** with `tls internal` terminating `news.presserl.test` → `presserl:8080` and
  `auth.presserl.test` → `keycloak:8080`. Network aliases for both names make hairpin access
  work from inside the network. Caddy follows the guide's Caddy section (step 5), while Traefik
  labels are only checked for plausibility (`docker compose config`). Babylon5 already proves
  Traefik in production.
- **Presserl**: a fresh copy of `deploy/` exactly as step 1 names it. The image is the compose
  default `gufalcon/presserl:latest` (`PRESSERL_IMAGE` left commented out, as in `.env.example`),
  not a local build, because that is what an operator gets. So the upstream commit of this change
  must be released before the final passing run (D7 step 1).
- **Trust of the private CA** is a harness artefact, not an operator gap: a real Keycloak has a
  public certificate. The harness adds the Caddy root to presserl through a
  `compose.override.yaml` in the scratch dir, using whatever setting the image already honours
  (e.g. `QUARKUS_OIDC_TLS_*` / JVM truststore via `JAVA_TOOL_OPTIONS`). If the image offers no
  such knob, that is noted under Troubleshooting ("Keycloak with a private CA"). It gets no
  backend feature in this change.
- **Clicks** (Keycloak admin console steps 2/2a, `/admin/` editorial loop, reader login) are
  driven by the Playwright container from `reference_build_and_test.md` on the same network with
  `ignoreHTTPSErrors`. Both 2 (new realm) and 2a (partial import into an empty realm) are
  walked through, in two runs.

Rule while walking: do exactly what the text says. Every time a step needs knowledge not in the
guide, that is a finding, recorded with the step number in the task list and fixed in
`INSTALL.md`. The run is repeated from scratch after the fixes until it passes without findings.
All containers and networks are removed afterwards.

*Alternative:* walk the guide on babylon5 with a throwaway hostname. Rejected: it touches
the shared Traefik and Keycloak and is not repeatable.

### D7 — Rollout order and approval

1. Upstream commit (spec, tests, INSTALL, docs). The push triggers a new image and a staging
   deploy.
2. `presserl-deployment` commit (starter comments, README). The push redeploys staging.
3. `alexpresse`: `git pull upstream master`, theme commit. Before pushing, show Gerald local
   screenshots of the theme (front page, article, issue, archive, print; light and dark; text
   sizes S and XL), rendered by a local backend with `presserl.theme.dir` pointing at
   `../alexpresse/deploy/theme`. **Push only after his go.** Afterwards, check
   `https://alexpresse.net/theme/custom.css` and a screenshot of the live front page.

Every push is outward-facing and gets asked for separately.

## Risks / Trade-offs

- [The theme looks "like OÖN" and is mistaken for their brand] → own name only, no assets, a
  different wordmark font weight, and a credit comment. It is a private family newspaper.
- [Font files bloat the fork repo (~300 KB)] → only 4 weights × 2 subsets as woff2.
- [Walkthrough harness deviations hide real gaps] → the only allowed deviations are the Keycloak
  container, private-CA trust and the Playwright driver, each listed in the findings.
- [A starter comment change in `deploy/theme/custom.css` conflicts when forks merge upstream] →
  forks overwrite `custom.css` with their own theme anyway. On a conflict, Alex-Presse keeps its
  version.
- [Live redeploy of alexpresse.net fails] → rollback: `git revert` the theme commit and push, or
  delete `custom.css` on the server. The reader falls back to the default theme without a
  restart.

## Migration Plan

No data migration. Deploy order as in D7. Rollback per repo with `git revert` + push.

## Open Questions

- Which exact Keycloak 26 minor version and Presserl release the walkthrough uses. This is
  decided at run time (latest) and recorded in the findings.
