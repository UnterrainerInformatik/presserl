## Context

The Android job in `.github/workflows/pipeline.yml` ends with `google-github-actions/auth@v2`
(short-lived OIDC credentials) and `r0adkll/upload-google-play@v1.1.5`, which opens a Play edit,
uploads AAB and mapping, assigns the internal track and commits the edit. Any error discards the
edit, so a failed attempt leaves nothing behind on Play's side. The manual re-run on 2026-10-02
(`gh run rerun <id> --failed`) proved that simply running the same step again is enough. See
proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Survive Play API outages of a few minutes without human action.
- Keep the job's failure semantics: the job still fails, with Play's message, when every attempt
  fails.

**Non-Goals:**
- Classifying errors as transient or permanent (the action exposes no machine-readable reason).

## Decisions

### D1 — Repeat the step in the workflow instead of a retry wrapper action
Three copies of the upload step (`id: play-upload-1..3`). Attempts 1 and 2 run with
`continue-on-error: true`; attempt 2 runs only `if: steps.play-upload-1.outcome == 'failure'`,
attempt 3 only if attempt 2's outcome is `failure`. Attempt 3 has no `continue-on-error`, so its
failure fails the job exactly as today. The `with:` block is defined once as a YAML anchor on
attempt 1 (`with: &play-upload-inputs`) and reused by attempts 2 and 3 (`with: *play-upload-inputs`),
which GitHub Actions supports; should the runner or the linter reject the anchor, the block is
copied verbatim instead and a comment marks the copies as needing to stay identical.

Alternatives: a generic retry wrapper action (e.g. `Wandalen/wretry.action`) — one step, but a new
third-party action that receives the Play credentials path and wraps another action, plus its own
version to maintain; a custom script against the Play Developer API — replaces a working action
for no gain. Repeating the step uses only built-in GitHub Actions features.

### D2 — Pauses of 1 and 3 minutes, announced as warnings
Before attempt 2 a `run:` step sleeps 60 s, before attempt 3 one sleeps 180 s; each runs under the
same condition as the attempt it precedes and first prints
`::warning::Google Play upload attempt N failed, retrying in S s`. Total extra wait ≤ 4 min, well
inside the OIDC credentials' 1 h lifetime, so no re-authentication is needed. The 2026-10-02
outage was over by the manual re-run; minutes-long outages are the case worth covering, longer
ones still need a human.

### D3 — `steps.changes.outputs.changed` stays the outer guard
Every new step keeps `steps.changes.outputs.changed == 'true'` in its `if:` alongside the outcome
check, so backend-only pushes keep skipping the whole chain.

## Risks / Trade-offs

- [Play commits the edit but the response is lost] → the retry fails with "versionCode already
  used"; the release is on the track anyway and the red job is a false alarm. Rare; acceptable, and
  the warning plus message make it diagnosable.
- [Permanent refusals now take ~4 min longer to fail] → acceptable for a job nobody waits on.
- [The three attempts drift apart] → one anchored `with:` block; only `id`, `if` and
  `continue-on-error` differ between the copies.
- [Retry path cannot be exercised without a real Play failure] → verified by YAML review and a
  workflow lint; the success path is verified on the next release (attempts 2/3 and pauses skipped).
