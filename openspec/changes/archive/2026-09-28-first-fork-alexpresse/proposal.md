## Why

Milestone M7 ("first fork") assumed `../presserl-deployment` would go live for a real family
newspaper. In reality the first real newspaper already runs: **Alex-Presse** at
`https://alexpresse.net`, a fork of `presserl-deployment` (repo `guFalcon/alexpresse`, locally
`../alexpresse`), while `presserl-deployment` stays the LAN-only staging site. What M7 still
lacks is a look of its own for Alex-Presse, a verified installation guide and documentation that
matches this reality. Theming a real newspaper also shows that the stable styling API stops at the
M4 views: issues, print views and lead images have no documented hooks.

## What Changes

- **Record the real topology**: `presserl-deployment` = staging (`presserl.unterrainer.info`,
  LAN/VPN, auto-deployed on every upstream image); `alexpresse` = first public fork
  (`alexpresse.net`, updated by merging `upstream` into it). Update `docs/architecture.md`,
  `docs/vision.md` (M7 row), `openspec/config.yaml` context, `.claude/CLAUDE.md` and the project
  memory accordingly.
- **Alex-Presse theme** in `../alexpresse/deploy/theme/`: a `custom.css` closely modelled on the
  look of *Oberösterreichische Nachrichten* (nachrichten.at): white paper, navy masthead bar, blue
  links, red accent, Merriweather headlines, Lato body text, with its own dark mode. Fonts are
  self-hosted OFL files in `theme/fonts/`. Only the look is borrowed. No OÖN name, logo, wordmark
  or images.
- **Stable styling API extended** to the views and classes added since M4: `data-view` values
  `issue`, `issues`, `print-article`, `print-issue`, and the classes `.masthead__tools`,
  `.masthead__viewer`, `.masthead__issue`, `.lead-image`, `.lead-image__caption`, `.issue-list`,
  `.issue-list__item`, `.issue-list__label`, `.issue-list__headline`, `.stories`. The `custom.css`
  starter comments in `deploy/theme/` (and the copies in both deployment repos) list them.
- **INSTALL.md walked through end to end** on a fresh local environment (fresh copy of `deploy/`,
  throwaway Keycloak 26, TLS-terminating proxy), following the text literally and covering the
  full editorial loop (publisher login, section, article with uploaded lead image, publish,
  issue, private newspaper with reader login, theme swap, backup commands). Every gap found is
  fixed in `INSTALL.md`. One gap is already known: step 1 does not list `theme/` among the files
  to copy.
- Remove the M7 entry from `ai/open-proposals.md`.

## Non-goals

- No new staging-to-production promotion mechanism. Alex-Presse keeps deploying on push or by
  manual run; upstream does not dispatch to it.
- No logo, wordmark or artwork for Alex-Presse, and no reuse of OÖN brand assets or proprietary
  fonts.
- No changes to reader markup beyond what the stable API already renders. Documenting hooks
  must not rename or restructure existing classes.
- No Mobile/QR work (M8).
- No live changes to the Alex-Presse server beyond what a push to its repo deploys, and that push
  only happens after Gerald approves.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `reader-theme`: the "Stable styling API for forks" requirement covers the issue, issues and print
  views and the masthead, lead-image and issue-list classes.
- `deployment`: the "Installation guide" requirement lists `theme/` among the files to copy and
  requires that the guide, followed on a fresh host, carries an operator through the full
  editorial loop (lead image upload, publish, issue) and not just to the first login.

## Impact

- **Backend / reader**: no behaviour change. A `@QuarkusTest` asserts the newly documented hooks
  on the issue, issues and print views so they cannot silently disappear.
- **Admin**: none.
- **Deploy**: `deploy/INSTALL.md` (fixes from the walkthrough), `deploy/theme/custom.css` and
  `deploy/theme/README.md` (hook list).
- **Deployment repos** (outside this repo, recorded here): `../alexpresse` (theme, fonts, README),
  `../presserl-deployment` (starter comment sync, README note that it is staging and that
  alexpresse is its first fork).
- **Docs / project config**: `docs/architecture.md`, `docs/vision.md`, `openspec/config.yaml`,
  `.claude/CLAUDE.md`, `ai/memory/`, `ai/open-proposals.md`.
- **REST contract**: unchanged, so `ai/primer/endpoints.md` is not touched.
