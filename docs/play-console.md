# Google Play Console — answers and release flow

The answers given in every Play Console declaration for the Android admin app
(`info.unterrainer.presserl`), each with its reason. When a change makes the app send data
somewhere new, request a new permission or add an SDK, check the sections below and change the
declaration in the Console together with this file.

Store listing texts and graphics: `admin/androidApp/play/` (check with `admin/androidApp/play/check.sh`).
Privacy policy: <https://unterrainer.info/app/presserl/privacy> (source in the homepage repository,
`src/locales/parts/presserlPrivacy_{de,en}.ts`).

Checked against Google's pages on 2026-10-01; form wording in the Console may differ slightly.

## What the app does, as far as the declarations are concerned

- Permissions in the release manifest: `INTERNET`, `ACCESS_NETWORK_STATE` (from Google Play
  services), and the app-internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No camera, storage,
  location, contacts or advertising ID (`AD_ID`) permission.
- SDKs: Jetpack/Compose libraries, Ktor, and `com.google.android.gms:play-services-code-scanner`
  (Google code scanner, ML Kit via Google Play services). No ads, analytics, crash-reporting or
  Firebase SDK.
- Network: only to the newspaper server the user chose (scanned slip or typed address), `https`
  only in release builds; the server's Keycloak login page is shown in the app.
- On the device: the newspaper address and — after a QR login — username and password, encrypted
  with an Android Keystore key, excluded from backups; deleted on logout or uninstall.
- Photos: only those picked in the system photo picker or taken with the camera app; uploaded to
  the newspaper server, which strips all metadata (EXIF, GPS) before storing.

## Families policy and the code scanner

**Result: allowed; the metrics are declared in Data safety and named in the privacy policy.**

What Google says (2026-10-01):

