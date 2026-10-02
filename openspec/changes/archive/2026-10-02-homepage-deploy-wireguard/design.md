## Context

See proposal.md – Why. Relevant state (2026-10-02):

- `deploy-workflow` (`master`) declares `WG_CONFIG` as an optional secret, exports it as job env and
  runs `Install WireGuard` / `Connect to WireGuard` (`wg-quick up wg-deploy`, DNS lines stripped)
  when it is non-empty, plus `Disconnect WireGuard` with `always()`. OpenVPN steps only run when
  `WG_CONFIG` is empty and `VPN_OVPN_FILE` is set. `ovpn_enabled`/`wg_enabled` are declared but
  ignored.
- `presserl-deployment` (`deploy.yml`) passes `WG_CONFIG: ${{ secrets.WG_CONFIG }}` and deploys
  green from both dev1 and babylon5 runners.
- Homepage secrets: `DATA_DIR`, `DEPLOY_*`, `DOCKER_HUB_*`, `DOCKER_IMAGE_NAME`, `WG_CONFIG`; no
  `VPN_*`. Last runs green on babylon5 without VPN (e.g. 36994253964, 2026-10-02).
- The local clone `~/source/private/js/homepage` is 6 commits behind `origin/master`.

## Goals / Non-Goals

**Goals:**
- The homepage pipeline passes exactly the VPN secret it has, and its deploy connects through
  WireGuard.

**Non-Goals:**
- Proving or using dev1 reachability; touching the shared workflow.

## Decisions

### D1 — WireGuard, not OpenVPN

The repository holds `WG_CONFIG` and no OpenVPN secrets; Gerald created it on 2026-09-26, the same
day as `presserl-deployment`'s `WG_CONFIG`, when WireGuard support went into `deploy-workflow`.
WireGuard is therefore the intended VPN. *Alternative:* drop the VPN lines entirely and keep the
direct connection — works today from babylon5, but leaves the created secret unused and ties the
deploy to babylon5's network path for good.

### D2 — Remove the dead lines instead of keeping them "optional"

`ovpn_enabled` is ignored by the shared workflow and the `VPN_*` secrets do not exist (they
evaluate to empty strings). Keeping them suggests an OpenVPN setup that does not exist — that
misreading is how this entry landed in the backlog.

### D3 — Keep the babylon5 pin

The pin from pin-deploys-to-babylon5 stays. Builds and deploys on babylon5 are faster, and this
change verifies only the babylon5 path. Unpinning can be tried later as its own change.

## Risks / Trade-offs

- [`WG_CONFIG` content is stale or routes the deploy host through a peer that cannot reach it] →
  the deploy step fails at SSH; the pushed run shows it immediately. Rollback: `git revert` the
  commit and push (redeploys without VPN as today). The failure is reported to Gerald, not patched
  around.
- [A WireGuard tunnel left up on a shared babylon5 runner] → the workflow tears down `wg-deploy`
  before connecting and in an `always()` step; verify the step ran in the job log.
- [Overlapping `AllowedIPs` with presserl-deployment's tunnel when both jobs run concurrently on the
  same runner host] → both use interface name `wg-deploy` inside separate runner containers; not
  observed so far, noted for the verification log.

## Migration Plan

Pull the homepage clone, edit `pipeline.yml`, `actionlint`, commit, push (runs PIPELINE). Check the
deploy job: runner `babylon5-runner*`, `Connect to WireGuard` ran and printed a handshake,
`Disconnect WireGuard` ran, run green, `https://unterrainer.info/` answers `200`. Rollback: revert
the commit.
