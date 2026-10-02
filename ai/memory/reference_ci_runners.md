---
name: reference_ci_runners
description: Self-hosted GitHub runners on babylon5 (fast) and dev1 (slow VM); presserl builds and homepage/alexpresse deploys pinned to babylon5 with persistent buildx builder 'presserl'; reset commands; npm-build-workflow uses npm ci (callers need an in-sync lockfile)
metadata:
  type: reference
---

Two sets of org-scoped (`UnterrainerInformatik`) self-hosted runners, each `ghar-runner1..3`
(image `myoung34/github-runner:ubuntu-noble` + maven), both registered with
`linux,x64,ubuntu-latest`. A job without a narrower label lands on either set at random.

- **babylon5** (`babylon5-runner1..3`): AMD Ryzen 5 5600 (6C/12T, ≤ 4.47 GHz), bare metal, 62 GiB
  RAM, disk 1.3 GB/s. Compose in `babylon5:/home/psilo/scripts/github-runner/docker-compose.yml`
  (git repo `scripts`, Gerald keeps uncommitted edits there — don't commit them). Since 2026-09-29
  runners 1..3 carry the extra label **`babylon5`**. Also hosts guFalcon repo runners
  (`ghar-priv-*`, incl. alexpresse). Swap was full (8/8 GiB) and `/` 89 % used on 2026-09-29.
- **dev1** (`dev1-runner1..3`): Intel Xeon E5-2620 @ 2.0 GHz (2012), VMware VM, 12 vCPU, 35 GiB,
  disk 45 MB/s.

Measured 2026-09-29 (`openssl speed rsa2048` sign/s; `dd` 1 GiB fdatasync): babylon5 1988 single /
9832 multi, dev1 557 / 3138 → dev1 3–4× slower in CPU, ~30× slower on disk. presserl image build:
15:30 on dev1 vs 6:33 on babylon5 (Gradle Wasm 8:47 vs 3:22, Binaryen optimize 3:07 vs 0:41).

Runners mount the host's `/var/run/docker.sock`, so buildx builder containers live on the host
docker daemon. `docker-build-workflow` (since 2026-09-29) takes optional inputs `runs-on` (JSON
label array) and `builder-name`; callers without them still get a fresh `builder-<uuid>` per run
(leaked `buildx_buildkit_builder-*` containers, weeks old, still sit on babylon5 — not ours to
remove without asking).

**presserl** (ci-build-speed) builds only on `babylon5` with the persistent builder `presserl`:
node container `buildx_buildkit_presserl0`, state volume `buildx_buildkit_presserl0_state` on
babylon5. BuildKit GC: `maxUsedSpace 15GB`, `minFreeSpace 20GB`, `reservedSpace 2GB` (inline
config, applied only when the container is first created). Volume was 4.9 GB after the first
builds. Reset / change the cap: `docker buildx rm presserl` from a runner, or on the host
`docker rm -f buildx_buildkit_presserl0 && docker volume rm buildx_buildkit_presserl0_state`.
Build times (image build step): cold 5:36 (job 5:57; Gradle Wasm 3:11, Brotli 20 s, Maven 51 s,
push 39 s); fully cached 18 s (job 35 s). Brotli now runs in the admin stage, so backend-only
pushes keep Gradle and Brotli cached. Not yet observed: a second babylon5 runner attaching to the
existing node container (both measured runs ran on runner3). babylon5 busy/down ⇒ presserl builds
queue; drop the label in `pipeline.yml` as fallback.

**Deploys pinned to babylon5** (pin-deploys-to-babylon5, 2026-10-01). `deploy-workflow` (since
`b53bedb`) takes an optional `runs-on` input (JSON label array, default
`'["self-hosted","Linux","X64"]'`, same as `docker-build-workflow`). The **homepage**
(`UnterrainerInformatik/homepage`) and **alexpresse** (`guFalcon/alexpresse`) deploy jobs pass
`'["self-hosted","Linux","X64","babylon5"]'`. Reason: dev1 cannot reach the homepage deploy host
(SSH `Connection timed out`, run 36850891421 on dev1-runner1/2), babylon5 can. The alexpresse repo
runner `ghar-priv-alexpresse` (`babylon5-runner11-priv-ALEXPRESSE`, compose service
`worker11-gufalcon`) carries the `babylon5` label via its compose `LABELS` — set it there, not via
the labels API (lost on re-registration), otherwise alexpresse deploys queue forever.
`presserl-deployment` and the other callers stay unpinned (default).

**Homepage deploys through WireGuard** (homepage-deploy-wireguard, 2026-10-02). The homepage
`deploy` job passes `WG_CONFIG` (no `ovpn_enabled`, no `VPN_*` secrets — `deploy-workflow` picks
the VPN from the secrets: `WG_CONFIG` → WireGuard, else `VPN_OVPN_FILE` → OpenVPN, else none).
Before this every homepage deploy ran without VPN. Verified with run 37011845901 (babylon5-runner1).
Evidence that the tunnel carries traffic: `deploy-workflow` prints `wg show` right after
`wg-quick up`, so no `latest handshake` ever appears in the job log; check the UniFi VPN event log
instead ("homepage has connected to WG - Pipelines Deployments's VPN", ~50 s, ~28 KB up/down per
deploy — same pattern as the `presserl`/`alexpresse` peers).

**Current-builder pointer pitfall** (seen 2026-09-30, run 36754878179: `Multi-platform build is
not supported for the docker driver`). Each runner container has its own `~/.docker/buildx/`
(`instances/`, `current`), shared by every repo's jobs on that runner. Other callers create an
ephemeral `builder-<uuid>` with `--use` and remove it, leaving `current` dangling → buildx falls
back to `default` (docker driver). `setup-buildx-action` only `use`s a builder it creates; for an
existing one it logs `already exists, skipping creation` and leaves `current` alone. Fix
(ci-persistent-builder-explicit, docker-build-workflow `f490069`): the `Build and push` step passes
`builder: ${{ inputs.builder-name }}` (empty for other callers = current builder, as before).
Never rely on `current` for the persistent builder.

**Runner too old ⇒ jobs stay `queued` forever while GitHub shows the runners online/idle.** Seen
2026-09-29: image runner 2.333.1, GitHub pushed 2.337.0; the runner downloads the update, exits,
the container restarts from the old image and the loop repeats every ~2 min (log: `Downloading
2.337.0 runner` … `Runner will exit shortly for update` … `Current runner version: '2.333.1'`).
Fix on babylon5: `cd ~/scripts/github-runner && docker compose build --pull && docker compose up -d
--force-recreate --remove-orphans` (every service has `build: .` on `myoung34/github-runner:ubuntu-noble`
+ maven; images `github-runner-worker*`). Check with `docker logs <c> | grep 'Current runner version'`.
dev1 (`ssh dev1`, needs VPN; user `elite`, `~/scripts/github-runner`, not a git repo) needs the same
commands; there the failed update left the containers restart-looping with `Cannot configure the
runner because it is already configured` (exit 2) — recreating the containers clears that too.

**npm-build-workflow installs with `npm ci`** (npm-build-workflow-lockfile, `a581196`,
2026-10-02). Before, it deleted `package-lock.json` and ran `npm install`, so every build took the
newest allowed versions (took unterrainer.info down 2026-09-30). Callers (homepage, overmind-gui,
js-cms-gui, js-cms-gui-v2, guFalcon/flowers-frontend — the last on `main`) must commit a lockfile in
sync with `package.json` that carries the Linux-native optional entries; drift fails loudly at
`npm ci`. Node 14 callers (npm 6) need a lockfile npm 6 can read: homepage's npm ≥7 v2 lockfile
failed with `bindings not accessible from watchpack-chokidar2:fsevents` and was regenerated as v1
in `node:14` (`67ba07b`). Verify a caller locally with `docker run node:<.nvmrc>` →
`npm ci && npm run build`. No `actions/cache` step any more (setup-node caches `~/.npm`).

**init-runner-action on Node 24** (init-runner-action-node24, 2026-10-02). The shared
`UnterrainerInformatik/init-runner-action` (referenced `@master`, first step of every shared
workflow) checks out with `actions/checkout@v7` (Node 24, needs runner ≥ 2.327.1; v6+ stores the
git credentials in a file under `$RUNNER_TEMP` instead of `.git/config`). Its `self_hosted: 'true'`
pre-fetch list is on the current majors: checkout v7, upload-artifact v7, download-artifact v8,
setup-node v7, setup-java v6, cache v6; old v4/v3 clones stay on the runners (path keyed by ref).

See [[reference_build_and_test]].
