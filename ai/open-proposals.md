# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M8 — Mobile & QR
Android/iOS targets of the admin app; the app reads the account slip's QR code
(`<base>/qr?u=<username>#pw=<pass-phrase>`: `<base>` = server address, `u` = username, `pw` =
pass-phrase) and logs in without typing; store publishing (Google Play Families policy, Apple
developer account). The slip's QR code itself is done (qr-slip-login); no token exchange.

## init-runner-action on Node 24
`UnterrainerInformatik/init-runner-action` (used by every shared workflow, incl.
docker-build-workflow) still checks out with `actions/checkout@v4` and pre-fetches v4/v3 actions,
which GitHub forces onto Node 24 with a deprecation annotation. Raise to the current majors
(checkout v7 at the time of ci-build-speed). Lives outside this repo; affects all callers.
