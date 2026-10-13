## 1. Deploy -- presserl pipeline

- [x] 1.1 Add `with: cache-disabled: true` to `gradle/actions/setup-gradle@v5` in `.github/workflows/pipeline.yml`. Verify with `actionlint` (Docker image `rhysd/actionlint`): no finding except the known custom `babylon5` runner label.

## 2. Deploy -- shared workflows and runners (outside this repo)

- [x] 2.1 `docker-build-workflow`: input `platforms` (default `linux/amd64`), QEMU only when it differs. Verify: pushed as 4d83833, actionlint exit 0.
- [x] 2.2 `npm-build-workflow` and `maven-central-workflow`: GitHub cache steps removed. Verify: pushed as 4821ec5 and 9068223.
- [x] 2.3 babylon5: shared `m2-repository` and `npm` cache mounted into all runner containers. Verify: all ten containers "Listening for Jobs", both mounts present.

## 3. Contract/Docs

- [x] 3.1 Update `ai/memory/reference_ci_runners.md`: shared caches on babylon5, no GitHub cache in the shared workflows or presserl's Gradle setup, `platforms` input with amd64 default. Verify by reading the file.

## 4. Verification

- [x] 4.1 The pipeline run of this push is green (bump, docker-build, staging dispatch) and `docker buildx imagetools inspect` of the new tag lists only `linux/amd64`.
  - Run 38398749976 (d92c0b4) green: bump 11 s, docker-build 32 s with `platforms: linux/amd64`, staging dispatch; Android job skipped (no `admin/` change). `https://presserl.unterrainer.info/` 200.
- [ ] 4.2 The next Android build (first push touching `admin/`, or a manual run) is green and shows no cache restore/save in `Set up Gradle` / `Post Set up Gradle`.
