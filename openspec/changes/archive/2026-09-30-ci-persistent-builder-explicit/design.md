## Context

See proposal.md – Why. Relevant facts about the runners (details in
`ai/memory/reference_ci_runners.md`):

- Each runner container keeps its own buildx store in `~/.docker/buildx/` (`instances/`,
  `current`). The builder's node container (`buildx_buildkit_presserl0`) lives on the host docker
  daemon and is shared by all runners through the mounted socket.
- The same runner containers also run jobs of other repos that call `docker-build-workflow`
  without `builder-name`; those create and remove `builder-<uuid>` and leave `current` dangling.
- `docker-build-workflow` has two mutually exclusive `setup-buildx-action` steps (ephemeral when
  `builder-name` is empty, persistent otherwise) followed by one `build-push-action@v7` step
  without a `builder` input.

## Goals / Non-Goals

**Goals:**
- The presserl build uses the `presserl` builder deterministically, independent of which jobs ran
  on the runner before.
- Zero behaviour change for callers without `builder-name`.

**Non-Goals:**
- Repairing or resetting the `current` pointer in the runner containers.

## Decisions

### D1 – Pass `builder` to `build-push-action` instead of re-selecting the current builder

`build-push-action` gets `builder: ${{ inputs.builder-name }}`, which becomes `--builder <name>`
on the buildx command line. For an empty input the action omits the flag and uses the current
builder, as today.

Alternatives considered:
- *Extra step `docker buildx use <name>` after setup.* Also works, but it fixes the symptom through
  more shared mutable state; a later step or composite action could change it again. The explicit
  flag is scoped to the one command that needs it.
- *`builder: ${{ steps.<id>.outputs.name }}` of whichever setup step ran.* Covers the ephemeral
  case too, but needs ids on both steps and an `||` expression, for no gain: the ephemeral builder
  is already current because it was just created with `--use`.
- *Upstream fix in `setup-buildx-action` (`use` on an existing builder).* Outside our control and
  not needed once the build names its builder.

### D2 – Verify on presserl by re-running, not by a synthetic test

The failing state (dangling `current` on every babylon5 runner) exists right now, so re-running the
presserl pipeline after the workflow push is a real reproduction: a green run proves the fix.
One other caller's next build confirms the ephemeral path still works.

## Risks / Trade-offs

- [The workflow is consumed at `@master` by five repos; a mistake breaks all of them] → the change
  is one input line; `actionlint` before pushing; Gerald sees the diff and approves the push; one
  non-presserl caller's build is checked afterwards.
- [`build-push-action@v7` might treat an empty `builder` differently than an absent one] → check
  the action's input handling (`action.yml` / source) before pushing and confirm with the
  non-presserl caller's run log (`Builder info` shows the `builder-<uuid>`).

## Migration Plan

1. Push the workflow change to `docker-build-workflow@master`.
2. Re-run the presserl `PIPELINE` (`gh workflow run` or re-run of 36754878179's failed job).
3. Rollback: revert the one commit in `docker-build-workflow`; presserl can temporarily drop
   `builder-name` from `pipeline.yml` to build with an ephemeral builder.
