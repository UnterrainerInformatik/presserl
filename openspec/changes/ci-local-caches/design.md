## Context

The Android job runs on `[self-hosted, Linux, X64, babylon5]`, i.e. one of `babylon5-runner1..3`,
persistent `myoung34/github-runner` containers whose home directory survives between jobs.
`setup-gradle` restores `~/.gradle` from the GitHub cache at the start and saves it in its post
step.

## Decisions

### D1 -- `cache-disabled: true` rather than `cache-read-only`

Read-only would still download the cache on every run. The runner-local `~/.gradle` is warmer than
any GitHub cache entry, so neither restore nor save is needed.

### D2 -- amd64 by default in `docker-build-workflow`

ARM is opt-in per caller (`platforms: linux/amd64,linux/arm64/v8`). The data logger keeps its own
multi-arch build in its own pipeline.

## Risks / Trade-offs

- A recreated runner container starts with an empty `~/.gradle`; one slower Android build per
  runner, then warm again.
