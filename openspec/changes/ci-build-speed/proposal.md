## Why

A push to `master` takes 7 to 17 minutes until the new image is on Docker Hub, almost all of it in
the image build. Two causes, measured on 2026-09-29 (details in `ai/memory/reference_ci_runners.md`):

- The build job lands at random on one of two self-hosted runner sets. `dev1` (Xeon E5-2620 VM) is
  3–4× slower in CPU and ~30× slower on disk than `babylon5` (Ryzen 5 5600, bare metal): the same
  build took 15:30 on dev1 and 6:33 on babylon5.
- Nothing is reused between runs. The shared `docker-build-workflow` creates a fresh buildx builder
  per job and removes it afterwards, so every run pulls the base images, runs `apt-get`, downloads
  all Gradle and Maven dependencies and rebuilds the admin Wasm bundle (the Binaryen optimisation
  alone takes 0:41–3:07) — even when only backend code changed.

Staging and the public fork get every fix that much later, and the dev1 runners are blocked for
a quarter of an hour per build.

## What Changes

- **Shared workflow `UnterrainerInformatik/docker-build-workflow`** (outside this repo, used by
  presserl, homepage, js-cms-gui, js-cms-gui-v2, overmind-gui) gets two optional inputs; callers
  that do not set them behave exactly as today:
  - `runs-on` — JSON array of runner labels, default `["self-hosted","Linux","X64"]`.
  - `builder-name` — name of a persistent buildx builder. When set, the builder is reused across
    runs (layers and `RUN --mount=type=cache` directories survive) and not removed after the job;
    its BuildKit garbage collection is capped so the cache cannot fill the host's disk.
- **presserl `pipeline.yml`** passes `runs-on: ["self-hosted","Linux","X64","babylon5"]` and a
  builder name, so the image build always runs on babylon5 with a warm cache.
- **presserl `Dockerfile`**: the Brotli pre-compression of the admin bundle runs its files in
  parallel instead of one after the other (same quality, same output).
- The actions in the shared workflow are raised to majors that run on Node 24, removing the Node 20
  deprecation warnings.

Expected: backend-only pushes ≈ 2–3 min build (admin stage from cache), admin changes ≈ 5 min.

## Non-goals

- No change to the image content, tags, platforms (`linux/amd64`, `linux/arm64/v8`) or the
  staging dispatch.
- No registry cache (`type=registry` on Docker Hub) and no GitHub Actions cache: the cache stays on
  babylon5's local disk.
- No cleanup of babylon5 beyond the new builder's own cap (full swap, 89 % disk, leaked
  `buildx_buildkit_builder-*` containers and ~41 GB of reclaimable volumes are noted for Gerald,
  not touched).
- No retirement or relabelling of the dev1 runners; other projects keep using both sets.
- No faster Kotlin/Wasm compilation (Binaryen stays on; the bundle size matters to readers).
- The `bump` job and the alexpresse/staging deploy jobs keep their runners.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none: CI tooling only, no product behaviour changes (skip_specs: true) -->

## Impact

- **Platforms:** CI only. Backend, reader, admin, deploy templates and docs are unaffected at
  runtime; the image is byte-for-byte equivalent apart from build timestamps.
- **Repos:** `UnterrainerInformatik/docker-build-workflow` (`.github/workflows/workflow.yml`),
  presserl `.github/workflows/pipeline.yml` and `Dockerfile`.
- **Infrastructure:** babylon5 runners `babylon5-runner1..3` carry the label `babylon5` (already
  set in `babylon5:/home/psilo/scripts/github-runner/docker-compose.yml`). A persistent builder
  container plus its state volume live on babylon5's docker daemon (capped size).
- **Risk:** if all three babylon5 runners are busy, presserl builds wait instead of falling back to
  dev1. If babylon5 is down, presserl cannot build until it is back (or the label is dropped).
- **Memory:** `ai/memory/reference_ci_runners.md` updated with the outcome.
