## 1. Deploy (CI pipeline)

- [x] 1.1 In `.github/workflows/pipeline.yml`, give the existing upload step `id: play-upload-1`, `continue-on-error: true` and anchor its inputs (`with: &play-upload-inputs`); verify by reading the step
- [x] 1.2 Add the pause step (warning annotation + `sleep 60`) and attempt 2 (`id: play-upload-2`, `continue-on-error: true`), both guarded by `steps.changes.outputs.changed == 'true' && steps.play-upload-1.outcome == 'failure'`; verify the conditions by reading the step
- [x] 1.3 Add the pause step (warning + `sleep 180`) and attempt 3 (`id: play-upload-3`, no `continue-on-error`, `with: *play-upload-inputs`), guarded by `changed == 'true' && steps.play-upload-2.outcome == 'failure'`; verify by reading the steps
- [x] 1.4 Lint the workflow (`actionlint` via its Docker image or `go run`) and verify it reports no errors; if the anchor is rejected, fall back to verbatim copies as in design D1

## 2. Contract/Docs

- [x] 2.1 Remove the "Retry the Play upload on transient errors" entry from `ai/open-proposals.md`; verify with `grep`

## 3. Verification

- [ ] 3.1 After push, verify on the next pipeline run with an `admin/` change that attempt 1 succeeds, the pauses and attempts 2/3 are skipped, the job is green and the release appears on the internal track (`gh run view`)
