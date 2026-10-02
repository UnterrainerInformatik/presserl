# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M8 — Play production release
Follows android-play-publishing (internal releases from CI since 0.0.44). Find 12 testers
(Google accounts), create the closed testing track with them (e-mail list or Google Group) and
promote the current internal release to it — this submits the app for its first review, after
which the store page shows the real icon and listing; after 14 consecutive days with at least 12
opted-in testers apply for production access in the Play Console (questions on the test), then roll
out to production and switch the pipeline's track if releases should go there directly.

## M8 — iOS (later)
iOS target of the admin app with the same QR login as Android; App Store publishing. Blocked
until an Apple developer account exists — not before the Android part is done.

## Callers still pinning Node 20 action majors
`init-runner-action` checks out with `actions/checkout@v7` since init-runner-action-node24, but
callers still pin Node 20 majors themselves and keep emitting the deprecation annotation:
`npm-build-workflow` (`setup-node@v4`, `upload-artifact@v4`, `reedyuk/npm-version@1.1.1`), `maven-central-workflow`
(`cache@v3`, `setup-java@v4`, `upload-artifact@v4`) and the pipelines' own cache steps
(`cms-keycloak`, `htl-zeromq`, `java-cms-data-logger`, `java-elite-server`,
`java-overmind-server` — check each). Raise to the current majors (upload-artifact v7,
setup-node v7, setup-java v6, cache v6); separate repositories, verify each with a real run.
