## Why

The `Upload to Google Play (internal)` step tries exactly once. On 2026-10-02 release 0.0.46
uploaded fine, but committing the Play edit failed with `The service is currently unavailable.`;
the edit was discarded and nothing reached the internal track until the job was re-run by hand.
A short Google outage should not leave testers' phones on the previous version unless someone
notices the red job.

## What Changes

- The Play upload in `.github/workflows/pipeline.yml` is attempted up to three times, with a pause
  between attempts, before the Android job fails.
- A failed attempt that is followed by a retry is surfaced as a warning annotation on the run, so a
  flaky Play API stays visible even when the release finally lands.
- The backlog entry "Retry the Play upload on transient errors" is removed from
  `ai/open-proposals.md`.

## Non-goals

- Distinguishing transient from permanent Play errors — a permanent refusal (e.g. a duplicate
  `versionCode`) is retried too and fails after the last attempt with Google Play's message.
- Retrying other steps (Gradle build, Google Cloud authentication) or the image/staging jobs.
- Changing the track, release status or anything else about what is uploaded.
- The shared `deploy-workflow` / `init-runner-action` backlog items.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `android-release`: "Pipeline delivers app changes to the internal track" gains retries of the
  upload on failure before the job fails.

## Impact

- Platforms: CI only (`.github/workflows/pipeline.yml`, Android job). Backend, reader, admin code,
  deploy and REST contract are untouched.
- Runtime: a failing upload now holds the Android job for a few extra minutes before it fails; the
  image build and staging deployment are independent and unaffected.
- `ai/open-proposals.md`: backlog entry removed.
