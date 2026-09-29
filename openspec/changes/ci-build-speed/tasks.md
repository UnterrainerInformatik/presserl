## 1. Shared workflow (`UnterrainerInformatik/docker-build-workflow`)

- [x] 1.1 Clone `UnterrainerInformatik/docker-build-workflow` next to this repo (`../docker-build-workflow`) if not present and pull `master`. Verify with `git -C ../docker-build-workflow log -1`.
- [x] 1.2 Add the optional input `runs-on` (string, default `'["self-hosted","Linux","X64"]'`) and use `runs-on: ${{ fromJSON(inputs.runs-on) }}` (design D1). Verify with `actionlint` (via its Docker image) on the workflow file.
- [x] 1.3 Add the optional input `builder-name` (string, default `''`); when set, pass `name`, `keep-state: true`, `cleanup: false` and an inline BuildKit config with GC enabled and a ≈ 15 GB cap plus a free-space guard to `setup-buildx-action`; when empty, the step behaves as today (design D2). Check the GC key names against the BuildKit version the builder image ships. Verify with `actionlint` and by reading the rendered step for both cases.
- [x] 1.4 Raise `setup-qemu-action`, `setup-buildx-action`, `login-action`, `build-push-action` and `download-artifact` to their current Node 24 majors; check each changelog that the inputs used stay compatible and disable build summary / build record upload if the new `build-push-action` adds them (design D4). Verify with `actionlint`.
- [x] 1.5 Show Gerald the diff and ask before pushing (the workflow is live at `@master` for every caller). After the push, run one other caller's image build (e.g. `workflow_dispatch` or its next push) and confirm it succeeds without the new inputs and without Node 20 warnings (`gh run view`).
  - Result: js-cms-gui run 36577436253 green (fresh `builder-<uuid>`, artifact download OK). One Node 20 annotation remains for `actions/checkout@v4`, which comes from `UnterrainerInformatik/init-runner-action` (outside D4) and is noted in `ai/open-proposals.md`.

## 2. presserl

- [x] 2.1 In `.github/workflows/pipeline.yml`, pass `runs-on: '["self-hosted","Linux","X64","babylon5"]'` and `builder-name: presserl` to the `docker-build` job. Verify with `actionlint`.
- [x] 2.2 In the `Dockerfile`, run Brotli in parallel: `find … -print0 | xargs -0 -P "$(nproc)" -n 1 brotli --best --keep`, and move the step into the admin stage after the Gradle build so a backend-only change does not invalidate it (design D3). Verify with `docker build -t presserl:local .` that the image still contains a `.br` next to every `.wasm`/`.js` under `/deployments/…/admin/` and that the build step is faster than before (compare the step time).
- [x] 2.3 Run `./mvnw test -Dtest=AdminDeliveryTest,PrecompressedAdminBundleTest` in `backend/` to confirm the admin bundle delivery is untouched. Verify that the tests pass.

## 3. Rollout and measurement

- [ ] 3.1 Commit and, after asking Gerald, push presserl `master`. Watch the pipeline (`gh run watch`): the `docker-build` job runs on a `babylon5-runner*`, creates `buildx_buildkit_presserl0` on babylon5 and leaves it (plus its state volume) after the job. Record the cold build time. Verify with `gh run view --log` (runner name, step times) and `ssh babylon5 docker ps`.
- [ ] 3.2 Trigger a second run without admin changes (`gh workflow run PIPELINE` or the next backend-only commit), ideally picked up by a different babylon5 runner than 3.1. Verify in the log that the admin Gradle and Brotli steps are `CACHED`, the job attaches to the existing builder instead of creating a second node container, and record the warm build time.
- [ ] 3.3 Check the cache size on babylon5 (`docker buildx du --builder presserl`, `docker system df`) and confirm it stays below the cap. Adjust the cap in 1.3 if the first builds show a clearly different need (requires `docker buildx rm presserl` once).
- [ ] 3.4 Confirm staging redeployed the new image (the `dispatch-staging` job ran and `presserl-deployment`'s DEPLOY run succeeded). Verify with `gh run list -R UnterrainerInformatik/presserl-deployment -L 1`.

## 4. Memory

- [ ] 4.1 Update `ai/memory/reference_ci_runners.md` with the outcome: presserl pinned to `babylon5`, builder name, state volume, cap, reset command, and cold/warm build times from 3.1/3.2. Also update the leaked-builder note if the new setup changes it. Verify by reading the file.
