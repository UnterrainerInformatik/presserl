## MODIFIED Requirements

### Requirement: Pipeline delivers app changes to the internal track
For every push to `master` that changes files under `admin/`, and for every manual pipeline run,
the pipeline SHALL build the Android release bundle, sign it with the upload key and upload it,
together with its deobfuscation mapping, to the Google Play **internal** test track. Pushes that
do not change `admin/` SHALL NOT produce an Android release. When an upload attempt fails, the
pipeline SHALL wait and try again, up to three attempts in total, and SHALL mark every failed
attempt that is followed by another one with a warning on the pipeline run. Only when the last
attempt fails SHALL the upload count as failed. A failed build or upload SHALL fail the pipeline
run and SHALL NOT affect the image build or the staging deployment. The pipeline SHALL NOT promote
a release to the closed, open or production track.

#### Scenario: Admin change
- **WHEN** a push to `master` changes `admin/composeApp/src/commonMain/kotlin/App.kt`
- **THEN** the pipeline uploads a bundle with the push's release version to the internal track, and internal testers can install it from Google Play

#### Scenario: Backend-only change
- **WHEN** a push to `master` changes only files under `backend/`
- **THEN** the pipeline builds and deploys the image and uploads no Android bundle

#### Scenario: Google Play briefly unavailable
- **WHEN** the first upload attempt fails because Google Play answers `The service is currently unavailable.` and a later attempt succeeds
- **THEN** the release reaches the internal track, the Android job succeeds, and the run shows a warning naming the failed attempt

#### Scenario: Upload refused
- **WHEN** Google Play refuses the upload on every attempt (for example a `versionCode` that already exists)
- **THEN** the Android job fails after the third attempt with Google Play's message, and the image build and staging deployment still complete
