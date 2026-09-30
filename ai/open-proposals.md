# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M8 — Mobile & QR
Android/iOS targets of the admin app; the app reads the account slip's QR code
(`<base>/qr?u=<username>#pw=<pass-phrase>`: `<base>` = server address, `u` = username, `pw` =
pass-phrase) and logs in without typing; store publishing (Google Play Families policy, Apple
developer account). The slip's QR code itself is done (qr-slip-login); no token exchange.

## Reporter without a section
Instead of a separate photo-reporter role: a `REPORTER` who belongs to no section. They may upload
images, use the media view ("Images") and edit their own uploads under the existing uploader rule
(only while no live revision and no pending submission uses them); they write no articles, since
every article needs a section. Images stay shared across the whole newspaper, as today. Needs a
newspaper-wide marker for the sectionless reporter (per-section reporter rows cannot express it),
assignment from editor-in-chief upward (a section editor only assigns within their own sections),
password-reset and role-change rules, the account form, the realm template, and decoupling the media
endpoints and the "Images" header entry from `WRITE_ARTICLES` (own action). Removing a reporter from
their last section leaves them a photographer instead of an account without any writing right.
Builds on media-view-crop-blur.

## Higher levels edit articles
Approvers may correct an article instead of rejecting it with a note: the section editor of the
article's section, editors-in-chief and publishers. Allowed while a submission is pending and on
published or offline articles, not on drafts (a child's draft stays theirs). The edit creates a new
revision recorded with the editor as its author. The approval chain must not be bypassed: once
someone other than the author changed the content, the levels above that editor apply as if the
editor had written the article (trust in the original author does not skip them). The author sees
who changed what as a diff between revisions in the editor. Concurrent edits by author and reviewer
are refused with a reload offer, as for images.

## init-runner-action on Node 24
`UnterrainerInformatik/init-runner-action` (used by every shared workflow, incl.
docker-build-workflow) still checks out with `actions/checkout@v4` and pre-fetches v4/v3 actions,
which GitHub forces onto Node 24 with a deprecation annotation. Raise to the current majors
(checkout v7 at the time of ci-build-speed). Lives outside this repo; affects all callers.

## Analytics package: article counts and list sorting
- Section list: every section shows its number of articles — currently (live/published now), ever
  (all articles that were ever in it) and per issue.
- Article list: sorting "newest first" and "by section".
Needs count fields (or a stats endpoint) for sections, a sort parameter for `GET /api/articles`,
the admin list UI, primer and `.http` updates.

## Publishers may edit other authors' articles (newspaper switch)
A higher-ranking editor (publisher) may edit articles of other authors after all. Controlled by a
switch on the "Newspaper" settings page; default: editable. Touches `ArticlePolicy` (EDIT verdict),
the newspaper settings (new setting with default `true`), the settings screen, primer and tests;
clarify how it interacts with pending submissions and the emergency-brake lock.
