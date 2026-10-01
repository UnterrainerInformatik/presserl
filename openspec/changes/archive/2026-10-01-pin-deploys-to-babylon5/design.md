## Context

See proposal.md – Why. Relevant state:

- `UnterrainerInformatik/deploy-workflow` (`.github/workflows/workflow.yml`, `master`, latest
  `edf9058`) has one job `deploy` with `runs-on: [self-hosted, Linux, X64]`. Callers:
  `homepage`, `presserl-deployment`, `js-cms-gui`, `js-cms-gui-v2`, `overmind-gui` (code search),
  plus `guFalcon/alexpresse`.
- Org runners `babylon5-runner1..3` carry `linux,x64,ubuntu-latest,babylon5`; `dev1-runner1..3`
  carry no `babylon5` label (memory `reference_ci_runners.md`). GitHub matches `runs-on` labels
  case-insensitively, so `Linux`/`X64` match `linux`/`x64`.
- `guFalcon/alexpresse` has exactly one runner, the repo runner `babylon5-runner11-priv-ALEXPRESSE`
  (compose service `worker11-gufalcon`, container `ghar-priv-alexpresse`, image built from the
  same `myoung34/github-runner` Dockerfile) with `LABELS: linux,x64,ubuntu-latest`. The runner
  registers on container start from the environment; its registration is not kept in a volume
  (only `RUNNER_WORKDIR` is mounted).
- `docker-build-workflow` already has `runs-on` (JSON label array, default
  `'["self-hosted","Linux","X64"]'`, used as `${{ fromJSON(inputs.runs-on) }}`); presserl's
  pipeline passes `'["self-hosted","Linux","X64","babylon5"]'`.

## Goals / Non-Goals

**Goals:**
- Callers of `deploy-workflow` can choose the runner labels of the deploy job; nothing changes for
  callers that do not.
- Homepage and alexpresse deploys only run on babylon5 runners.

**Non-Goals:**
- Making dev1 able to reach the homepage host.
- Any per-repo runner, new runner or runner-group setup.

## Decisions

### D1 — Optional `runs-on` input in the shared deploy workflow

Add to `on.workflow_call.inputs`:

```yaml
      runs-on:
        description: 'JSON array of runner labels for the deploy job'
        required: false
        type: string
        default: '["self-hosted","Linux","X64"]'
```

and `runs-on: ${{ fromJSON(inputs.runs-on) }}` in the job. Identical to `docker-build-workflow`,
so both workflows are configured the same way.

*Alternatives:* a boolean `babylon5` input (too specific for a shared workflow); copying the
deploy steps into the homepage repo (duplicates the VPN/SSH logic the shared workflow exists for);
runner groups (not available for these self-hosted runners without org settings changes).

### D2 — Pin with the existing `babylon5` label

Homepage and alexpresse pass `'["self-hosted","Linux","X64","babylon5"]'`. The label already
exists on the org runners; on the org runners only babylon5 can serve the homepage deploy.

*Alternative:* a label naming the reachable network (e.g. `deploy-net`) — clearer intent, but
needs relabelling every runner and gains nothing while babylon5 is the only such host.

### D3 — Label the alexpresse runner through its compose `LABELS`

Edit `LABELS: linux,x64,ubuntu-latest` → `linux,x64,ubuntu-latest,babylon5` in
`babylon5:~/scripts/github-runner/docker-compose.yml` and recreate only that service:
`docker compose up -d --no-deps --force-recreate worker11-gufalcon`, when it runs no job.
Confirm the labels via `gh api repos/guFalcon/alexpresse/actions/runners`.

*Alternative:* `POST /repos/guFalcon/alexpresse/actions/runners/{id}/labels` — immediate and no
restart, but lost the next time the container re-registers (image update, host reboot), after
which every alexpresse deploy would queue forever.

The compose file is edited in place, without a commit (it carries Gerald's uncommitted edits and
an access token). Before editing, take a backup copy next to it in the session's scratchpad.

### D4 — Order of rollout

1. `deploy-workflow` input (no effect on anyone).
2. alexpresse runner label (D3), then the alexpresse workflow change — never the other way round.
3. Homepage workflow change; its push runs the whole pipeline and deploys the pending
   account-deletion page.

## Risks / Trade-offs

- [babylon5 down or all three org runners busy] → homepage deploys queue instead of failing on
  dev1. Fallback: drop the input in `pipeline.yml`.
- [Push of `deploy-workflow` is live for every caller at once] → the default equals today's value;
  `actionlint` before pushing; the next homepage run exercises the new input, and the next
  presserl-deployment dispatch exercises the default.
- [Recreating `ghar-priv-alexpresse` while it runs a job] → check `docker ps` / the runner's
  `busy` flag first; the staging/fork deploys are short.
- [alexpresse push redeploys alexpresse.net] → it deploys the latest upstream release tag, the
  same image that is already running (`docker compose` pull is a no-op restart at most).
- [Recreated runner does not register the new label] → visible in the runners API before the
  alexpresse workflow is pushed; then revert the compose edit from the backup.

## Migration Plan

Rollout in D4 order. Rollback: revert the commit in the affected repo (homepage/alexpresse) — the
shared workflow input can stay, it is inert with the default; restore the compose file from the
backup and recreate the runner.
