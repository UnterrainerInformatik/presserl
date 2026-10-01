## Context

See proposal.md for the why. Current state that shapes the approach:

- Accounts are Keycloak users. The backend's service account already holds `manage-users`;
  `AccountService.deleteOrphan` already calls `realm.users().delete(id)` (rollback of an incomplete
  creation). Locking is `AccountResource` → `AccountPolicy` → `AccountService.setEnabled`.
- Account references in the database are Keycloak subs stored as text, without foreign keys:
  - name snapshots (`*_sub`, `*_username`, `*_display_name`, copied at write time) in `article`
    (V2), `article_revision` (V14, `NOT NULL`), `article_review` (V6) and `media` (V9);
  - bare ids in `section_role` (`account_id`, `assigned_by`), `sectionless_reporter`
    (`account_id`, `assigned_by`) and `trust` (`account_id`, `set_by`).
  The reader byline comes from `article.author_display_name` (falling back to the username) in
  `ReaderArticle`.
- No "my account" area exists; the header has the user line (display name · roles from
  `GET /api/me`) and "Log out" (`App.kt` `Header`).
- The reader's `/legal-notice` (reader-legal-notice) is the pattern for a public reader page; its
  text comes from the theme file only.
- Latest migration: V17.

## Goals / Non-Goals

**Goals:**
- One transaction-like operation that leaves either the account and its names intact, or the
  account gone and every name snapshot anonymised.
- No change to the approval-chain rules beyond withdrawing the deleted author's pending
  submissions.

**Non-Goals:**
- Keycloak realm changes, background jobs, deletion deadlines.
- Rewriting historical log lines (INFO logs keep usernames until log rotation).

## Decisions

### D1 — Deletion requests in their own table
Migration `V18__account_deletion.sql` creates `account_deletion_request(account_id TEXT PRIMARY
KEY, requested_at TIMESTAMPTZ NOT NULL)`, the same shape as `sectionless_reporter`. `GET
/api/accounts` joins it into `deletionRequestedAt`; `GET /api/me` reads it for the caller.
*Alternative:* a Keycloak user attribute — the realm's user profile would have to declare it
(unmanaged attributes are off by default), and every list call would need it per user from
Keycloak instead of one query.

### D2 — Anonymise by nulling the name snapshots, keep the sub
V18 also drops `NOT NULL` from the username and display-name snapshot columns of `article`,
`article_revision`, `article_review` and `media`. Deleting sets both to `NULL` for every row whose
`*_sub` is the account; the `*_sub` columns stay. After the Keycloak user is gone the sub is a
random UUID that resolves to no one and is never reissued, so it carries no personal data, and
keeping it preserves the `NOT NULL` sub invariants and indexes ("my articles" simply never match
it again). `NULL` name = deleted account everywhere: DTOs send `null`, the admin app and the reader
render the localized "former newsroom member".
*Alternative:* a placeholder username such as `~deleted` — needs no migration but leaks into
every place that shows a username, and a magic string is easier to get wrong than `null`.
*Alternative:* replacing the sub with a fresh random id — no privacy gain over a dead Keycloak id.

### D3 — Order: database first, Keycloak last, one transaction
`AccountService.delete(id)` runs in one `@Transactional` method:
1. load the Keycloak user (404 for unknown or service account), check the policy (D5);
2. withdraw every pending submission of articles whose `author_sub` is the account (the existing
   withdraw path without its author check: status and live revision stay, `pending_level`
   cleared, no review record);
3. null the name snapshots (D2);
4. delete its `section_role`, `sectionless_reporter`, `trust` (`account_id` = it) and
   `account_deletion_request` rows; rows it set for others (`assigned_by`, `set_by`) stay;
5. delete the Keycloak user via `KeycloakCalls` (sessions end with it).

A Keycloak failure in step 5 throws and rolls the database back → `503`, nothing changed. A
commit failure after a successful step 5 (database down at that instant) leaves names in place for
a sub that no longer exists; it is logged at ERROR with the sub, and the operator runs the
recovery SQL of D4.
*Alternative:* Keycloak first, then database — a database failure would leave content under a
deleted user's name with no account left to retry from the UI.

### D4 — Anonymisation as a separate, idempotent step
Steps 2–4 live in `AccountDataEraser.erase(sub)`, idempotent and callable for a sub whose Keycloak
user is already gone; tests use it directly. There is no endpoint for it: for the recovery cases
(D3 commit failure, late writes in Risks) `deploy/INSTALL.md` documents the equivalent SQL for
one sub.

