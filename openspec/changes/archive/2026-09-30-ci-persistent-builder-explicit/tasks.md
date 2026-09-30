## 1. Shared workflow (`UnterrainerInformatik/docker-build-workflow`)

- [x] 1.1 Clone `UnterrainerInformatik/docker-build-workflow` to `../docker-build-workflow` (not present locally) and check out `master`. Verify with `git -C ../docker-build-workflow log -1`.
- [x] 1.2 Confirm in `docker/build-push-action@v7` (its `action.yml` and input handling) that an empty `builder` input omits `--builder` (design, Risks). Verify by quoting the relevant lines.
- [x] 1.3 Add `builder: ${{ inputs.builder-name }}` to the `Build and push 🔨` step's `with:` block, with a short comment why (design D1). Verify with `actionlint` (Docker image) on the workflow file.
- [x] 1.4 Show Gerald the diff and ask before committing and pushing to `master` (live for every caller). Verify with `git -C ../docker-build-workflow log -1` and `git status` clean after the push.

## 2. Verification

- [x] 2.1 Re-run the presserl pipeline (`gh workflow run PIPELINE` or `gh run rerun 36754878179 --failed`). Verify with `gh run view --log` that the `docker-build` job's `Builder info` shows driver `docker-container` and builder `presserl`, the build succeeds, and `dispatch-staging` runs.
- [x] 2.2 Confirm staging redeployed the new image. Verify with `gh run list -R UnterrainerInformatik/presserl-deployment -L 1`.
- [ ] 2.3 Check one other caller's next (or a dispatched) image build, e.g. js-cms-gui, still succeeds with its ephemeral `builder-<uuid>` (design D2). Verify with `gh run view --log` (`Builder info` name and a green job).

## 3. Memory

- [x] 3.1 Update `ai/memory/reference_ci_runners.md`: `current` builder pointer is per runner container and left dangling by other repos' ephemeral builders; `setup-buildx-action` does not `use` an existing builder; the workflow therefore passes `builder` explicitly (since this change). Verify by reading the file.
