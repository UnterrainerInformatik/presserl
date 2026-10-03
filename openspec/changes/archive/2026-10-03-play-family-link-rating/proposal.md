## Why

The IARC questionnaire gave the Android admin app the PEGI rating **"Parental guidance"** (PG), not
an age number, because the app carries user-generated content. On a child's Google account supervised
by Google Family Link, any app age filter ("up to PEGI 3/7/12/16/18") blocks PG apps. The block is
silent: parental approval sends no request, and Google Play shows a misleading "enable Wi-Fi or
mobile data" error. On 3 Oct 2026 this stopped the first install on a supervised account in Austria.
School and family newspapers — the product's core audience — will mostly hand the app to children
on supervised accounts, so without a clear explanation nearly every such installation fails, and
nobody can tell why.

## What Changes

- `docs/play-console.md`, section "Content rating": record the ratings issued on 1 Oct 2026, explain
  why PEGI assigns PG and why the user-generated-content answer must stay "yes", describe the Family
  Link consequence, and keep what was observed (3 Oct 2026) apart from what is still open (whether
  supervised accounts can install from test tracks at all). Add a troubleshooting checklist for the
  "enable Wi-Fi or mobile data" / "Item not found" symptoms, and link the new page.
- New user-facing page `docs/admin-app.md` ("The admin app on children's phones"): written for
  newspaper operators, parents and teachers, not developers. It covers the symptoms, the cause in plain
  words, the exact Family Link setting that lets the app through, the trade-off of that setting
  (it applies to every app on that phone), and the alternatives.
- `deploy/INSTALL.md`: a short pointer to `docs/admin-app.md` for operators who give the app to
  children.
- `ai/open-proposals.md`, entry "M8 — Play production release": state the PG block in the store
  description (within the `check.sh` limits) and note that supervised accounts probably cannot serve
  as the 12 closed-test testers.

## Non-goals

- Changing the content-rating answers to obtain a lower PEGI rating. The user-generated-content
  answer is true, and a false declaration risks enforcement by Google.
- Changing the store listing texts now. That belongs to the production release (M8), and a later
  change does it.
- App or backend code changes, e.g. an in-app hint. The app never gets installed in this situation,
  so an in-app hint would not be seen.
- A sideloading guide (signed universal APK). It is mentioned only as a last resort for developers in
  `docs/play-console.md`, not recommended to operators.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `android-release`: adds a requirement that the content rating's effect on supervised (Family
  Link) accounts is documented for developers and explained to operators on a user-facing page.

## Impact

- Platforms: **docs** and **deploy** (`deploy/INSTALL.md` pointer) only. Backend, reader and admin
  code are untouched, and nothing changes in the deployment repositories.
- Files: `docs/play-console.md`, `docs/admin-app.md` (new), `deploy/INSTALL.md`,
  `ai/open-proposals.md`.
- Play Console: no change. The questionnaire answers stay as submitted.
