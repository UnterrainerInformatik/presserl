## Why

The homepage pipeline (`UnterrainerInformatik/homepage`, `.github/workflows/pipeline.yml`) asks the
shared `deploy-workflow` for OpenVPN (`ovpn_enabled: true`, `VPN_OVPN_FILE`, `VPN_USERNAME`,
`VPN_PASSWORD`), but the repository holds none of those secrets. It does hold a `WG_CONFIG` secret
(created 2026-09-26) — and never passes it. Since `deploy-workflow` picks the VPN from the secrets
it receives (`WG_CONFIG` → WireGuard, else `VPN_OVPN_FILE` → OpenVPN, else none) and treats
`ovpn_enabled` as deprecated and ignored, every homepage deploy runs without a VPN and connects
directly. That is why dev1 timed out on 2026-09-30/10-01 and babylon5 works. The configuration
says one thing and does another; the secret Gerald set up for this deploy is dead.

The runner pinning half of this backlog entry is already done (pin-deploys-to-babylon5,
2026-10-01: `deploy-workflow` has `runs-on`, homepage deploys on `babylon5`). This change finishes
the remaining VPN half.

## What Changes

- **Homepage `UnterrainerInformatik/homepage`** (`~/source/private/js/homepage`): the `deploy` job
  of `.github/workflows/pipeline.yml`
  - passes `WG_CONFIG: ${{ secrets.WG_CONFIG }}` (as `presserl-deployment` does),
  - drops `ovpn_enabled: true` and the three OpenVPN secret lines with their comment (secrets that
    do not exist and an input the shared workflow ignores),
  - keeps `runs-on: '["self-hosted","Linux","X64","babylon5"]'`.
- **Memory** `ai/memory/reference_ci_runners.md`: the homepage deploy connects through WireGuard
  (`WG_CONFIG`); outcome of the verification run.
- **Backlog** `ai/open-proposals.md`: the entry "Shared deploy-workflow lands on runners that
  cannot reach the server" is deleted (its runner part landed with pin-deploys-to-babylon5).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — CI configuration only, no product behaviour changes (`skip_specs: true`).

## Impact

- **Platforms:** deploy (homepage CI). Backend, reader, admin, docs and the presserl deployment
  repositories are not affected.
- **External repositories:** `UnterrainerInformatik/homepage`; pushing it runs the full pipeline
  and redeploys `unterrainer.info` (same content as now, new image version).
- **Shared workflows:** `deploy-workflow` is unchanged — its WireGuard path is already used by
  `presserl-deployment` and `guFalcon/alexpresse`.
- **Risk:** if the `WG_CONFIG` stored in the homepage repo does not route to the homepage host,
  the deploy that works today breaks; rollback is reverting the one commit.

## Non-goals

- Unpinning the homepage deploy from babylon5. Whether dev1 can deploy through the tunnel is not
  tested here; the pin stays.
- Removing the deprecated `ovpn_enabled`/`wg_enabled` inputs from `deploy-workflow` (other callers
  may still pass them).
- Changes to the other `deploy-workflow` callers (`js-cms-gui`, `js-cms-gui-v2`, `overmind-gui`)
  or to the WireGuard server side.
- Reading or rotating the secret's contents.