### D5 — Who may delete
`AccountPolicy` gets `DELETE`:
- requester holds `PUBLISHER`; target is not the requester;
- target without `PUBLISHER`: allowed (locked or not, request or not);
- target with `PUBLISHER`: allowed only with a pending request and at least one *other* enabled
  `PUBLISHER` besides the target (members of the `publisher` group, enabled, minus the target).
  The requester is such a publisher, so in practice the check matters only for locked
  requesters — kept for clarity and future roles.
`allowedActions` uses the same function, appended after `LOCK`/`UNLOCK`.

### D6 — REST shapes
```
POST   /api/me/deletion-request        (no body)
DELETE /api/me/deletion-request
  → 200 {"deletionRequestedAt": "2026-10-02T08:15:00Z"}   or   {"deletionRequestedAt": null}
  → 403 service account (empty body), 401 without token

GET /api/me → existing fields + "deletionRequestedAt": "2026-10-02T08:15:00Z" | null

DELETE /api/accounts/{id}
  → 204
  → 403 {"message": "you may not delete account 'chief'", "field": null}
  → 404 error body, 503 error body (Keycloak unavailable)

AccountDto → existing fields + "deletionRequestedAt": <instant|null>,
             allowedActions order EDIT_ROLES, RESET_PASSWORD, LOCK, UNLOCK, DELETE

ArticleDto/ArticleSummaryDto  authorUsername, authorDisplayName      → nullable
Revision entries              authorUsername, authorDisplayName      → nullable
Review entries                reviewerUsername, reviewerDisplayName  → nullable
MediaDto                      uploaderUsername, uploaderDisplayName  → nullable
```
(Field names as they are today in the primer; only nullability changes.) The admin API client
models become `String?`; `ai/primer/endpoints.md` documents `null` = deleted account.

### D7 — Admin app
- `MyAccountScreen` (commonMain) reached from the header's user line (now a `TextButton`), marker
  "Löschung beantragt" / "Deletion requested" on the line while a request is pending. Confirmation
  dialogs like the existing account actions. Same on web and Android; no platform code.
- Account list: sort pending requests first client-side (by `deletionRequestedAt`), marker with
  localized date, "Delete" action with the confirmation text of the spec.
- One helper `authorLabel(username, displayName)` returning the localized "former newsroom member"
  for `null`, used by the article list, editor (byline, "last changed by", review history) and
  media views.

### D8 — Reader
- `ReaderArticle` byline: `null` display name and username → message `byline.former`
  ("ehemaliges Redaktionsmitglied" / "former newsroom member").
- `GET /account-deletion`: new method in `ReaderResource`, template
  `ReaderResource/accountDeletion.html`, texts in the reader message bundles (de/en), public and
  cached like `legal-notice`; link to `/legal-notice` only when `ThemeFiles` has a notice. The legal
  notice template gets the link at its end.

### D9 — Homepage page (outside this repository)
`../../JAVASCRIPT/homepage`: route `/app/presserl/account-deletion` with
`presserlAccountDeletion_{de,en}.ts` and a view like `presserlPrivacy.vue`; the privacy policy's
"Your rights" links it. That URL goes into the Play Data safety form; the page points to each
newspaper's `/account-deletion` and to "My account" in the app. Committed and pushed in the archive
step with the other touched repositories.

## Risks / Trade-offs

- [Commit fails after Keycloak deletion] → ERROR log with the sub; `AccountDataEraser.erase(sub)`
  is idempotent and can be run for it; extremely rare (single database, short transaction).
- [A deleted account was a contributor (corrector) of a pending submission] → its revisions stay;
  the chain treats the unknown sub as holding no role, so the article may need more levels than
  before. Acceptable: the next reviewer can approve or reject as usual.
- [Section left without a section editor] → the existing "unstaffed level" rule applies; nothing
  new.
- [Publisher deletes a child's account by mistake] → confirmation dialog; content stays; the
  account can be re-created (new id, old content stays anonymous).
- [Names inside review notes or article texts] → out of scope (proposal non-goal); the publisher
  can edit them as before.
- [Access token of the deleted user still valid for its remaining lifetime] → refresh fails at
  once (Keycloak user gone), but an article or upload saved in those minutes would write a fresh
  name snapshot. Accepted: the realm's access tokens live 5 minutes and a deleted user rarely
  works at that moment; the recovery SQL of D4 anonymises such rows too. Documented in the primer.

## Migration Plan

1. Deploy: V18 runs (new table, nullable name columns — no data change).
2. Homepage page live, then the URL into the Play Console (android-play-publishing task 6.5).
Rollback: the image before V18 runs against the relaxed columns fine as long as no account was
deleted; after deletions, older images would show empty bylines — roll forward instead.
