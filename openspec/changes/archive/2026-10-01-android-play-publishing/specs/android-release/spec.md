## Purpose

How the Android admin app is versioned, built, signed and delivered to Google Play, and what the
repository keeps for its store listing and the Play Console forms.

## ADDED Requirements

### Requirement: App version follows the release version
A release build of the Android app SHALL carry the release version `X.Y.Z` that the pipeline
assigns to the commit as its `versionName`, and `X * 10_000_000 + Y * 100_000 + Z` as its
`versionCode`, so that every later release version has a higher `versionCode`. A build without a
release version (a local build) SHALL use the `versionName` `0.0.0-local` and `versionCode` `1`.
A release version whose minor part exceeds 99 or whose patch part exceeds 99_999 SHALL fail the
build instead of producing a `versionCode` out of order.

#### Scenario: Pipeline release
- **WHEN** the pipeline builds the release bundle for release version `0.0.42`
- **THEN** the bundle has `versionName` `0.0.42` and `versionCode` `42`

#### Scenario: Minor version raises the code
- **WHEN** release `0.1.0` follows release `0.0.512`
- **THEN** `0.1.0` has `versionCode` `100000`, higher than `512`

#### Scenario: Local build
- **WHEN** a developer runs the release build without a release version
- **THEN** the bundle has `versionName` `0.0.0-local` and `versionCode` `1`

#### Scenario: Version out of range
- **WHEN** the release version is `0.100.0`
- **THEN** the build fails with a message naming the version and the allowed range

### Requirement: Pipeline delivers app changes to the internal track
For every push to `master` that changes files under `admin/`, and for every manual pipeline run,
the pipeline SHALL build the Android release bundle, sign it with the upload key and upload it,
together with its deobfuscation mapping, to the Google Play **internal** test track. Pushes that
do not change `admin/` SHALL NOT produce an Android release. A failed build or upload SHALL fail the
pipeline run and SHALL NOT affect the image build or the staging deployment. The pipeline SHALL
NOT promote a release to the closed, open or production track.

#### Scenario: Admin change
- **WHEN** a push to `master` changes `admin/composeApp/src/commonMain/kotlin/App.kt`
- **THEN** the pipeline uploads a bundle with the push's release version to the internal track, and internal testers can install it from Google Play

#### Scenario: Backend-only change
- **WHEN** a push to `master` changes only files under `backend/`
- **THEN** the pipeline builds and deploys the image and uploads no Android bundle

#### Scenario: Upload refused
- **WHEN** Google Play refuses the upload (for example a `versionCode` that already exists)
- **THEN** the Android job fails with Google Play's message, and the image build and staging deployment still complete

### Requirement: Signing and Play credentials stay out of the repository
The upload keystore and its passwords SHALL NOT be committed to any repository. The pipeline SHALL
read them from repository secrets and SHALL NOT print them to the job log. The pipeline SHALL
authenticate to Google Play with short-lived credentials obtained for the job (no long-lived
service account key exists), granted only to pipeline runs of this repository's `master` branch.
Files written from secrets or credentials during the job SHALL be removed when the job ends,
whether it succeeds or fails.

#### Scenario: Job log
- **WHEN** the Android job runs, successfully or not
- **THEN** its log contains neither keystore content, passwords nor Google credentials, and no keystore or credentials file is left in the runner's workspace afterwards

### Requirement: Store listing is kept in the repository
The repository SHALL keep the Google Play store listing of the app in German and English: title
(at most 30 characters), short description (at most 80 characters), full description (at most
4000 characters), and the graphics Google Play requires — a 512×512 PNG icon, a 1024×500 feature
graphic and at least four phone screenshots of the current app. Texts and graphics in the Play
Console SHALL match these files. The listing SHALL describe what the app does without promising
features the app lacks and SHALL name that a presserl newspaper server and an account from its
newsroom are needed.

#### Scenario: Listing limits
- **WHEN** the listing files are checked
- **THEN** every text stays within Google Play's length limit and every graphic has the required size and format

#### Scenario: Requirement stated
- **WHEN** a parent reads the store listing
- **THEN** the description says that the app needs a presserl newspaper and an account slip from that newspaper's newsroom

### Requirement: Play Console answers are documented
The repository SHALL document the answers given in every Play Console declaration — target audience
and content, Families policy, data safety, content rating, ads, app access, privacy policy URL,
government/financial/health declarations — together with the reasoning for each answer, so that
the declarations can be checked against the app's behaviour when it changes. The data safety
answers SHALL match the privacy policy at `https://unterrainer.info/app/presserl/privacy`.

#### Scenario: App starts sending data
- **WHEN** a later change makes the app send data somewhere new
- **THEN** the documented data safety answers show which declaration has to change with it

#### Scenario: Data safety matches the privacy policy
- **WHEN** the documented data safety answers are compared with the privacy policy
- **THEN** both name the same data, the same recipients and the same deletion options

### Requirement: Reviewers can use the app
Google Play's reviewers SHALL be given a working way into the app: the address of a newspaper
server reachable from the internet and the credentials of an account on it (as text and as the
slip's QR code), with the permissions of a reporter. The account SHALL NOT be able to publish
anything on its own.

#### Scenario: Reviewer logs in
- **WHEN** a reviewer outside the developer's network enters the given address and credentials, or scans the given QR code
- **THEN** the app logs in and shows "My articles"

#### Scenario: Reviewer writes an article
- **WHEN** the reviewer creates an article and submits it
- **THEN** the article waits for approval and does not appear in the newspaper
