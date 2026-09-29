---
name: reference_ci_runners
description: Self-hosted GitHub runners on babylon5 (fast) and dev1 (slow VM); presserl pinned to babylon5 with persistent buildx builder 'presserl'; reset commands
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

See [[reference_build_and_test]].
