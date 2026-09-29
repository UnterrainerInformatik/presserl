## Context

- presserl's `pipeline.yml` calls `UnterrainerInformatik/docker-build-workflow/.github/workflows/workflow.yml@master`
  (`bump` → `docker-build` → `dispatch-staging`). The shared workflow is also used by homepage,
  js-cms-gui, js-cms-gui-v2 and overmind-gui, and is referenced at `@master`: a push there is live
  for every caller at once.
- The job runs on `[self-hosted, Linux, X64]`, matched by `ghar-runner1..3` on both babylon5 and
  dev1. Runners are `myoung34/github-runner` containers that mount the host's
  `/var/run/docker.sock`; buildx builder containers therefore run on the host daemon, and the
  runner containers themselves are recreated from time to time (their `~/.docker` is not durable).
- babylon5 docker: 29.1.3, storage driver `overlay2` (no containerd image store), buildx 0.33.0;
  `/` has 97 GB free (89 % used).
- `setup-buildx-action@v3` without `name` creates a random `builder-<uuid>` and removes it in its
  post step; leaked ones (`buildx_buildkit_builder-*`, weeks old) exist on babylon5.
- The Dockerfile already builds stages 1–2 on `$BUILDPLATFORM` and uses `RUN --mount=type=cache`
  for `~/.gradle/caches` and `~/.m2`; stage 3 is the only per-architecture stage.

## Goals / Non-Goals

**Goals:**
- presserl image builds always run on babylon5.
- Layers and cache mounts survive between presserl builds; an unchanged `admin/` skips the Gradle
  and Brotli steps entirely.
- Callers that do not opt in see no behaviour change.

**Non-Goals:**
- Sharing one cache between presserl and the other callers.
- Making the cache survive a builder removal (it is a cache; losing it only costs one cold build).

## Decisions

### D1 — Runner selection by an optional `runs-on` input

The shared workflow gets `runs-on` (type `string`, JSON array, default
`'["self-hosted","Linux","X64"]'`) and the job uses `runs-on: ${{ fromJSON(inputs.runs-on) }}`.
presserl passes `'["self-hosted","Linux","X64","babylon5"]'`.

*Alternatives:* a boolean `fast-runner` input — hides the label in the shared workflow and is
useless for other hosts; a dedicated runner group — needs org admin setup for one label's worth
of effect; hard-coding `babylon5` in the shared workflow — forces every caller onto one host.

### D2 — Persistent named buildx builder (`docker-container` driver) with a GC cap

New optional input `builder-name` (default `''`). When set, `setup-buildx-action` gets
`name: <builder-name>`, `keep-state: true` and `cleanup: false`, plus an inline BuildKit config
that enables garbage collection with a hard cap (≈ 15 GB used space, and a minimum free-space
guard so the cache yields first when the disk runs low; exact keys checked against the BuildKit
version the builder runs). presserl passes `builder-name: presserl`.

The builder's node container is named `buildx_buildkit_presserl0` on the host daemon, with its
state in the volume `buildx_buildkit_presserl0_state`. Each runner container knows builders only
from its own `~/.docker/buildx`; a runner that has never seen `presserl` creates the builder
entry again, and the docker-container driver attaches to the existing node container instead of
starting a second one. So all three babylon5 runners share one BuildKit instance and one cache,
and concurrent presserl builds are handled by BuildKit itself. The inline config applies only when
the node container is first created; changing the cap later means `docker buildx rm presserl`
(or removing the container) once — a documented one-off, acceptable for a cache.

