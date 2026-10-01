# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M8 — Play production release
Follows android-play-publishing (closed track prepared, internal releases from CI). Find 12 testers
(Google accounts) and add them to the closed track; after 14 consecutive days with at least 12
opted-in testers apply for production access in the Play Console (questions on the test), then roll
out to production and switch the pipeline's track if releases should go there directly.

## Admin app on phones — layout polish
Non-blocking findings of the phone-size check in android-app-qr-login (Pixel 7 emulator, 412 dp):
- The header (newspaper, user, navigation) and the editor's bottom bar (undo/redo/saved/delete,
  publish) stay fixed and take about 40 % of the height; with the keyboard open only a few lines
  of the paragraph being written are visible. Collapse the header while scrolling or typing and
  shrink the bottom bar when the keyboard is shown.
- The navigation row scrolls sideways and cuts the last entry ("Acc…") without a hint that more
  follows; a menu or wrapping row would show all entries.
- The issue publication date is typed as `YYYY-MM-DD`; a date picker suits phones better.
- The photo picker offers every image type the phone has (e.g. HEIC); the server refuses
  anything but JPEG, PNG and WebP with the usual upload error — check whether converting to JPEG
  on the device is worth it.

## M8 — iOS (later)
iOS target of the admin app with the same QR login as Android; App Store publishing. Blocked
until an Apple developer account exists — not before the Android part is done.

## Shared npm-build-workflow ignores the lockfile
`UnterrainerInformatik/npm-build-workflow` runs `rm -rf node_modules package-lock.json && npm install`
before every build, so each build takes the newest versions allowed by `package.json`. On
2026-09-30 this broke `unterrainer.info` completely (a newer `on-headers`, pulled in by a
`compression` import in the browser app, read `http.ServerResponse` at load time; fixed in the
homepage by dropping the import). Builds should use `npm ci` with the committed lockfile, and
resolve platform-native dependencies another way if that was the reason. Lives outside this repo;
affects every npm caller.

## Shared deploy-workflow lands on runners that cannot reach the server
`UnterrainerInformatik/deploy-workflow` has no `runs-on` input and runs on any self-hosted runner.
On 2026-09-30 the homepage deploy's SSH to the server timed out three times from `dev1-runner*`
and succeeded from `babylon5-runner*` (VPN step skipped in both cases). Add a `runs-on` input
(as docker-build-workflow has) or fix the route from dev1. The homepage pipeline also passes
`ovpn_enabled: true` while its repository holds a `WG_CONFIG` secret and no OpenVPN secrets —
check which VPN is meant.

## init-runner-action on Node 24
`UnterrainerInformatik/init-runner-action` (used by every shared workflow, incl.
docker-build-workflow) still checks out with `actions/checkout@v4` and pre-fetches v4/v3 actions,
which GitHub forces onto Node 24 with a deprecation annotation. Raise to the current majors
(checkout v7 at the time of ci-build-speed). Lives outside this repo; affects all callers.
