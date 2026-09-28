# admin-articles Specification

## Purpose

Lets newsroom members manage and write articles in the administration app at editor level
`standard`, on top of the `/api/articles` endpoints and body format v1.

## Requirements

### Requirement: Article lists
After login the admin app SHALL show the list "My articles" (`GET /api/articles?mine=true`) and
SHALL offer a second list "All articles" (`GET /api/articles`), which contains the articles the
server makes visible to the user. Each entry SHALL show headline (or a placeholder for an empty
headline), section (name with its colour marker), status, author and time of last change, newest
change first as returned by the server. An entry with a `pendingLevel` SHALL additionally show
that it waits for approval by that level; an entry with `locked` `true` SHALL additionally show
that it is locked by a publisher. Selecting an entry SHALL open the article; an entry whose
`allowedActions` lack `EDIT` SHALL open read-only. Users holding a section role but no
newspaper-wide writer role SHALL get the same lists.

#### Scenario: Publisher sees own articles
- **WHEN** the publisher, who has one draft and one published article, logs in
- **THEN** "My articles" lists both with their section and status and the placeholder for a draft without headline

#### Scenario: Editor-in-chief opens a foreign article
- **WHEN** an editor-in-chief opens a published article of the publisher from "All articles"
- **THEN** the article is shown read-only with its section and the action "Take offline" only

#### Scenario: Reporter logs in
- **WHEN** `reader`, who is `REPORTER` in `Sport` only, logs in
- **THEN** "My articles" is shown and offers "New article"

#### Scenario: Waiting article in the list
- **WHEN** the section editor of `Sport` opens "All articles" while a reporter's article in `Sport` waits for `SECTION_EDITOR`
- **THEN** its entry shows that it waits for approval by the section editor

#### Scenario: Locked article in the list
- **WHEN** `chief` opens "My articles" while the publisher has taken one of `chief`'s articles offline
- **THEN** its entry shows that it is locked by a publisher

### Requirement: Review queue
Whenever the article lists are shown or reloaded, the admin app SHALL fetch the articles waiting
for the user (`GET /api/articles?awaitingMe=true`). While that list is not empty, the lists SHALL
offer a third list "Waiting for me" next to "My articles" and "All articles", whose label SHALL
show the number of waiting articles and which lists them with the same entries as the other
lists. While that list is empty, "Waiting for me" SHALL NOT be offered; when it becomes empty
while it is selected, the app SHALL show "My articles" instead. The app SHALL decide on the
queue from this response only, never from `roles` or `sectionRoles`.

#### Scenario: Section editor has something to approve
- **WHEN** the section editor of `Sport` opens the article lists while two reporters' articles in `Sport` wait for `SECTION_EDITOR`
- **THEN** "Waiting for me" is offered with the count `2` and lists both articles

#### Scenario: Solo publisher sees no queue
- **WHEN** the bootstrap publisher, the only account, opens the article lists
- **THEN** only "My articles" and "All articles" are offered

#### Scenario: Reporter sees no queue
- **WHEN** `reader`, `REPORTER` in `Sport` with a submitted article, opens the article lists
- **THEN** "Waiting for me" is not offered

#### Scenario: Count drops after approving
- **WHEN** the section editor, with two articles waiting, opens one from "Waiting for me", approves it and returns to the lists
- **THEN** "Waiting for me" shows the count `1` and lists only the other article

#### Scenario: Queue empties while selected
- **WHEN** the section editor, with one article waiting, opens it from "Waiting for me", approves it and returns to the lists
- **THEN** "Waiting for me" is no longer offered and "My articles" is shown

### Requirement: Create an article
The lists SHALL offer "New article", which creates an empty article (`POST /api/articles`
without `sectionId`, so the server files it under its default choice) and opens it in the editor.

#### Scenario: New article
- **WHEN** the publisher chooses "New article"
- **THEN** a draft is created and the editor opens with all fields empty and the section chosen by the server

### Requirement: Editor fields at level standard
The editor SHALL offer single-line fields kicker, headline and subheadline (at most 200
characters each), a single-line lead (at most 1000 characters) and a body edited as a sequence of
blocks of the types paragraph, subhead, quote and bullet list. Blocks SHALL be addable after any
block, movable up and down and removable. Paragraph, quote and list items SHALL support the mark
bold and no other formatting; subheads are plain text. List blocks SHALL have at least one item.
The editor SHALL NOT let the user enter line breaks into single-line fields or exceed the
length limits.