*Alternatives:*
- `driver: docker` (the daemon's built-in BuildKit): persistent by nature, but multi-platform
  output needs the containerd image store, which babylon5 does not use; switching the daemon's
  storage affects every container on the host.
- `cache-to/cache-from type=local`: exports layers only, not `RUN --mount=type=cache` content (so
  Gradle/Maven downloads would stay cold on every admin/backend change), needs a rotation step to
  keep the directory from growing, and each runner has its own directory.
- `type=registry` on Docker Hub: independent of the host, but pushes a large `mode=max` cache over
  the home uplink on every build and still loses cache mounts.
- `type=gha`: uploads to GitHub from a self-hosted runner, 10 GB repo limit, same cache-mount gap.

### D3 — Parallel Brotli in the Dockerfile

Replace `find … -exec brotli --best --keep {} +` with `find … -print0 | xargs -0 -P "$(nproc)" -n 1
brotli --best --keep`. Same quality (11), same files, same output bytes; the bundle has a handful
of large files, so wall time drops to roughly that of the largest one.

The Brotli step moves from the backend stage into the admin stage, right after the Gradle build
(`brotli` joins `libatomic1` in that stage's `apt-get`). In the backend stage it ran after
`COPY backend/`, so every backend-only change re-ran it despite a warm cache; in the admin stage it
depends on `admin/` only, and the `.br` files arrive in the backend stage with the bundle's `COPY`.

*Alternative:* lower quality (`-q 10`) — faster but a few percent more bytes for every reader; the
admin-startup-speed change chose `--best` deliberately.

### D4 — Node 24 action majors

Raise `setup-qemu-action`, `setup-buildx-action`, `login-action`, `build-push-action` and
`download-artifact` to their current majors that run on Node 24 (checked at apply time). If the
new `build-push-action` major records build summaries/uploads build records by default, disable
that (`DOCKER_BUILD_SUMMARY`/`DOCKER_BUILD_RECORD_UPLOAD` = `false`) to keep today's job output
and avoid extra artifacts. Inputs used today (`context`, `file`, `platforms`, `push`, `tags`,
`cache-image`) must keep their meaning.

### D5 — Order of rollout

1. Push the shared workflow change (defaults unchanged) and confirm with one run of another
   caller (`workflow_dispatch` or its next push) that it still builds on the old path.
2. Push presserl's `pipeline.yml`/`Dockerfile` change: first run is cold on babylon5 (creates the
   builder), then a backend-only change measures the warm path.

## Risks / Trade-offs

- [All three babylon5 runners busy or babylon5 down → presserl builds queue instead of using
  dev1] → Accepted: builds are rare; a queued build on babylon5 still finishes before a dev1
  build would. If babylon5 is down for long, drop the label in `pipeline.yml`.
- [Cache fills babylon5's already tight disk] → GC cap in the builder config (D2); the cap and the
  location of the state volume are recorded in `ai/memory/reference_ci_runners.md`.
- [Stale or corrupted cache produces a wrong image] → BuildKit keys layers by content; a manual
  `docker buildx rm presserl --keep-state=false` on babylon5 resets it. Documented in memory.
- [Two runners create the builder at the same moment on the very first run] → the second
  `docker run` of the node container fails once; rerun the job. Avoided in practice by the first
  (cold) run in D5 happening alone.
- [Another runner container's buildx client does not attach to the existing node container but
  errors] → verified during apply by running the warm build on a different babylon5 runner than
  the cold one; fallback: pre-create the builder entry per runner in `init-runner-action` or pin
  the endpoint.
- [Shared workflow at `@master` breaks other callers] → inputs are optional with today's values as
  defaults; D5 step 1 checks one other caller before presserl opts in.
- [Action major bumps change behaviour] → D4 limits them to majors whose used inputs are unchanged;
  roll back to the previous majors in the shared workflow if the first run fails.

## Migration Plan

Follow D5. Rollback: in presserl remove `runs-on`/`builder-name` from `pipeline.yml` (back to the
random runner and a fresh builder); in the shared workflow the inputs can stay. To discard the
cache: `docker buildx rm presserl` on babylon5 (or `docker rm -f buildx_buildkit_presserl0 &&
docker volume rm buildx_buildkit_presserl0_state`).

## Open Questions

- Exact GC cap value (≈ 15 GB assumed) — tune after seeing the cache size of a few builds.
