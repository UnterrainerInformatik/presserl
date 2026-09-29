# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M8 — Mobile & QR
Android/iOS targets of the admin app; the app reads the account slip's QR code
(`<base>/qr?u=<username>#pw=<pass-phrase>`: `<base>` = server address, `u` = username, `pw` =
pass-phrase) and logs in without typing; store publishing (Google Play Families policy, Apple
developer account). The slip's QR code itself is done (qr-slip-login); no token exchange.

## Photo-reporter role
A new newspaper-wide role for someone who takes pictures but has no section (or whose sections are
irrelevant): may upload images, use the media view ("Images") and edit their own uploads under the
same rule as other uploaders (only while no live revision and no pending submission uses them); does
not write articles. Assigned from editor-in-chief upward. Needs a Keycloak group, delegation and
password-reset rules, the account form, the realm template, and decoupling the media endpoints and
the "Images" header entry from `WRITE_ARTICLES` (own action). Builds on media-view-crop-blur.

## init-runner-action on Node 24
`UnterrainerInformatik/init-runner-action` (used by every shared workflow, incl.
docker-build-workflow) still checks out with `actions/checkout@v4` and pre-fetches v4/v3 actions,
which GitHub forces onto Node 24 with a deprecation annotation. Raise to the current majors
(checkout v7 at the time of ci-build-speed). Lives outside this repo; affects all callers.
