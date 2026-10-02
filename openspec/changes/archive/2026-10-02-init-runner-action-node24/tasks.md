## 1. Deploy – shared action (`UnterrainerInformatik/init-runner-action`)

- [x] 1.1 Clone `UnterrainerInformatik/init-runner-action` to `../init-runner-action` if not present and check out `master`. Verify with `git -C ../init-runner-action log -1` showing `5e56f6a` (or newer, then re-read `action.yml`).
- [x] 1.2 In `action.yml`: `Checkout repo 📦` → `actions/checkout@v7`; pre-fetch list → `checkout@v7`, `upload-artifact@v7`, `download-artifact@v8`, `setup-node@v7`, `setup-java@v6`, `cache@v6` (design D1, D2). Verify with a YAML parse (`python3 -c 'import yaml,sys;yaml.safe_load(open(sys.argv[1]))' action.yml`), `git diff` showing only version strings, and `git ls-remote --tags https://github.com/actions/<name> <major>` returning a ref for each of the six entries.
- [x] 1.3 Commit and push to `master`. Verify with `git -C ../init-runner-action status -sb` clean and in sync with `origin/master`.

## 2. Contract/Docs

- [x] 2.1 Update `ai/memory/reference_ci_runners.md`: `init-runner-action` checks out with `actions/checkout@v7` (Node 24, runners ≥ 2.327.1); pre-fetch list on current majors. Verify by reading the file.
- [x] 2.2 In `ai/open-proposals.md`: delete the entry "init-runner-action on Node 24"; add an entry listing the callers that still pin Node 20 majors themselves (`npm-build-workflow`: `setup-node@v4`, `upload-artifact@v4`; `maven-central-workflow`: `cache@v3`, `setup-java@v4`, `upload-artifact@v4`; pipeline-level cache steps). Verify by reading the file.

## 3. Verification

- [x] 3.1 `gh workflow run deploy.yml -R UnterrainerInformatik/presserl-deployment` (deploy-workflow path, no `self_hosted`). Verify the run is green, the `Init Runner` step's log resolves `actions/checkout@v7`, and the run's annotations (`gh api repos/UnterrainerInformatik/presserl-deployment/check-runs/<job id>/annotations`) contain no Node 20 deprecation for `actions/checkout`.
- [ ] 3.2 Run a pipeline whose `bump-semver-workflow` job uses `self_hosted: 'true'` (`gh workflow run pipeline.yml -R UnterrainerInformatik/homepage`, or this change's own presserl push if it comes first). Verify the bump job is green, its pre-fetch log lists the six new refs as fetched/available, and the version tag push succeeded (new tag visible via `git ls-remote --tags`). On failure in 3.1/3.2: revert in `../init-runner-action`, push, and report to Gerald.

> Note (2026-10-02): 3.1 green (presserl-deployment run 37006497870). 3.2 partially verified via
> homepage run 37007325263 (self_hosted pre-fetch cloned all six refs, checkout@v7, all green), but
> `github-tag-action` skipped ("No new commits since previous tag"), so the tag push is unproven.
> Decision: verify 3.2 on presserl's own pipeline after the archive commit is pushed (its bump job
> must push a new tag); tick 3.2 only then, revert `init-runner-action` on failure.
