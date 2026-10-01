## Why

Google Play requires every app that lets users create accounts from within the app to let users
request deletion of their account — in the app and through a web link entered in the Data safety
form (<https://support.google.com/googleplay/android-developer/answer/13327111>). In presserl the
newsroom creates accounts in the admin app, but there is no way to delete one, only to lock it.
This blocks the Play review of android-play-publishing (see `docs/play-console.md`, "Open points"),
and it is the missing half of the right to erasure the privacy policy already promises.

## What Changes

- **Request deletion of one's own account:** every logged-in user gets a "My account" screen in the
  admin app (web and Android) with "Request account deletion" and "Withdraw request". The request
  is stored; nothing is deleted yet. Publishers decide — parents stay in charge of their children's
  accounts.
- **Delete an account:** publishers can delete accounts in the account list, with a confirmation.
  Same scope as locking (not their own, not other publishers) with one addition: a publisher may
  delete another publisher's account when that publisher has requested deletion, as long as at
  least one other enabled publisher remains. Accounts with a pending request are marked and listed
  first.
- **Content is kept, anonymised:** the deleted account's articles (all states), revisions, reviews
  and images stay; every name snapshot of the account (byline, revision author, reviewer, uploader)
  is replaced by a neutral "former newsroom member". Its section roles, sectionless-reporter marker,
  trust entries and deletion request are removed, pending submissions are withdrawn, and the
  Keycloak user is deleted (which ends its sessions).
- **REST:** `POST`/`DELETE /api/me/deletion-request`, `GET /api/me` reports a pending request,
  `DELETE /api/accounts/{id}`, `AccountDto` gains `deletionRequestedAt`, `allowedActions` gains
  `DELETE`. Primer, admin API client and `.http` files follow.
- **Reader page** `GET /account-deletion` on every newspaper: how to request deletion in the app
  or by contacting the newspaper's operator (linking the legal notice when there is one), and what
  happens to content. Linked from the legal notice page.
- **Homepage page** `https://unterrainer.info/app/presserl/account-deletion` (German/English, in
  the homepage repository): the one URL Play needs — every newspaper is run by its own operator,
  how to request deletion in the app or through the operator, and that the developer holds no
  accounts. Privacy policy section "Your rights" links it.
- `docs/play-console.md`: Data safety deletion answer and account-deletion URL; "Open points" entry
  closed.

## Non-goals

- Deleting content together with the account (the publisher can still take articles offline or
  edit images as before).
- Self-service deletion without a publisher, e-mail notifications to publishers, automatic
  deletion deadlines.
- Deleting publisher accounts when no other publisher exists (that stays with the operator in the
  Keycloak admin console, documented in `deploy/INSTALL.md`).
- Removing names that other people typed into review notes or article texts.
- Fixing Keycloak's "update profile" prompt for accounts without a last name (separate issue).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `accounts`: deletion requests on one's own account, deleting accounts, anonymising the deleted
  account's name snapshots, `DELETE` in `allowedActions`, `deletionRequestedAt` in the account list.
- `admin-accounts`: "My account" screen with request/withdraw, deletion requests marked in the
  account list, delete action with confirmation.
- `admin-shell`: the header's user line opens "My account" and marks a pending request.
- `reader-legal-notice`: public account deletion page per newspaper, linked from the legal notice.

## Impact

- **backend:** account resource/service/policy (delete, request), new table for deletion requests
  (migration V18), anonymisation over `article`, `article_revision`, `article_review`, `media`,
  cleanup of `section_role`, `sectionless_reporter`, `trust`; `GET /api/me`; reader page + template
  + messages (de/en).
- **admin:** API client, "My account" screen and header entry, account list marker and delete
  dialog; German/English strings; works the same on web and Android.
- **reader:** new page `/account-deletion`, link on `/legal-notice`.
- **docs / ai:** `ai/primer/endpoints.md`, `http/` request files, `docs/roles-and-workflow.md`
  (deletion next to locking), `docs/play-console.md`, `deploy/INSTALL.md` (deleting the last
  publisher).
- **External:** homepage repository (`../../JAVASCRIPT/homepage`): account deletion page and privacy
  policy link.
- **deploy:** none (the backend's service account already holds `manage-users`).
