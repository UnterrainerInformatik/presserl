## 1. Deploy – homepage pipeline

- [x] 1.1 `git -C ~/source/private/js/homepage pull --ff-only` on `master`. Verify with `git status -sb` showing `master...origin/master` without `behind` and a clean tree.
- [x] 1.2 In `.github/workflows/pipeline.yml`, `deploy` job: add `WG_CONFIG: ${{ secrets.WG_CONFIG }}` to `secrets`, remove `ovpn_enabled: true`, the `# Optional – only when OVPN is needed:` comment and the `VPN_OVPN_FILE`/`VPN_USERNAME`/`VPN_PASSWORD` lines; keep `runs-on` (design D1–D3). Verify with `actionlint` (Docker image `rhysd/actionlint`) on the file and `git diff` showing only these lines.

## 2. Contract/Docs

- [x] 2.1 Update `ai/memory/reference_ci_runners.md` (deploys pinned to babylon5 section): the homepage deploy passes `WG_CONFIG` and connects through WireGuard; record the verification run id. Verify by reading the file.
- [x] 2.2 Delete the entry "Shared deploy-workflow lands on runners that cannot reach the server" from `ai/open-proposals.md`. Verify with `grep -c "cannot reach the server" ai/open-proposals.md` → `0`.

## 3. Verification

- [x] 3.1 Commit and push the homepage (runs `PIPELINE`). Verify with `gh run list -R UnterrainerInformatik/homepage -L 1` green; in the deploy job (jobs API / `gh run view --log`) `runner_name` starts with `babylon5-runner`, `Connect to WireGuard` ran (`wg-deploy` up, route set) and the VPN controller's event log shows the `homepage` peer connected during the run with data transferred (`deploy-workflow` runs `wg show` before any traffic, so the job log never shows a handshake), the OpenVPN steps were skipped and `Disconnect WireGuard` ran.
- [x] 3.2 The site is live after the deploy: `curl -s -o /dev/null -w '%{http_code}' https://unterrainer.info/` → `200`. On failure in 3.1/3.2: revert the commit, push, confirm the next run is green again, and report to Gerald.

> Note (2026-10-02): 3.1 verified with run 37011845901 (babylon5-runner1, all jobs green, OpenVPN steps
> skipped, `Disconnect WireGuard` ran). VPN controller log: `homepage` connected 15:20:21 local
> (13:20 UTC), 52 s, 28.07 KB up / 31.34 KB down. The handshake criterion was replaced by this
> evidence (Gerald, 2026-10-02).
