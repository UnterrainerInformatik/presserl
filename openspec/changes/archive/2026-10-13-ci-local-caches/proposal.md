## Why

On self-hosted runners the GitHub cache service is a detour: the runner containers on babylon5
are persistent and keep `~/.gradle`, `~/.m2` and `~/.npm` locally, yet the workflows uploaded and
downloaded those caches on every run. In presserl's Android job (run 37107150306) `Set up Gradle`
took 27 s and `Post Set up Gradle` 82 s. Separately, `docker-build-workflow` built every image for
`linux/amd64` and `linux/arm64/v8`; only the data logger and its modules run on ARM, and they do
not use this workflow.

## What Changes

- **presserl `pipeline.yml`, Android job:** `gradle/actions/setup-gradle@v5` gets
  `cache-disabled: true`; Gradle uses the runner's own `~/.gradle`.
- **Shared workflow `UnterrainerInformatik/docker-build-workflow`** (outside this repo, `@master`,
  4d83833): new input `platforms`, default `linux/amd64`; QEMU is set up only for other values.
  presserl's image becomes amd64 only.
- **Shared workflows `npm-build-workflow`** (4821ec5, `cache: npm` removed) **and
  `maven-central-workflow`** (9068223, `actions/cache` on `~/.m2/repository` removed) -- recorded
  here, presserl calls neither.
- **babylon5 runners** (`babylon5:~/scripts` a8f04e3): all runner containers share
  `/app/data/github-runner/cache/{m2-repository,npm}` -- recorded here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None -- CI configuration only, no presserl behaviour changes (`skip_specs: true`).

## Impact

- **Platforms:** deploy (CI). Backend, reader, admin, docs not affected.
- **ARM:** staging (babylon5) and alexpresse run on amd64; a `linux/arm64` pull of new presserl
  tags would fail with "no matching manifest". Older tags keep both architectures.
- **Cold caches:** the first Android build on each babylon5 runner after a container recreate
  downloads Gradle dependencies once.

## Non-goals

- No shared `~/.gradle` across runner containers (the org runners move off babylon5 later).
- No local registry proxy (Verdaccio, Reposilite, `registry:2`) -- not worth it on one build host,
  see `~/scripts/github-runner/README.md` on starfury.
