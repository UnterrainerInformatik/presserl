## Context

See proposal.md – Why. Relevant state (2026-10-02):

- `init-runner-action` (`master`, `5e56f6a`) is a composite action: debug echo of `self_hosted`;
  `Pre-fetch actions` (only when `self_hosted == 'true'`) shallow-clones each listed
  `owner/name@ref` into `$RUNNER_WORKSPACE/_actions/owner/name/ref` unless `action.yml` exists
  there; then `actions/checkout@v4` with `fetch-depth: 0`. No local clone exists yet.
- Callers passing `self_hosted: 'true'`: `bump-semver-workflow`, `java-elite-server`. All others
  use the defaults.
- Current majors: checkout v7.0.1, upload-artifact v7, download-artifact v8, setup-node v7,
  setup-java v6, cache v6. `docker-build-workflow` already uses `download-artifact@v8`.
- Runners: babylon5 and dev1 run actions runner 2.337.0 (memory `reference_ci_runners.md`).

## Goals / Non-Goals

**Goals:**
- No Node 20 deprecation annotation from `init-runner-action` itself.
- The pre-fetch list names majors a current workflow would actually use.

**Non-Goals:**
- Updating callers' own action pins (backlog entry instead).

## Decisions

### D1 — Jump straight to `checkout@v7`

v5 is the Node 24 switch; v6 and v7 add credential-file persistence and the fork-PR guard, neither
of which affects these callers (push to own repo via token, no `pull_request_target`). Going to
the current major avoids another round soon. *Alternative:* `v5` (minimum to clear the annotation)
— no benefit, older dependency set.

### D2 — Keep the pre-fetch, raise its list

The pre-fetch exists for self-hosted runners and two callers opt into it; removing it changes their
behaviour and is not what this entry asks for. Its list is raised to the current majors so it
pre-fetches what updated workflows will reference. Since the clone path is keyed by ref, the old
v4/v3 copies stay on the runners and remain usable by callers still pinning them.

### D3 — Verify on a cheap caller before declaring done

`init-runner-action@master` is live for everyone on push. Validate the YAML first (`actionlint`
cannot lint composite actions; use a YAML parse plus a review of the diff), push, then trigger
real runs: presserl-deployment's `deploy.yml` via `gh workflow run` (deploy-workflow path, default
inputs) and one caller using `self_hosted: 'true'` (`bump-semver-workflow` runs as the first job
of the homepage pipeline — or of presserl's pipeline on the next push). Check that the checkout
step shows `actions/checkout@v7`, no Node 20 annotation appears, and, for the bump job, the tag
push still succeeds.

## Risks / Trade-offs

- [Push breaks every pipeline at once] → minimal diff (version strings only); rollback is a revert
  push, effective on the next run.
- [checkout v6+ credential file breaks `git push` in `bump-semver-workflow`] → verified in D3; on
  failure revert, then investigate (e.g. `persist-credentials` handling) in a follow-up change.
- [`git clone --branch v7` in the pre-fetch fails if a major tag is missing] → all six majors exist
  as tags (checked 2026-10-02); the bump job's log shows each clone.
- [Disk on babylon5 (`/` 89 % on 2026-09-29)] → six additional shallow clones per runner, a few MB
  each; negligible.

## Migration Plan

Clone, edit, commit, push `init-runner-action`; trigger the verification runs (D3). Rollback:
`git revert` + push.