#### Scenario: Write a structured body
- **WHEN** the author writes a paragraph with one bold word, adds a subhead, a quote and a list with two items
- **THEN** the saved body is format v1 with a paragraph containing a bold run, a subhead, a quote and a two-item list, and it is accepted by the server

#### Scenario: Reopen keeps content
- **WHEN** the author reopens an article whose body contains every block type and bold runs
- **THEN** the editor shows the same blocks and bold text, and saving without changes sends an identical body

#### Scenario: Line break in the headline
- **WHEN** the author presses Enter in the headline field
- **THEN** no line break is inserted

### Requirement: Autosave
The editor SHALL save changes automatically with `PUT /api/articles/{id}` and the last received
`version`, shortly after the user stops typing, before publishing and when leaving the editor.
It SHALL show the save state (saving, saved, not saved). On `400` it SHALL show the server's
messages next to the named fields and save again after the next change. On `409` it SHALL stop
saving and tell the user the article was changed elsewhere, offering to load the current
version. On network errors it SHALL keep the changes and retry.

#### Scenario: Typing is saved
- **WHEN** the author types into the headline and pauses
- **THEN** the article is saved once with the latest content and the state shows "saved"

#### Scenario: Changed in another tab
- **WHEN** the same article was saved in another tab and the author continues typing in the first tab
- **THEN** the first tab shows that the article was changed elsewhere and offers to load the current version, and no content of the other tab is overwritten

#### Scenario: Offline for a moment
- **WHEN** a save fails because the network is unavailable and the network returns
- **THEN** the pending changes are saved without the user doing anything

### Requirement: Undo and redo
The editor SHALL offer always-visible undo and redo buttons covering all field and block changes
made since the article was opened. Undone changes SHALL be autosaved like any other change.

#### Scenario: Undo a removed block
- **WHEN** the author removes a paragraph and chooses undo
- **THEN** the paragraph is back at its position with its content and bold runs

### Requirement: Actions follow allowedActions
The editor SHALL show Publish, Submit, Withdraw, Approve, Reject, Take offline, Unlock and Delete
exactly when the article's `allowedActions` contain `PUBLISH`, `SUBMIT`, `WITHDRAW`, `APPROVE`,
`REJECT`, `TAKE_OFFLINE`, `UNLOCK` and `DELETE` respectively, and SHALL be editable exactly when
they contain `EDIT`. Publish, Submit and Approve are primary actions (bottom right). Publishing and
submitting SHALL first save pending changes. Deleting SHALL ask for confirmation in an in-app dialog
and return to the list. Rejecting SHALL open an in-app dialog asking for a note, SHALL NOT send an
empty note and SHALL show the server's message when it rejects the note. After an action the editor
SHALL show the returned article state. While the article has a `pendingLevel`, the editor SHALL
show that it waits for approval by that level; while it is `locked`, the editor SHALL show that a
publisher locked it and only a publisher can put it back online.

#### Scenario: Solo publisher publishes a draft
- **WHEN** the publisher publishes their own draft with a headline
- **THEN** the article is `PUBLISHED`, Publish disappears and Take offline appears

#### Scenario: Publish without headline
- **WHEN** the publisher publishes a draft with an empty headline
- **THEN** the editor shows the server's message at the headline field and the article stays a draft

#### Scenario: Delete a draft
- **WHEN** the author chooses Delete on a never-published draft and confirms
- **THEN** the article is deleted and the list no longer shows it

#### Scenario: Reporter submits
- **WHEN** a reporter chooses Submit on their draft with a headline in a section with a section editor
- **THEN** the pending changes are saved first, the editor becomes read-only, shows that the article waits for approval by the section editor and offers Withdraw

#### Scenario: Section editor approves
- **WHEN** the section editor opens the waiting article and chooses Approve
- **THEN** the editor shows that the article now waits for approval by the editor-in-chief and Approve and Reject disappear

#### Scenario: Reject with a note
- **WHEN** an approver chooses Reject, enters `Please add who scored.` and confirms
- **THEN** the article is rejected with that note and the editor shows the returned state

#### Scenario: Reject needs a note
- **WHEN** an approver chooses Reject and leaves the note empty
- **THEN** the dialog cannot be confirmed

#### Scenario: Publisher pulls the brake and unlocks
- **WHEN** the publisher opens `chief`'s published article and chooses Take offline
- **THEN** the editor shows that the article is locked and offers Unlock; after choosing Unlock the lock notice and Unlock disappear

