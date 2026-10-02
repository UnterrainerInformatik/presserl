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

## init-runner-action on Node 24
`UnterrainerInformatik/init-runner-action` (used by every shared workflow, incl.
docker-build-workflow) still checks out with `actions/checkout@v4` and pre-fetches v4/v3 actions,
which GitHub forces onto Node 24 with a deprecation annotation. Raise to the current majors
(checkout v7 at the time of ci-build-speed). Lives outside this repo; affects all callers.
