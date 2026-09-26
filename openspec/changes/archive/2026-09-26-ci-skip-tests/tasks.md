## 1. CI

- [x] 1.1 Remove the `test` job from `.github/workflows/pipeline.yml`
- [x] 1.2 Drop `needs: test` from `bump` and change `docker-build` to `needs: bump`
- [x] 1.3 Remove the `pull_request` trigger; keep `push` to `master` (with its `paths-ignore`) and `workflow_dispatch`

## 2. Contract/Docs

- [x] 2.1 Extend the CI/CD row in `docs/architecture.md`: CI builds and ships images only; backend and admin tests run locally before push

## 3. Verification

- [x] 3.1 Validate the workflow YAML syntax and check that every remaining `needs` references an existing job
- [x] 3.2 Run the backend (`./mvnw verify`) and admin (`./gradlew check`) suites locally before committing
- [ ] 3.3 After the push, confirm the pipeline run goes bump → docker-build → dispatch-staging without a test job
