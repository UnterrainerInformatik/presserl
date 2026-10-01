## 1. Deploy – shared workflow (`UnterrainerInformatik/deploy-workflow`)

- [x] 1.1 Clone `UnterrainerInformatik/deploy-workflow` to `../deploy-workflow` if not present and check out `master`. Verify with `git -C ../deploy-workflow log -1`.
- [x] 1.2 Add the optional `runs-on` input and `runs-on: ${{ fromJSON(inputs.runs-on) }}` on the `deploy` job (design D1). Verify with `actionlint` (Docker image) on the workflow file.
- [x] 1.3 Commit and push to `master`. Verify with `git -C ../deploy-workflow status` clean and `git log origin/master -1`.

## 2. Deploy – alexpresse runner on babylon5

- [x] 2.1 Back up `babylon5:~/scripts/github-runner/docker-compose.yml` into the session scratchpad; add `babylon5` to `LABELS` of service `worker11-gufalcon` only (design D3). Verify with `ssh babylon5 'cd ~/scripts/github-runner && git diff docker-compose.yml'` showing that one line on top of Gerald's own edits.
- [x] 2.2 Check the runner is idle (`gh api repos/guFalcon/alexpresse/actions/runners` → `busy: false`), then `docker compose up -d --no-deps --force-recreate worker11-gufalcon`. Verify the runner is `online` with labels including `babylon5` in the same API call.

## 3. Deploy – callers

- [x] 3.1 `../alexpresse/.github/workflows/deploy.yml`: `deploy` job passes `runs-on: '["self-hosted","Linux","X64","babylon5"]'`. Verify with `actionlint`.
- [x] 3.2 `../../JAVASCRIPT/homepage/.github/workflows/pipeline.yml`: `deploy` job passes the same `runs-on`; build and docker-build unchanged. Verify with `actionlint`.

## 4. Contract/Docs

- [x] 4.1 Update `ai/memory/reference_ci_runners.md`: `deploy-workflow` takes `runs-on`; homepage and alexpresse deploy only on `babylon5`; `ghar-priv-alexpresse` carries the `babylon5` label (compose `LABELS`); dev1 cannot reach the homepage deploy host. Verify by reading the file.

## 5. Verification

- [x] 5.1 Commit and push alexpresse (after 2.2); the push runs `DEPLOY`. Verify with `gh run list -R guFalcon/alexpresse -L 1` green and the deploy job's `runner_name` `babylon5-runner11-priv-ALEXPRESSE` (jobs API); `https://alexpresse.net/` answers `200`.
- [x] 5.2 Commit and push the homepage; the push runs `PIPELINE`. Verify the run is green and the deploy job's `runner_name` starts with `babylon5-runner` (jobs API).
- [x] 5.3 The live homepage bundle contains the route: `curl -s https://unterrainer.info/` → `app.*.js` contains `presserl/account-deletion`; the page shows its German and English text in a headless browser.
- [x] 5.4 Tick task 7.4 in `openspec/changes/archive/2026-10-01-account-deletion-request/tasks.md` (staging part already checked on 2026-10-01 via the public IP). Verify by reading the file.
- [x] 5.5 The next `presserl-deployment` deploy (dispatched by any presserl image build, or `gh workflow run` there) still succeeds without the input (default path). Verify with `gh run list -R UnterrainerInformatik/presserl-deployment -L 1`.
