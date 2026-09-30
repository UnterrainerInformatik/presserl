# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M8 — Google Play publishing
Publish the Android app (android-app-qr-login) on Google Play: store listing (de/en, screenshots,
icon), Families policy and target-audience questionnaire, data safety form, the mandatory closed
test for new personal developer accounts (12 testers, 14 days), Play App Signing enrolment with the
upload key from `ai/secrets/`, version code handling and a CI bundle build. Privacy policy is
done in android-app-qr-login (`https://unterrainer.info/app/presserl/privacy`).

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

## Homepage: private legal notice, LeRoi's e-mail
The company Unterrainer Informatik OG no longer exists; `unterrainer.info` (homepage repo
`/mnt/data/source/JAVASCRIPT/homepage`, deployed by pushing `master`) must present Gerald as a private
person. Gerald's decisions (2026-09-30):
- **Legal notice** (`src/locales/parts/about_{de,en}.ts`, key `impressum`): only Gerald Unterrainer
  as a private person, with the name "Unterrainer Informatik" **without "OG"**; address Flurstraße 17,
  4470 Enns (already live); contact e-mail. Remove everything of the company: "Offene Gesellschaft",
  UID-Nr ATU66981117, FN 374582 g, Landesgericht Steyr, WKÖ/WKOÖ membership, Bezirkshauptmannschaft
  Linz-Land, the shareholders (Gerald 50 %, Günter 50 %) and the company purpose. Keep only what
  Austrian law requires of a private website: the disclosure under § 25 Mediengesetz (name, place of
  residence; for a site beyond personal presentation also ownership and basic orientation). The
  site is **not commercial, private only** (Gerald, 2026-09-30), so no § 5 ECG details. Gerald
  (2026-09-30): "Unterrainer Informatik" is the name of his sole-trader business and stays.
- **Logo:** the header image `src/assets/logo.png` reads "UNTERRAINER INFORMATIK OG" — Gerald
  wants "OG" removed there too (image file, not text).
- **About us** (`members` in `about_{de,en}.ts`): remove LeRoi's e-mail (`leroi@unterrainer.info`)
  only; keep his entry, name and description as they are. `src/components/peopleStream.vue` builds
  both the `mailto:` link and the Gravatar from `mail`, so it must show an entry without a mail
  (no link, a neutral avatar).
- The presserl app's privacy policy refers to this legal notice; recheck that its controller line
  still fits afterwards.

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