- Families policy (<https://support.google.com/googleplay/android-developer/answer/9893335>):
  apps whose audience includes children "must not contain any APIs or SDKs that are not approved
  for use in primarily child-directed services"; mixed-audience apps may use such SDKs only behind a
  neutral age screen or "in a way that does not result in the collection of data from children".
  No ads unless through Families self-certified ads SDKs; no transmission of AAID, SIM serial,
  build serial, BSSID, MAC, SSID, IMEI, IMSI from children; no precise location.
- Data practices in Families apps (<https://support.google.com/googleplay/android-developer/answer/11043825>):
  the self-certification list exists for **ads** SDKs only; Google Play services get no exemption,
  and Google publishes no list of "approved" non-ads SDKs.
- Google code scanner (<https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner>):
  "All image processing occurs on the device and Google doesn't store the results or image data";
  the app needs no camera permission.
- ML Kit data disclosure (<https://developers.google.com/ml-kit/android-data-disclosure>): ML Kit
  APIs send Google device information (manufacturer, model, OS version), package name and app
  version, "a device identifier used for diagnostics and usage analytics" and per-installation
  identifiers, performance metrics, API configuration, input/output size, event types and error
  codes; encrypted in transit (HTTPS), not transferred to third parties.
- ML Kit terms (<https://developers.google.com/ml-kit/terms>): "You are responsible for informing
  users of your app about Google's processing of ML Kit metrics data as required by applicable law."

Assessment:

- The code scanner is not an ads SDK and transmits none of the identifiers the Families policy
  lists (no AAID, IMEI, MAC …); images never leave the device. Nothing in the policy forbids it.
- It does send usage metrics with a diagnostics device identifier to Google. That is data
  collection through an SDK and has to be declared in Data safety (below) and named in the privacy
  policy — done in the policy of 2026-10-01 (section "Scanning QR codes": data categories, purpose,
  encryption, no third parties, and that logging in by address avoids the scanner).
- There is no way to switch ML Kit's metrics off (open feature request
  googlesamples/mlkit#593).
- Fallback if a reviewer objects: the address field logs in without scanning, so the app stays
  usable; replacing the scanner (bundled ML Kit still sends metrics; ZXing-based scanning does not,
  but needs the camera permission) would be its own change.

## Target audience and content

- **Target age groups:** 9–12, 13–15, 16–17, 18 and over. Not 5 and under, not 6–8.
  *Reason:* children from school age on write for the family or school newspaper; adults
  (parents, teachers) run the newsroom. Six- to eight-year-olds can write too, but the app is not
  designed for them (reading-heavy forms); leaving 6–8 out keeps the app out of the "primarily
  child-directed" range.
- **Appeal to children:** "The app is not designed to appeal primarily to children" — it is a
  newsroom tool without characters, games or child-oriented graphics. Mixed audience: the
  Families policy still applies to the child users.
- **Designed for Families programme:** not joined. *Reason:* not primarily child-directed; the
  programme adds review requirements without benefit for a tool that needs a newspaper account.
- **Store listing matches these answers:** no age range and no "made for children" wording in the
  listing texts or feature graphics (the vision's 6–16 is not a Play claim); Play compares the
  listing with the target audience, and "from 6" against a declared 9+ is a Families rejection
  reason.
- **Expert Approved programme (Kids tab):** "Do not include my app". *Reason:* same as above — the
  app is not primarily for children and is useless without an invitation to a newspaper, so a
  Kids-tab listing would only attract installs that cannot sign in.
- **Neutral age screen:** none. *Reason:* the app collects nothing from children for the
  developer; the only SDK collection is the code scanner's metrics (see above).

## Ads and purchases

- **Contains ads:** No. *Reason:* no ads SDK, no ad content (product principle: no advertising).
- **In-app purchases / subscriptions:** none; the app is free.

## Data safety

Play counts as "collected" all data the app transmits off the device, including through SDKs and
to servers the developer does not run. The exemption for service providers does not fit (the
newspaper operator is not working for the developer), the user-initiated-action exemption covers
only *sharing*. So the answers declare what goes to the newspaper server and what the code scanner
sends, and state that the developer receives nothing.

- **Does the app collect or share any of the required user data types?** Yes.
- **Is all user data encrypted in transit?** Yes — release builds connect only via `https`; ML Kit
  uses HTTPS.
- **Do you provide a way for users to request that their data is deleted?** Yes — on the device by
  logging out or uninstalling; for the account: in the app under *My account* → *Request account
  deletion*, which the newspaper's publishers act on (they delete the account; its articles and
  images stay under "former newsroom member"), or by asking the newspaper's publishers or operator.
- **Account deletion URL** (Data safety → Data deletion): <https://unterrainer.info/app/presserl/account-deletion> — explains that every newspaper is
  run by its own operator, how to request deletion in the app or through the operator, links each
  newspaper's `/account-deletion` page and states that the developer holds no accounts.

Collected data types (none is **shared** with third parties by the developer; nothing is used for
advertising or marketing; nothing is processed ephemerally only):

| Data type | Why it leaves the device | Required / optional | Purpose |
|---|---|---|---|
| Personal info → **Name** | first/last name when a newsroom member creates or edits accounts, stored on the newspaper server | optional (only for account administrators) | App functionality, Account management |
| Personal info → **User IDs** | username and password for logging in on the newspaper server | required | App functionality, Account management |
| Photos and videos → **Photos** | pictures the user uploads into articles | optional | App functionality |
| App activity → **Other user-generated content** | articles, captions, review notes | required (the app's purpose) | App functionality |
| App info and performance → **Diagnostics** | ML Kit usage metrics of the code scanner (latency, error codes, API configuration) sent to Google | optional (only when scanning) | Analytics (Google: improve and maintain the API) |
| Device or other IDs → **Device or other IDs** | ML Kit's diagnostics device identifier / per-installation identifier sent to Google | optional (only when scanning) | Analytics |

Not collected: location (GPS is stripped server-side; the app has no location permission),
contacts, e-mail address, phone number, financial info, health, messages, audio, files and docs,
calendar, web browsing, installed apps, crash logs (no crash reporting), advertising ID.

## Content rating (IARC questionnaire)

- **Category:** Reference, News, or Educational → answer the questionnaire as a tool/utility app
  (the IARC category list has no "productivity" choice that fits better).
- **Violence, sexual content, language, controlled substances, gambling:** No — the app ships no
  such content.
- **User-generated content / users can interact or exchange content:** Yes. Content written in the
  app is shared with other users of the same newspaper and, once approved, published in the
  newspaper's reader. *Moderation:* every article waits for approval by section editor, editor-in-chief
  or publisher; publishers can take any article offline at once; there is no chat and no comments
  from strangers; accounts are created by the newsroom only (no self-registration).
- **Shares the user's location:** No. **Digital purchases:** No. **Unrestricted internet access /
  web browser:** No — the app talks to the chosen newspaper server only (the reader opens in the
  phone's browser).

### Issued ratings

Issued on 2026-10-01 for the answers above:

| Authority (region) | Rating |
|---|---|
| **PEGI (Europe, including Austria)** | **Parental guidance** |
| USK (Germany) | Ages 6+ |
| ESRB (North America) | Everyone 10+ |
| ClassInd (Brazil) | All ages |
| IARC Generic (rest of the world) | 12+ |
| Google Play Russia | 12+ |
| Google Play South Korea | 12+ |

PEGI is the rating Google Play shows and filters on in Austria and most of Europe.

### Why PEGI says "Parental guidance"

PEGI gives non-game apps whose content comes from their users, and so cannot be rated by age
(social networks, video platforms), "Parental guidance recommended" instead of an age number. The
trigger is the answer **Yes** to "users can interact or exchange content" above. On 2026-10-03 the
submitted questionnaire was checked against this section: every answer is as documented, so PG is
the expected outcome, not a mistake.

The answer stays **Yes**. Reporters' articles, captions and photos are user-generated content
shared with other users of the newspaper; answering **No** to get an age-based PEGI rating would
make the declaration false and risks enforcement by Google.

### Effect on Family Link accounts

A child's Google account supervised by Google Family Link has an app filter ("up to PEGI 3/7/12/16/18").
Every such filter blocks a PG app. Google Play does not name the cause.

Observed on 2026-10-03 with a supervised child account in Austria, internal test track:

1. With the Family Link app filter "up to PEGI 12", installing from the opt-in link failed with
   "An error occurred. Turn on Wi-Fi or mobile data and try again", although the phone was online.
   No approval request reached the parent's Family Link app — parental approval does not help.
2. Setting Google Play approvals to "never" alone changed nothing.
3. With the rating filter and approvals both lifted ("Allow all"), the error changed to
   "Item not found" ("Nicht gefunden"). Clearing the cache of Play Store and Play services and
   opening the exact "Copy link" opt-in link on the phone did not help. A later retry is pending.
4. The same release installs normally on the developer's own (not supervised) account.

**Not verified:** whether supervised accounts can install from a test track (internal or closed) at
all. Third-party developer reports say they cannot; Google's help does not say either way. Until
this is settled, do not count supervised accounts among the 12 closed-test testers.

What operators, parents and teachers are told: [The admin app on children's phones](admin-app.md).

## Install problems

Checklist when someone reports that the admin app cannot be installed from Google Play ("Turn on
Wi-Fi or mobile data", "Item not found", no install button):

- **Tester list:** the person's Google account is on a tester list (e-mail list or Google Group),
  and that list is ticked for the track the release is on (Testing → Internal / Closed testing →
  Testers).
- **Play Store account:** the account the Play Store uses on the phone (Play Store → profile
  picture) is the tester address, not another account on the same phone.
- **Family Link:** if the account is supervised, the PEGI "Parental guidance" rating is blocked by
  any app filter (see [Effect on Family Link accounts](#effect-on-family-link-accounts)); test
  tracks may not work for supervised accounts at all.
- **Opt-in link:** use the link from "Copy link" of the track's testers page, open it on the phone
  with the tester account, accept the invitation, then follow the link to Google Play.
- **Cache:** clear the cache of the Play Store and Google Play services apps, then retry; a fresh
  tester opt-in can take some minutes to reach the Play Store.
- **Last resort, developers only:** download the signed universal APK from Play Console → App
  bundle explorer (the release → Downloads) and install it by hand. Not for operators, parents or
  children: it bypasses Play's updates and Family Link.

## App access (for the review)

- **All or some functionality is restricted:** Yes — everything after the start screen needs an
  account on a presserl newspaper.
- **Instructions:** reviewer account `play-review` on the staging newspaper
  `https://presserl.unterrainer.info` (reporter rights, no trust, section without auto-approval):
  enter the address on the start screen, then username and pass-phrase from the slip — or scan the
  slip's QR code (image attached in the Console). Slip PDF: `ai/secrets/` (not in the repository).
  Every article the reviewer submits waits for approval and is never published by itself.

## Privacy policy

- URL: `https://unterrainer.info/app/presserl/privacy` (the app links it on the start screen with
  `?lang=de` for a German phone language, `?lang=en` otherwise).

## Other declarations

- **News app:** No. *Reason:* presserl is a tool to write a family/school newspaper whose readers
  are invited members; the developer publishes no news, and the newspapers are not news publishers
  in Play's sense (no general news distribution to the public through the app — reading happens in
  the browser).
- **News aggregator:** No. *Reason:* the app collects no content from other publishers; it only
  edits the one newspaper the user has an account on.
- **Government app:** No. **Financial features:** none. **Health:** none / not a health app.
- **COVID-19 contact tracing or status:** No.
- **Data deletion (account deletion requirement):** met by the account-deletion-request change —
  in-app request under *My account*, deletion by the newspaper's publishers, web link
  <https://unterrainer.info/app/presserl/account-deletion>.
- **Foreground service / exact alarm / full-screen intent / photo and video permissions
  declarations:** not applicable — none of these permissions is requested (photos come from the
  system photo picker).
- **Advertising ID declaration:** the app does not use the advertising ID (no `AD_ID` permission).

## Release flow

1. **Repository secrets** (Settings → Secrets and variables → Actions):
   `ANDROID_UPLOAD_KEYSTORE_BASE64` (`base64 -w0 ai/secrets/android-upload.jks`),
   `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD` (values
   from `ai/secrets/android-upload.properties`); repository **variables**
   `GCP_WORKLOAD_IDENTITY_PROVIDER` and `PLAY_SERVICE_ACCOUNT` (see 2). Without them the
   `android-release` job fails before building.
2. **Play API access without a key (Workload Identity Federation):** service account keys are
   disabled by organisation policy (`iam.disableServiceAccountKeyCreation`) and not needed. In the
   Google Cloud project (Cloud Shell): enable `androidpublisher`, `iamcredentials` and `sts` APIs;
   create the service account `play-publisher`; create the workload identity pool `github` with
   the OIDC provider `presserl` (issuer `https://token.actions.githubusercontent.com`, mapping
   `google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref`,
   condition `assertion.repository=='UnterrainerInformatik/presserl' && assertion.ref=='refs/heads/master'`);
   grant `roles/iam.workloadIdentityUser` on the service account to
   `principalSet://iam.googleapis.com/projects/<number>/locations/global/workloadIdentityPools/github/attribute.repository/UnterrainerInformatik/presserl`.
   The provider resource name
   (`projects/<number>/locations/global/workloadIdentityPools/github/providers/presserl`) goes to
   `GCP_WORKLOAD_IDENTITY_PROVIDER`, the service account e-mail to `PLAY_SERVICE_ACCOUNT`. Then
   Play Console → Users and permissions → invite the service account e-mail with release rights
   (release to testing tracks, manage testing tracks) for presserl. Pipeline runs from other
   branches or forks get no token. App permissions belong to one app: a recreated app has to be added
   again, otherwise the upload fails with "The caller does not have permission".
3. **First upload by hand:** the Play Developer API cannot create an app or its first release.
   Create the app in the Console (name "Presserl App", package `info.unterrainer.presserl`, default language German, app, free), download
   the AAB from the workflow artifact `presserl-android-X.Y.Z` of a pipeline run (kept 7 days) and
   upload it to **Testing → Internal testing**. This enrols the app in Play App Signing (Google
   holds the app signing key) and registers our key as upload key.
4. **Drafts until the first rollout:** while the app has never been rolled out, Play accepts only
   draft releases through the API, so the job uploads with status `draft` (default). Roll out the
   first internal release by hand, then set the repository variable `PLAY_RELEASE_STATUS=completed`
   (Settings → Variables). From then on every push that changes `admin/` reaches internal testers
   automatically.
5. **Promotion:** internal → closed testing (track "Closed testing - Alpha", testers as e-mail list
   or Google Group, opt-in link) is a manual "Promote release" in the Console; the pipeline never
   promotes. The 14-day closed test of new personal accounts starts once 12 testers have opted in;
   production access is applied for afterwards (backlog "M8 — Play production release").
6. **Version codes:** `versionName` = release tag `X.Y.Z` of the pipeline's bump job,
   `versionCode = X·10,000,000 + Y·100,000 + Z`. A rerun of the same pipeline run has the same
   version and is refused by Play ("version code already used") — push a new commit or run the
   pipeline manually instead.
7. **Lost upload key:** Play Console → App integrity → request upload key reset (Play App Signing
   keeps the app signing key). Keep `ai/secrets/android-upload.jks` backed up.

## Open points

None.
