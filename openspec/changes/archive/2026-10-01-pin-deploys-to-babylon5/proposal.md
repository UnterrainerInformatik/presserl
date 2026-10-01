## Why

The homepage deploy of 2026-10-01 (`UnterrainerInformatik/homepage`, run 36850891421) failed
twice in a row at the first SSH step with `ssh: connect to host *** port ***: Connection timed
out`, once on `dev1-runner1` and once on `dev1-runner2` after a re-run. The deploy of the same
morning ran on `babylon5-runner3` and succeeded. The run passes no VPN secret that reaches the job
(both VPN steps skipped), so the deploy connects directly — which works from babylon5 and not from
dev1. Build and image were green; the page `/app/presserl/account-deletion` (account-deletion-request,
task 7.4, needed for Google Play's Data safety form) is therefore not live.

The shared `UnterrainerInformatik/deploy-workflow` hard-codes `runs-on: [self-hosted, Linux, X64]`,
so every caller lands on babylon5 or dev1 at random and cannot choose. `docker-build-workflow`
already takes a `runs-on` input for the same reason (ci-build-speed).

The public fork `guFalcon/alexpresse` deploys through the same shared workflow. Its only runner
today is the repo runner `babylon5-runner11-priv-ALEXPRESSE`, so it happens to run on babylon5;
Gerald wants that pinned explicitly so a future runner elsewhere cannot take its deploys.

## What Changes

- **Shared workflow `UnterrainerInformatik/deploy-workflow`** (outside this repo): new optional
  input `runs-on` (JSON array of runner labels, default `["self-hosted","Linux","X64"]`, same
  shape and wording as `docker-build-workflow`); the deploy job uses
  `runs-on: ${{ fromJSON(inputs.runs-on) }}`. Callers that do not pass it behave as today.
- **Homepage `UnterrainerInformatik/homepage`** (`../../JAVASCRIPT/homepage`): the `deploy` job of
  `.github/workflows/pipeline.yml` passes `runs-on: '["self-hosted","Linux","X64","babylon5"]'`.
  Build and docker-build stay unpinned.
- **Fork `guFalcon/alexpresse`** (`../alexpresse`): the `deploy` job of
  `.github/workflows/deploy.yml` passes the same `runs-on`.
- **babylon5 runner `ghar-priv-alexpresse`** (`babylon5:~/scripts/github-runner/docker-compose.yml`):
  its `LABELS` gain `babylon5`, and only this container is recreated so the runner registers with
  the label — before the alexpresse workflow change is pushed, otherwise its deploys would queue
  forever. The compose file is edited in place and not committed (Gerald keeps uncommitted edits
  there).
- **Memory** `ai/memory/reference_ci_runners.md`: `deploy-workflow` takes `runs-on`; homepage and
  alexpresse deploy on `babylon5`; the alexpresse runner carries the label; dev1 cannot reach the
  homepage deploy host.
- **account-deletion-request**: once the homepage deploy is green, task 7.4 of the archived change
  is ticked off.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — CI/runner configuration only, no product behaviour changes (`skip_specs: true`).

## Impact

- **Platforms:** deploy (CI of the homepage and the alexpresse fork, shared deploy workflow,
  runner host babylon5). Backend, reader, admin and docs are not affected.
- **External repositories:** `UnterrainerInformatik/deploy-workflow`, `UnterrainerInformatik/homepage`,
  `guFalcon/alexpresse`; pushing homepage and alexpresse redeploys those sites (same image as now
  for alexpresse; the pending account-deletion page for the homepage).
- **Other callers of `deploy-workflow`** (`presserl-deployment`, `js-cms-gui`, `js-cms-gui-v2`,
  `overmind-gui`): unchanged behaviour through the default.
- **Availability:** homepage and alexpresse deploys queue while babylon5 is down or busy; the
  fallback is dropping the input.

## Non-goals

- Staging `presserl-deployment` stays unpinned: its deploys from dev1 succeed (e.g. 2026-09-30 on
  `dev1-runner2`/`dev1-runner3`).
- No pinning of the other `deploy-workflow` callers.
- No fix of dev1's network path to the homepage host (VPN secrets, routing); pinning avoids it.
- No change to the homepage build or docker-build jobs, nor to the other babylon5 runners.
- No commit in babylon5's `scripts` repository.
