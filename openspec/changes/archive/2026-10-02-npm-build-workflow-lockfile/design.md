## Context

`npm-build-workflow/.github/workflows/workflow.yml` (single job, self-hosted Linux X64 runners
babylon5/dev1) runs: init runner → `actions/cache@v3` on `~/.npm` → `actions/setup-node@v4`
(`.nvmrc`, `cache: npm`) → `reedyuk/npm-version` sets the version in `package.json` →
`rm -rf node_modules package-lock.json && npm install` → `npm run build` → writes
`dist/version.js` → uploads `dist` as `build-artifacts` for docker-build.

Callers (all reference `@master`, all last runs green, all commit a lockfile):

| Repo | Node | lockfile | Linux-native optional packages |
|---|---|---|---|
| homepage | 14 | v2 | none |
| overmind-gui | 14 | v1 | none |
| js-cms-gui | 20.20.2 | v2 | none (webpack 4) |
| js-cms-gui-v2 | 24.20.0 | v3 | present (rollup/esbuild linux-x64 entries) |
| flowers-frontend | 20 | v2 | none |

So no caller is known to need the npm/cli#4828 workaround today; what is unknown is whether
every lockfile is in sync with its `package.json` — `npm install` silently fixed any drift,
`npm ci` will refuse it.

## Goals / Non-Goals

**Goals:**
- Builds install exactly the committed lockfile.
- No caller breaks when the workflow changes; drift is found and fixed before the switch.

**Non-Goals:**
- Supporting callers without a lockfile (none exist; `npm ci` failing loudly is acceptable).

## Decisions

### D1 — `npm ci` in place of delete + `npm install`

The step becomes `npm ci` (name: "Install dependencies from lockfile"). It installs the lockfile
verbatim, removes an existing `node_modules` itself, and fails on lockfile/`package.json` drift.

*Alternatives:* `npm install` without deleting the lockfile — would respect it mostly but still
rewrites it on drift and can resolve differently; not reproducible. Keeping the deletion behind an
opt-in input — keeps the dangerous path alive for no current user.

The version step stays before the install: `npm ci` does not compare the root package's own
`version` with the lockfile, only dependencies. Verified per caller in task 2 by bumping the
version locally before `npm ci`.

### D2 — Native-package fallback only if a caller needs it

Default: no fallback in the workflow. The rule instead is "lockfiles must carry the Linux-native
optional entries", which a current npm writes on any OS (js-cms-gui-v2's lockfile shows it does).
A caller whose lockfile lacks them gets the lockfile regenerated on Linux (task 2).

Only if a caller cannot be fixed that way does the workflow get a guarded step after `npm ci`
that installs the missing packages with `npm install --no-save <pkg>` (lockfile untouched). This
is decided during task 2 and recorded here.

**Outcome (task 2.3): no fallback needed.** All five callers pass `npm ci` + build on Linux; the
only failure (homepage, npm 6 on an npm ≥7 v2 lockfile) was fixed by regenerating its lockfile.

*Alternative:* always run `npm install --no-save --include=optional` after `npm ci` — re-resolves
the tree and reintroduces version drift through the back door.

### D3 — Drop `actions/cache@v3`

`setup-node` with `cache: npm` already caches `~/.npm` keyed on `package-lock.json`; the extra
cache step duplicates it and is on a deprecated major. Removing it changes no install result.

### D4 — Verify each caller in a clean Linux container before the switch

For each caller: fresh clone of `origin/master` into the session scratchpad (never Gerald's
working copies, two of which have uncommitted work), then in `docker run node:<.nvmrc>`:
`npm version --no-git-tag-version <x.y.z>` → `npm ci` → `npm run build` → `dist/` non-empty. This
mirrors the runner (Linux X64, Node from `.nvmrc`) without pushing anything. The workflow
itself is checked with `actionlint`.

*Alternative:* push the workflow to a branch and point a caller at it — needs throwaway commits in
a caller and still only tests one caller.

## Risks / Trade-offs

- [A caller's lockfile drifted from `package.json`] → found by D4; fix by regenerating the lockfile
  with `npm install` in the same container and committing only `package-lock.json`. Pushing it runs
  that caller's pipeline with the old workflow first — harmless, it ignores the lockfile anyway.
- [Regenerated lockfile pulls newer transitive versions — exactly the risk the change removes] →
  the build in D4 must pass and the deployed site is checked after the push.
- [Node 14 ships npm 6; `npm ci` with lockfile v2 there] → npm 6 reads v2 via its v1 section;
  covered by D4 for homepage and overmind-gui.
- [Unknown caller outside the two owners] → GitHub code search covered both accounts; others would
  fail loudly at `npm ci`, not deploy something broken.

## Migration Plan

1. Verify all callers (D4); fix and push drifted lockfiles, wait for their green pipelines.
2. Commit + push the workflow to `master` of `npm-build-workflow`.
3. Trigger a run in homepage and js-cms-gui-v2 (`gh workflow run PIPELINE`), confirm green and the
   sites answer; the others pick it up on their next push.

**Rollback:** `git revert` the workflow commit and push — callers take the old behaviour on their
next build.
