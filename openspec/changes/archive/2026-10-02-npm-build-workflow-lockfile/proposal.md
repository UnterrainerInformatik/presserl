## Why

The shared `UnterrainerInformatik/npm-build-workflow` deletes `package-lock.json` and runs
`npm install` before every build (commit `f350a0c`, 2025-10-28, "fix linux-errors"), so each build
resolves the newest versions `package.json` allows instead of the committed ones. On 2026-09-30
this took `unterrainer.info` down completely: a newer `on-headers` (pulled in by a `compression`
import in the browser bundle) read `http.ServerResponse` at load time. The homepage was patched by
dropping the import; the cause in the workflow is still there and can break any caller on its next
build without a single commit of its own.

The deletion was a workaround for npm/cli#4828: a lockfile written on Windows/macOS can lack the
Linux variants of optional native packages (`@rollup/rollup-linux-x64-gnu`, `@esbuild/linux-x64`,
…), and `npm install` on top of such a lockfile then fails or the build cannot load the binary.

## What Changes

- **Shared workflow `UnterrainerInformatik/npm-build-workflow`** (outside this repo, referenced
  by callers as `@master`): the "Fresh dep-install" step is replaced by `npm ci`, which installs
  exactly the committed lockfile and fails if `package.json` and lockfile disagree. A guarded
  fallback re-installs missing Linux-native optional packages without touching the lockfile, kept
  only if a caller actually needs it (design D2). The redundant `actions/cache@v3` step goes;
  `actions/setup-node` already caches `~/.npm`.
- **Callers** — any whose lockfile is out of sync with its `package.json` or lacks Linux-native
  entries gets a regenerated lockfile, committed and pushed **before** the workflow change:
  - `UnterrainerInformatik/homepage` (`/home/psilo/source/private/js/homepage`, Node 14)
  - `UnterrainerInformatik/overmind-gui` (`/home/psilo/source/private/js/overmind-gui`, Node 14)
  - `UnterrainerInformatik/js-cms-gui` (`/home/psilo/source/cms/js/js-cms-gui`, Node 20.20.2)
  - `UnterrainerInformatik/js-cms-gui-v2` (`/home/psilo/source/cms/js/js-cms-gui-v2`, Node 24.20.0, Vite)
  - `guFalcon/flowers-frontend` (no local clone, Node 20)
- **Backlog** `ai/open-proposals.md`: the entry "Shared npm-build-workflow ignores the lockfile"
  is removed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — shared CI configuration only, no presserl behaviour changes (`skip_specs: true`).

## Impact

- **Platforms:** deploy (shared CI of the JS frontends). presserl's backend, reader, admin, deploy
  templates and docs are not affected; presserl does not call this workflow.
- **External repositories:** `UnterrainerInformatik/npm-build-workflow` and, where a lockfile
  needs regenerating, the caller repos listed above. Every push to a caller runs its pipeline and
  redeploys its site.
- **Blast radius:** callers use `@master`, so the workflow change applies to all of them on their
  next build. A caller with a stale lockfile would then fail at `npm ci` (loudly, before any
  deploy) — hence the local verification of every caller first.
- **Builds become reproducible:** dependency updates happen only when a caller commits a new
  lockfile.

## Non-goals

- `guFalcon/2026-ai-meeting`: its pipeline sits under `frontend/.github/workflows/`, which GitHub
  never runs; it is not a caller in practice and is not touched.
- No dependency upgrades in the callers beyond what a lockfile regeneration strictly requires.
- No Node version changes (Node 14 callers stay on 14).
- No `runs-on` input or other new inputs for the workflow; no change to `init-runner-action`
  (separate backlog entry) or to `actions/setup-node@v4`/`upload-artifact@v4` majors.
- Gerald's uncommitted work in the local clones of `js-cms-gui` and `js-cms-gui-v2` is not
  touched; verification runs in separate clean checkouts.
