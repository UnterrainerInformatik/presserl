## Why

The GitHub pipeline runs the full backend (`./mvnw verify`, Dev Services) and admin (`./gradlew check`, Karma with headless Chrome) suites before every image build. That takes far too long for a pipeline whose only consumer is the staging deployment. Tests are run locally before committing and pushing instead.

## What Changes

- Remove the `test` job from `.github/workflows/pipeline.yml`.
- `bump` no longer depends on `test`; `docker-build` depends on `bump` only.
- Remove the `pull_request` trigger: with the test job gone, every remaining job is skipped for pull requests, so the trigger would only produce empty runs.
- Note in the CI/CD row of `docs/architecture.md` that CI does not run tests and that both suites run locally before a push.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. No spec covers the CI pipeline; the change sets `skip_specs: true`.

## Non-goals

- Changing the tests themselves or making them faster.
- A separate, manually triggered test workflow.
- Changing bump, image build or the staging dispatch.

## Impact

- Platforms: CI (`.github/workflows/pipeline.yml`) and docs (`docs/architecture.md`). Backend, reader, admin and deploy code are untouched.
- A push to `master` that breaks tests now reaches staging; the safeguard is the local test run before push (recorded in project memory).
- Pull requests no longer get a CI run.