### Requirement: Reviews in the editor
The editor SHALL show the article's reviews (`GET /api/articles/{id}/reviews`) with decision,
level, reviewer, time and note, newest first, whenever the article has at least one review. When
the newest review is a rejection and no submission is pending, the editor SHALL show its note
prominently above the article fields.

#### Scenario: Author sees why the article came back
- **WHEN** the author opens their draft that was rejected with the note `Please add who scored.`
- **THEN** the note is shown above the article fields together with the reviewer's name

#### Scenario: No reviews
- **WHEN** the author opens a draft that was never reviewed
- **THEN** no reviews are shown

### Requirement: Revision history
The editor SHALL offer the article's revision history (`GET /api/articles/{id}/revisions`)
showing number, last change, publication time and which revision is live, and SHALL show a
selected revision read-only (`GET /api/articles/{id}/revisions/{n}`), including its lead image
preview and caption when it has one.

#### Scenario: View an earlier revision
- **WHEN** the author of an article with revisions `2` (working) and `1` (live) opens revision `1`
- **THEN** its content is shown read-only and marked as live

#### Scenario: Earlier revision with a different lead image
- **WHEN** revision `1` has lead image 17 and revision `2` has lead image 18, and the author opens revision `1`
- **THEN** the read-only view shows the preview of media 17 and revision `1`'s caption

### Requirement: Link to the reader
For an article with status `PUBLISHED` the editor SHALL offer "View in reader", opening
`/articles/{id}` of the same origin in a new browser tab.

#### Scenario: Published article
- **WHEN** the author chooses "View in reader" on a published article
- **THEN** a new tab opens `/articles/{id}`

#### Scenario: Draft
- **WHEN** the author edits a draft that was never published
- **THEN** "View in reader" is not offered

### Requirement: Section chooser
The editor SHALL show the article's section above the kicker. When the article is editable it
SHALL offer a chooser listing, in position order, the sections whose `canWrite` is `true` in
`GET /api/sections`, each with its colour marker. Choosing another section SHALL be saved by
autosave like any content change (`PUT` with `sectionId`) and SHALL be covered by undo and redo.
A read-only article SHALL show its section without a chooser. When the server answers a save with
an error naming `sectionId`, the editor SHALL show the message at the chooser.

#### Scenario: Move a draft to another section
- **WHEN** an editor-in-chief opens their draft in `Sport` and chooses `Kultur`
- **THEN** the article is saved with the `sectionId` of `Kultur` and the lists show it under `Kultur`

#### Scenario: Reporter sees only their sections
- **WHEN** `reader`, `REPORTER` in `Sport` and `Kultur`, opens their draft while sections `General`, `Sport` and `Kultur` exist
- **THEN** the chooser offers `Sport` and `Kultur` only

#### Scenario: Undo a section change
- **WHEN** the author changes the section from `Sport` to `Kultur` and chooses undo
- **THEN** the chooser shows `Sport` again and the article is saved with `Sport`

### Requirement: Lead image in the editor
The editor SHALL offer a lead-image field at every editor level: a button to choose an image file
from the device, which uploads it (`POST /api/media`) and sets it as the article's lead image; a
preview of the chosen image (its `thumbnail` rendition); a single-line caption field (at most 300
characters, no line breaks); and buttons to replace and to remove the image. While the upload runs
the field SHALL show that it is busy. The lead image and caption SHALL be saved by the autosave
like the other fields. An upload that fails SHALL leave the current lead image unchanged and show
a plain message: too large (`413`, naming the maximum size), not a supported image type (`415`),
image damaged or too big in pixels (`400`), or the server not reachable (`503` and network errors).

#### Scenario: Add a lead image
- **WHEN** the author chooses a JPEG photo in the lead-image field and types the caption `Our cat Minka`
- **THEN** the image is uploaded, its preview is shown and the next autosave sends `leadImage` with the new media id and the caption

#### Scenario: File too large
- **WHEN** the author chooses a file the server refuses with `413`
- **THEN** the editor says the image is too large, names the maximum size and keeps the previous lead image

#### Scenario: Unsupported file
- **WHEN** the author chooses a HEIC file and the server answers `415`
- **THEN** the editor says this kind of image is not supported and keeps the previous lead image

#### Scenario: Remove the lead image
- **WHEN** the author removes the lead image
- **THEN** the preview and caption field disappear and the next autosave sends `leadImage` `null`

#### Scenario: Reopen keeps the lead image
- **WHEN** the author reopens an article with a lead image and caption
- **THEN** the editor shows its preview and caption, and saving without changes sends the same `leadImage`
