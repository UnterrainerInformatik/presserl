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

## Admin app on phones — distinguish "unreachable" from "not a presserl server"
Found in the real-phone check of android-app-qr-login (2026-09-30): `ConnectionModel.check`
catches every failure of `GET <base>/api/client-config` and shows `NO_PRESSERL_SERVER` ("no
presserl newspaper found at this address"), and it logs nothing. On the phone the cause was
the wrong VPN: DNS returned the public address, where the proxy answers `404`. The screen
looked exactly as it would for a wrong address, and only `adb` checks (DNS, `nc`) found the cause.
- Show a separate error when the server cannot be reached (DNS failure, connection refused or
  timeout, TLS error), e.g. "Server not reachable — check network/VPN", and keep
  `NO_PRESSERL_SERVER` for a reachable server that answers without a valid client config (4xx,
  non-JSON, missing OIDC fields).
- Log the cause (exception class and message, HTTP status; never credentials) so `adb logcat`
  shows it.
- Decide at propose time whether the error names the address it tried.
- Extend `ConnectionModelTest` (ktor-client-mock: thrown IO exception vs. `404`).

## Reader: section filter and front-page weighting
Gerald's idea (2026-09-30), two parts:
- **Section filter:** the section tags in the reader are clickable and filter the article list to
  that section. At most one section is active; clicking the active tag again clears the filter and
  shows all articles again. Decide at propose time: URL form (e.g. `/?section=<id>`) so a filtered
  view can be linked, behaviour on issue pages and in private newspapers.
- **Front page weighting ("Titelseite"):** articles get an integer weight; the smaller the number,
  the higher up. Weighted articles come first, above everything else — in the current layout the
  first is the lead story and the next three are shown below it (one lead plus three), before the
  page starts to scroll. Unweighted articles follow as today (newest first publication first).
  Decide at propose time: who may set the weight (editor-in-chief, publisher, section editors?),
  whether it belongs to the newspaper or to an issue, what happens to a weight when the article
  goes offline or leaves the issue, and how weighting and the section filter combine.
- Relates to "Articles of a not-live issue are visible on the front page" below.

## Reader: legal notice for the deployment (alexpresse.net)
The reader needs a legal notice (Impressum/Offenlegung) under Austrian law that each deployment
sets itself — a template in `deploy/` (e.g. a file next to the theme or `.env` values), the real
content in the deployment repositories (`../presserl-deployment`, `../alexpresse`); a link in the
reader (e.g. footer) to a page `/impressum` or similar. For `alexpresse.net`: Unterrainer
Informatik, Gerald Unterrainer, Flurstraße 17, 4470 Enns — with only the legal minimum. Gerald's
site is private and not commercial, so § 25 Mediengesetz (disclosure: name, place of residence;
for a newspaper that goes beyond personal presentation also ownership and basic orientation /
"Blattlinie") applies, § 5 ECG does not. Decide at propose time: whether the street is needed at
all (§ 25 MedienG asks for the place of residence only), the "Blattlinie" text, and the same
trade-name question as for the homepage (see "Homepage: private legal notice").

## Articles of a not-live issue are visible on the front page
Seen on alexpresse.net (2026-09-30): an issue without publication date and not live, yet its
approved articles show on the front page. This is the specified behaviour, not a code bug:
`reader-articles` lists every `PUBLISHED` article on `/` (and serves `/articles/{id}`), and
`reader-issues` lets the live switch govern only `/issues/{id}`, `/issues` and the masthead's issue
line ("The front page's article list itself SHALL stay as it is"). Approving therefore publishes at
once; the issue only groups. Gerald expected the issue's live switch to hold its articles back.
Decide at propose time:
- Should an article that belongs to a not-live issue stay off the front page and answer `404` on
  its article page (and print view) until the issue goes live? What about articles in no issue
  (blog mode) — immediately visible as today?
- What does the admin app show then (e.g. "published, waits for issue 2")? Section article counts
  (`articleCounts.live`) would need the same rule.
- Fits together with the front-page weighting / section filter idea (lead story plus three below).

## M8 — iOS (later)
iOS target of the admin app with the same QR login as Android; App Store publishing. Blocked
until an Apple developer account exists — not before the Android part is done.

## Homepage: private legal notice, LeRoi's e-mail
The company Unterrainer Informatik OG no longer exists; `unterrainer.info` (homepage repo
`~/source/private/js/homepage`, deployed by pushing `master`) must present Gerald as a private
person. Gerald's decisions (2026-09-30):
- **Legal notice** (`src/locales/parts/about_{de,en}.ts`, key `impressum`): only Gerald Unterrainer
  as a private person, with the name "Unterrainer Informatik" **without "OG"**; address Flurstraße 17,
  4470 Enns (already live); contact e-mail. Remove everything of the company: "Offene Gesellschaft",
  UID-Nr ATU66981117, FN 374582 g, Landesgericht Steyr, WKÖ/WKOÖ membership, Bezirkshauptmannschaft
  Linz-Land, the shareholders (Gerald 50 %, Günter 50 %) and the company purpose. Keep only what
  Austrian law requires of a private website: the disclosure under § 25 Mediengesetz (name, place of
  residence; for a site beyond personal presentation also ownership and basic orientation). The
  site is **not commercial, private only** (Gerald, 2026-09-30), so no § 5 ECG details. Still to
  confirm: whether the trade name without a registered company is fine to use.
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
