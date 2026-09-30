## Why

The presserl image build of 2026-09-30 (run 36754878179, babylon5-runner3) failed with
`Multi-platform build is not supported for the docker driver`. The persistent builder `presserl`
introduced by `ci-build-speed` existed and was running, but the build did not use it:

- `docker-build-workflow` never tells `build-push-action` which builder to use; the action takes
  the runner's *current* buildx builder.
- `setup-buildx-action` only makes a builder current when it creates it (`buildx create --use`).
  For an existing builder it logs `Builder … already exists, skipping creation` and leaves the
  current pointer alone.
- Other repos' jobs on the same runners create an ephemeral `builder-<uuid>` with `--use` and
  remove it afterwards. The pointer stays on the deleted builder and buildx falls back to
  `default` (docker driver), which cannot build `linux/amd64` + `linux/arm64/v8`.

Evidence: `~/.docker/buildx/current` in all three babylon5 runner containers points to a deleted
`builder-<uuid>`, while `~/.docker/buildx/instances` contains `presserl`. The earlier green runs
only worked because the builder had just been created on that runner, or no foreign job had run
there in between. As things stand, every presserl push fails on every babylon5 runner.

## What Changes

- **Shared workflow `UnterrainerInformatik/docker-build-workflow`** (outside this repo): the
  `Build and push` step passes the builder explicitly (`builder: ${{ inputs.builder-name }}`).
  With `builder-name` set, the build always runs on the persistent builder regardless of the
  runner's current pointer. Callers without `builder-name` pass an empty value and behave exactly
  as today (the ephemeral builder set up with `--use` directly before stays current).
- **presserl**: no file change; the pipeline is re-run to confirm the fix and ship the pending
  image `0.0.35` (android-app-qr-login) to staging.
- **Memory**: `ai/memory/reference_ci_runners.md` records the pitfall (current-builder pointer is
  per runner container and shared with other repos' jobs) and the fix.

## Non-goals

- No cleanup of the stale `current` pointers or leaked `buildx_buildkit_builder-*` containers on
  babylon5; with the explicit builder they no longer matter for presserl.
- No change to the persistent builder's name, GC cap, runner label, platforms or image tags.
- No change to how other callers' ephemeral builders are created or removed.
- The `Artifact not found for name: build-artifacts` message in the same job is out of scope; it
  appears before the build and does not stop the job.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none: CI tooling only, no product behaviour changes (skip_specs: true) -->

## Impact

- **Platforms:** CI only. Backend, reader, admin, deploy templates and docs are unaffected; the
  image content is unchanged.
- **Repos:** `UnterrainerInformatik/docker-build-workflow` (`.github/workflows/workflow.yml`),
  consumed at `@master` by presserl, homepage, js-cms-gui, js-cms-gui-v2 and overmind-gui.
- **Infrastructure:** none; uses the existing `presserl` builder on babylon5.
- **Risk:** low. The new input is empty for every caller that does not set `builder-name`.
- **Memory:** `ai/memory/reference_ci_runners.md`.
