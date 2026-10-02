## Why

`UnterrainerInformatik/init-runner-action` is the first step of every shared workflow
(`bump-semver-workflow`, `npm-build-workflow`, `docker-build-workflow`, `maven-central-workflow`,
`deploy-workflow`) and of several pipelines (`cms-keycloak`, `htl-zeromq`, `java-cms-data-logger`,
`java-elite-server`, `java-overmind-server`). It checks out with `actions/checkout@v4`, a Node 20
action that GitHub now runs on Node 24 with a deprecation annotation on every job — including
every presserl build and deploy. Its optional pre-fetch list (`self_hosted: 'true'`) still clones
v4/v3 majors that no current caller of the newer workflows uses.

## What Changes

- **Shared action `UnterrainerInformatik/init-runner-action`** (outside this repo, `action.yml`):
  - `Checkout repo 📦` uses `actions/checkout@v7` (Node 24; `fetch-depth: 0` unchanged).
  - The pre-fetch list moves to the current majors: `actions/checkout@v7`,
    `actions/upload-artifact@v7`, `actions/download-artifact@v8`, `actions/setup-node@v7`,
    `actions/setup-java@v6`, `actions/cache@v6`.
  - Inputs, step order and the `self_hosted` behaviour stay as they are.
- **Memory** `ai/memory/reference_ci_runners.md`: `init-runner-action` checks out with v7; runners
  need ≥ 2.327.1 (they run 2.337.0).
- **Backlog** `ai/open-proposals.md`: the entry "init-runner-action on Node 24" is deleted. A new
  entry records the callers that still pin Node 20 majors themselves (see Non-goals).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — CI tooling only, no product behaviour changes (`skip_specs: true`).

## Impact

- **Platforms:** deploy/CI of all repositories using the action (presserl image build and deploys
  included). Backend, reader, admin and docs are not affected.
- **External repositories:** `UnterrainerInformatik/init-runner-action`; it is referenced as
  `@master`, so the push is live for every caller at once.
- **Behaviour differences of checkout v5–v7:** Node 24 runtime (runner ≥ 2.327.1); v6 persists
  the git credentials in a file under `$RUNNER_TEMP` instead of `.git/config` (git push/fetch keep
  working — relevant for `bump-semver-workflow`, which pushes); v7 refuses fork PR checkouts under
  `pull_request_target`/`workflow_run` (no caller uses those triggers).

## Non-goals

- Raising the actions the callers pin themselves (`npm-build-workflow`: `setup-node@v4`,
  `upload-artifact@v4`; `maven-central-workflow`: `cache@v3`, `setup-java@v4`,
  `upload-artifact@v4`; pipelines' own cache steps). They emit the same annotation but are separate
  repositories with their own verification — recorded as a backlog entry.
- Removing the `self_hosted` input or the pre-fetch mechanism.
- Tagging/versioning the action instead of `@master`.
