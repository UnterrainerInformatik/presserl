## 1. Deploy – setup

- [x] 1.1 Clone `UnterrainerInformatik/npm-build-workflow` to `/home/psilo/source/oss/java/npm-build-workflow` (next to presserl) if not present and check out `master`. Verify with `git -C ../npm-build-workflow log -1` showing `b0e827c` or newer.
- [x] 1.2 Fresh clones of `origin/master` of homepage, overmind-gui, js-cms-gui, js-cms-gui-v2 and `guFalcon/flowers-frontend` into the session scratchpad (design D4; Gerald's working copies stay untouched). Verify each clone's `HEAD` equals `gh api repos/<repo>/commits/master --jq .sha`.
  - Result: all five HEADs match their remote. flowers-frontend's default and pipeline branch is `main` (not `master`); cloned `main` @ `293d025`.

## 2. Deploy – verify and fix callers (design D4, D2)

- [x] 2.1 For each clone, in `docker run --rm node:<.nvmrc>`: `npm version --no-git-tag-version 9.9.9 && npm ci && npm run build`. Verify exit code 0 and a non-empty `dist/`; record per caller pass/fail and npm version in this task.
  - homepage (Node 14.21.3, npm 6.14.18): **fail** at `npm ci` — `bindings not accessible from watchpack-chokidar2:fsevents` (npm 6 with the npm ≥7 v2 lockfile, which lacks the macOS-only `fsevents@1` deps in its v1 section).
  - overmind-gui (Node 14.21.3, npm 6.14.18): pass, `dist/` 17 entries.
  - js-cms-gui (Node 20.20.2, npm 10.8.2): pass, `dist/` 13 entries.
  - js-cms-gui-v2 (Node 24.20.0, npm 11.19.0): pass, `dist/` 9 entries.
  - flowers-frontend (Node 20.20.2, npm 10.8.2): pass; `build` is `echo no build needed`, so `dist/` stays empty by design (the workflow only writes `dist/version.js`).
- [x] 2.2 For each caller whose lockfile lacks Linux-native entries its build needs, or fails `npm ci` for drift: regenerate `package-lock.json` with `npm install` in the same container, re-run 2.1 on the result. Verify 2.1 passes and `git diff --stat` touches only `package-lock.json`. If none failed, note "no caller needed a fix" here.
  - homepage: `npm install` in `node:14` on top of the committed lockfile rewrote it as lockfileVersion 1 (npm 6). Resolved versions: 0 of 1500 changed; only hoisting positions differ, plus `bindings`, `file-uri-to-path`, `nan` added. 2.1 re-run: `npm ci` + build pass, `dist/` 9 entries. `git diff --stat`: only `package-lock.json`.
- [x] 2.3 If 2.2 could not fix a caller through its lockfile, decide the guarded `npm install --no-save` fallback (design D2) and record it in `design.md`; otherwise record "no fallback needed" in D2. Verify by reading `design.md`.
  - No fallback needed; recorded in D2.
- [x] 2.4 Commit and push each lockfile fixed in 2.2 to its repo (`fix: regenerate package-lock.json for npm ci`). Verify the caller's `PIPELINE` run is green (`gh run list -R <repo> -L 1`) before section 3.
  - homepage `67ba07b` pushed; run 36979678828 green (bump, build, docker-build, deploy), `https://unterrainer.info/` → 200. The build job ran on `dev1-runner3` and sat >10 min in "Post Set up Node".

## 3. Deploy – shared workflow (`UnterrainerInformatik/npm-build-workflow`)

- [x] 3.1 Replace the "Fresh dep-install" step with `npm ci` (design D1), add the fallback only if 2.3 decided it, remove the `actions/cache@v3` step (design D3). Verify with `actionlint` (Docker image `rhysd/actionlint`) on `workflow.yml`.
  - Done locally (uncommitted); actionlint 1.7.12 exit 0. Also checked that `npm ci` accepts a root `version` changed only in `package.json` (as `reedyuk/npm-version` may leave the lockfile alone): pass on npm 6.14.18, 10.8.2, 11.19.0.
- [x] 3.2 Commit (`fix: install from the committed lockfile with npm ci`) and push to `master`. Verify `git -C ../npm-build-workflow status` clean and `git log origin/master -1` is the commit.

## 4. Contract/Docs

- [x] 4.1 Remove the entry "Shared npm-build-workflow ignores the lockfile" from `ai/open-proposals.md`. Verify with `grep -c npm-build-workflow ai/open-proposals.md` → 0.
- [x] 4.2 Add to `ai/memory/reference_ci_runners.md` (or a new reference memory if it does not fit): `npm-build-workflow` installs with `npm ci`; callers must commit a lockfile in sync with `package.json` that carries Linux-native optional entries. Verify by reading the file and the `MEMORY.md` pointer.

## 5. Verification

- [x] 5.1 `gh workflow run PIPELINE -R UnterrainerInformatik/homepage`; verify the run is green, its build log shows `npm ci` (no `rm -rf ... package-lock.json`), and `https://unterrainer.info/` answers `200` and renders in a headless browser.
  - Run 36994253964 green (build on `dev1-runner2`), step "Install dependencies from lockfile" instead of the delete + install; site 200, renders in headless Chrome. The social icons from `files.unterrainer.info/logos/` answer 404 -- external file server, unrelated to the build.
- [x] 5.2 `gh workflow run PIPELINE -R UnterrainerInformatik/js-cms-gui-v2`; verify the run is green and its build log shows `npm ci`.
  - Run 36994258913 green (build on `babylon5-runner3`), step "Install dependencies from lockfile".
- [x] 5.3 Check that no `npm-build-workflow` run elsewhere failed since the push: `gh run list` for overmind-gui, js-cms-gui and flowers-frontend shows no new failure (they pick the change up on their next push). Verify by listing.
  - 2026-10-02 after pushing `a581196`: no run of any caller since the push; latest runs all green (overmind-gui 09-24, js-cms-gui 09-30, flowers-frontend 09-25, homepage and js-cms-gui-v2 10-02 — both still on the old workflow).
