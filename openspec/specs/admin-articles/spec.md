# admin-articles Specification

## Purpose

Lets newsroom members manage and write articles in the administration app at editor level
`standard`, on top of the `/api/articles` endpoints and body format v1.

## Requirements

### Requirement: Article lists
After login the admin app SHALL show the list "My articles" (`GET /api/articles?mine=true`). It
SHALL offer a second list "All articles" (`GET /api/articles`), which contains the articles the
server makes visible to the user. Each entry SHALL show headline (or a placeholder for an empty
headline), section (name with its colour marker), status, author and time of last change.

Both lists SHALL offer a sort choice: "Last changed" (`sort=changed`, preselected), "Newest first"
(`sort=newest`) and "By section" (`sort=section`). The chosen order SHALL be requested from the
server and kept as returned. With "By section", the list SHALL show a heading with the section's
name and colour marker before the first entry of each section. The choice SHALL stay while the
app is open.

An entry with a `pendingLevel` SHALL additionally show that it waits for approval by that level. An
entry with `locked` `true` SHALL additionally show that it is locked by a publisher. Selecting an
entry SHALL open the article; an entry whose `allowedActions` lack `EDIT` SHALL open read-only.
Users holding a section role but no newspaper-wide writer role SHALL get the same lists.

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

#### Scenario: Sort by section
- **WHEN** the publisher chooses "By section" in "All articles" while `Sport` and `Kultur` hold articles
- **THEN** the app requests `GET /api/articles?sort=section` and shows a `Sport` heading followed by its articles, then a `Kultur` heading followed by its articles

#### Scenario: Newest first
- **WHEN** the publisher chooses "Newest first"
- **THEN** the app requests `sort=newest` and shows the most recently created article first

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
blocks of the types paragraph, subhead, quote, bullet list and image. Blocks SHALL be addable after
any block, movable up and down and removable. Paragraph, quote and list items SHALL support the mark
bold and no other formatting; subheads are plain text. List blocks SHALL have at least one item.
The editor SHALL NOT let the user enter line breaks into single-line fields or exceed the
length limits.

#### Scenario: Write a structured body
- **WHEN** the author writes a paragraph with one bold word, adds a subhead, a quote and a list with two items
- **THEN** the saved body is format v1 with a paragraph containing a bold run, a subhead, a quote and a two-item list, and it is accepted by the server

#### Scenario: Reopen keeps content
- **WHEN** the author reopens an article whose body contains every block type and bold runs
- **THEN** the editor shows the same blocks, bold text and images with their captions, and saving without changes sends an identical body

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
`REJECT`, `TAKE_OFFLINE`, `UNLOCK` and `DELETE` respectively. It SHALL be editable exactly when
they contain `EDIT`. Publish, Submit and Approve are primary actions (bottom right).

Publishing, submitting, approving and rejecting SHALL first save pending changes. Approving and
rejecting SHALL send the article `version` the editor holds. When the server answers either with
`409`, the editor SHALL show the conflict notice with the offer to load the current state.

Deleting SHALL ask for confirmation in an in-app dialog and return to the list. Rejecting SHALL open
an in-app dialog asking for a note. It SHALL NOT send an empty note and SHALL show the server's
message when it rejects the note. After an action the editor SHALL show the returned article state.

While the article has a `pendingLevel`, the editor SHALL show that it waits for approval by that
level. While it is `locked`, the editor SHALL show that a publisher locked it and only a publisher
can put it back online.

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

#### Scenario: Approve after someone else corrected
- **WHEN** the editor-in-chief has the waiting article open while another approver corrects it, and then chooses Approve
- **THEN** the server answers `409`, the editor shows the conflict notice and offers to load the current state, and the article still waits

#### Scenario: Correct and approve
- **WHEN** the section editor changes the headline of a waiting article and immediately chooses Approve
- **THEN** the change is saved first and the approval is sent with the version returned by that save

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
The editor SHALL offer the article's revision history (`GET /api/articles/{id}/revisions`). The
history SHALL show number, author, last change, publication time and which revision is live. It
SHALL show a selected revision read-only (`GET /api/articles/{id}/revisions/{n}`), with its author,
its lead image preview and caption when it has one, and the preview and caption of every image
block of its body.

Every revision except the first SHALL offer "Changes", which compares it with the revision before
it:

- **Text fields** (kicker, headline, subheadline, lead, lead-image caption): shown with removed
  words struck through and added words highlighted.
- **Body:** compared block by block. Added and removed blocks are marked as a whole. Changed
  paragraphs, subheads, quotes, list items and image captions show their word changes. A replaced
  image is marked as changed.
- **Unchanged fields:** shown without markup.

The comparison SHALL name the author of the newer revision. Markings SHALL NOT rely on colour
alone.

#### Scenario: View an earlier revision
- **WHEN** the author of an article with revisions `2` (working) and `1` (live) opens revision `1`
- **THEN** its content is shown read-only and marked as live

#### Scenario: Earlier revision with a different lead image
- **WHEN** revision `1` has lead image 17 and revision `2` has lead image 18, and the author opens revision `1`
- **THEN** the read-only view shows the preview of media 17 and revision `1`'s caption

#### Scenario: Earlier revision with a body image
- **WHEN** revision `1` has an image block with media 19 and caption `Our class` after its first paragraph, and the author opens revision `1`
- **THEN** the read-only view shows the preview of media 19 with the caption `Our class` after the first paragraph

#### Scenario: Changes of a correction
- **WHEN** `chief` changed the headline `Wir gewinnen gros` of `reader`'s revision `1` to `Wir gewinnen groß` in revision `2`, and `reader` chooses "Changes" on revision `2`
- **THEN** the comparison names `chief`, shows `gros` struck through and `groß` highlighted in the headline, and shows the other fields without markup

#### Scenario: Paragraph added
- **WHEN** revision `3` adds a paragraph after the first one
- **THEN** "Changes" on revision `3` marks that paragraph as added and leaves the others unmarked

#### Scenario: First revision
- **WHEN** the history shows revision `1`
- **THEN** it offers no "Changes"

### Requirement: Link to the reader
For an article whose `readerVisible` is `true` the editor SHALL offer "View in reader", opening
`/articles/{id}` of the same origin in a new browser tab. For every other article it SHALL NOT be
offered.

#### Scenario: Published article
- **WHEN** the author chooses "View in reader" on a published article of a published issue
- **THEN** a new tab opens `/articles/{id}`

#### Scenario: Draft
- **WHEN** the author edits a draft that was never published
- **THEN** "View in reader" is not offered

#### Scenario: Waiting for the issue
- **WHEN** the author opens a published article of an issue that is not published
- **THEN** "View in reader" is not offered and the editor shows "wartet auf Ausgabe N"

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

### Requirement: Media picker in the editor
The editor SHALL choose images through a media picker: a dialog over the editor that lists the
newspaper's media (`GET /api/media`), newest first, as tiles with the `thumbnail` rendition (a
placeholder while it is not produced yet), the uploader's display name, the upload date, the usage
count ("not used" for `0`) and up to three tags. The picker SHALL offer the search of the
**Images** view — tags, text, "Unused" and "Mine", combined — starting without filters every time
it opens, and SHALL load further pages when the user scrolls to the end. Selecting a tile SHALL
close the picker and hand the chosen media to the field that opened it; closing the picker
(close button, Escape or outside) SHALL choose nothing and change nothing. The picker SHALL offer
no upload and no camera. When the newspaper has no media, the picker SHALL say so and that images
are added in the **Images** view; when active filters match nothing, it SHALL say so and offer to
clear the filters. A list that fails to load SHALL show the error with a retry. The picker SHALL be
available at every editor level and only while the article is editable.

#### Scenario: Pick a colleague's photo
- **WHEN** a reporter opens the picker from the lead-image field and another reporter uploaded `Sportfest` photos earlier
- **THEN** the picker shows those photos among the newspaper's images with thumbnail, uploader, date and usage count

#### Scenario: Search in the picker
- **WHEN** the reporter types `Sportfest` as tag and switches on "Unused" in the picker
- **THEN** only unused images tagged `Sportfest` are shown, and scrolling to the end loads further matching pages

#### Scenario: Filters start empty
- **WHEN** the reporter closes the picker with an active filter and opens it again
- **THEN** the picker shows all images without filters

#### Scenario: Close without choosing
- **WHEN** the reporter opens the picker and presses Escape
- **THEN** the picker closes, the article is unchanged and nothing is saved

#### Scenario: Empty media library
- **WHEN** the newspaper has no images and the reporter opens the picker
- **THEN** the picker says there are no images yet and that they are added in the "Images" view, and offers no upload

### Requirement: Lead image from the media library
The editor SHALL offer a lead-image field at every editor level: a button to choose an image, which
opens the media picker and sets the picked media as the article's lead image; a preview of the
chosen image (its `thumbnail` rendition); a single-line caption field (at most 300 characters, no
line breaks); and buttons to replace and to remove the image. Replacing SHALL open the media
picker and keep the caption; closing the picker without a choice SHALL keep the current lead
image. The editor SHALL NOT upload files from the device. Setting, replacing and removing the lead
image SHALL be covered by undo and redo. The lead image and caption SHALL be saved by the autosave
like the other fields.

#### Scenario: Add a lead image
- **WHEN** the author chooses an image in the lead-image field, picks the photo `Sportfest 1` in the media picker and types the caption `Our cat Minka`
- **THEN** its preview is shown, no upload is sent, and the next autosave sends `leadImage` with that photo's media id and the caption

#### Scenario: Replace keeps the caption
- **WHEN** the author replaces the lead image with caption `Our class` by another image from the picker
- **THEN** the field shows the new preview, keeps the caption `Our class`, and the next autosave sends the new media id

#### Scenario: Picker closed without a choice
- **WHEN** the author opens the picker from "Replace" and closes it without selecting an image
- **THEN** the previous lead image stays and nothing is saved

#### Scenario: Remove the lead image
- **WHEN** the author removes the lead image
- **THEN** the preview and caption field disappear and the next autosave sends `leadImage` `null`

#### Scenario: Reopen keeps the lead image
- **WHEN** the author reopens an article with a lead image and caption
- **THEN** the editor shows its preview and caption, and saving without changes sends the same `leadImage`

### Requirement: Field explanations for children
The editor SHALL show a question-mark button next to the label of each of these parts: section,
kicker, headline, subheadline, lead image, caption, lead, and the block types paragraph, subhead,
quote, bullet list and image, wherever the editable editor shows that part's label, at every editor
level. The read-only article view, which shows the article without field labels, SHALL stay
unchanged. Pointing at the button, focusing it with the keyboard, or tapping or clicking it
SHALL open an explanation of that part; tapping or clicking the button again, pressing Escape, or
tapping or clicking outside SHALL close it, and focus SHALL stay on or return to the button. At
most one explanation SHALL be open at a time. The button SHALL carry an accessible label naming
the part (for example "What is the kicker?"), and opening it SHALL NOT change the article, move
the cursor out of an unrelated field's text, or trigger a save.

Each explanation SHALL contain, in the user's interface language (German or English):
- one or two short sentences written for a reader of about ten years, saying what the part is for;
- the same sample article for every part, showing section, kicker, headline, subheadline, a lead
  image placeholder with caption, lead and a short body with one paragraph, one subhead, one quote,
  one bullet list and one image placeholder with caption, in the order the reader shows them, with
  the explained part visibly highlighted and the other parts shown unhighlighted.

The lead-image explanation and the image-block explanation SHALL additionally tell the child, in the
same plain language, that every person who can be recognised in the photo must be asked first
whether it may be shown in the newspaper (for children, their parents too); that if someone says no
or cannot be asked, the child chooses another photo or has the faces made unrecognisable
(pixelated), asking an adult for help; and that only photos the child took or may use are allowed.
Editing, saving and choosing images SHALL stay possible without opening any explanation.

#### Scenario: Explain the kicker
- **WHEN** a reporter clicks the question mark next to "Dachzeile" in the German interface
- **THEN** a short explanation of the kicker opens together with the sample article, in which the kicker line is highlighted and the other parts are not

#### Scenario: Same sample for every part
- **WHEN** the reporter opens the explanation of the headline and then that of the lead
- **THEN** both show the same sample article, first with the headline highlighted, then with the lead highlighted

#### Scenario: Image rights on the lead image
- **WHEN** the reporter opens the explanation of the lead image
- **THEN** it explains the lead image, highlights the sample's image placeholder, and says to ask recognisable people (and their parents) first, to choose another photo or pixelate faces with an adult's help otherwise, and to use only own or permitted photos

#### Scenario: Image rights on an image block
- **WHEN** the reporter opens the question mark on an image block
- **THEN** it explains that an image block shows a photo inside the text, highlights the image placeholder in the sample's body, and gives the same image-rights advice as the lead-image explanation

#### Scenario: Block type explained
- **WHEN** the reporter opens the question mark on a quote block
- **THEN** the explanation of the quote opens with the sample's quote highlighted

#### Scenario: Keyboard and Escape
- **WHEN** the reporter moves keyboard focus onto the question mark of the subheadline and then presses Escape
- **THEN** the explanation opens on focus, closes on Escape, and focus stays on the question mark

#### Scenario: English interface
- **WHEN** the interface language is English and the reporter opens the explanation of the caption
- **THEN** the explanation and the sample article are in English

#### Scenario: Read-only article
- **WHEN** a reporter opens their submitted article, which is shown read-only
- **THEN** the article is shown as before, without question-mark buttons

#### Scenario: Opening help does not save
- **WHEN** the reporter opens and closes several explanations without typing
- **THEN** no save request is sent and the save state is unchanged

#### Scenario: Screen-reader label
- **WHEN** a screen reader reads the question mark next to "Vorspann"
- **THEN** it announces a label naming the lead, such as "Was ist der Vorspann?"

### Requirement: Image blocks from the media library
The editor's "Add block" menu SHALL offer "Image" next to the other block types, at every place a
block can be added and at every editor level. Choosing it SHALL open the media picker; on a pick,
an image block with the picked media and an empty caption SHALL be inserted where the menu was
opened. Closing the picker without a choice SHALL insert nothing. The editor SHALL NOT upload files
from the device.

An image block SHALL show a preview of its image (`thumbnail` rendition), a single-line caption
field (at most 300 characters, no line breaks) and a button to replace the image, which opens the
media picker and keeps the caption; closing the picker without a choice SHALL keep the current
image. Image blocks SHALL be movable and removable like the other blocks, and adding, replacing,
moving, removing and caption changes SHALL be covered by undo and redo and saved by the autosave as
`{"type": "image", "mediaId": <id>, "caption": "..."}` (without `caption` when it is empty). When
the server answers a save with an error naming a field of an image block, the editor SHALL show
the message at that block. The read-only article view SHALL show image blocks as preview and caption.

#### Scenario: Insert an image after a paragraph
- **WHEN** the author opens "Add block" below the first paragraph, chooses "Image", picks the photo `Zieleinlauf` in the media picker and types the caption `The finish line`
- **THEN** an image block with its preview appears below the first paragraph without any upload, and the next autosave sends a body with `{"type": "image", "mediaId": <id of Zieleinlauf>, "caption": "The finish line"}` at that position

#### Scenario: Close the picker
- **WHEN** the author chooses "Image" and closes the media picker without selecting an image
- **THEN** no block is added and nothing is saved

#### Scenario: Same image twice
- **WHEN** the author picks the lead image's media again for an image block
- **THEN** the block is inserted with that media id and both uses are saved

#### Scenario: Replace the image of a block
- **WHEN** the author replaces the image of an image block with caption `Our class` by another image from the picker
- **THEN** the block shows the new preview, keeps the caption `Our class`, and the next autosave sends the new media id

#### Scenario: Undo an inserted image
- **WHEN** the author inserts an image block and chooses undo
- **THEN** the image block is gone and the body is saved without it

#### Scenario: Move an image up
- **WHEN** the author moves an image block above the paragraph before it
- **THEN** the image block stands before that paragraph and the next autosave sends that order

#### Scenario: Read-only article with an image block
- **WHEN** a reporter opens their submitted article whose body contains an image block with caption `Our class`
- **THEN** the read-only view shows the image's preview and the caption at its place in the body

### Requirement: Corrections in the editor
When a user opens someone else's article whose `allowedActions` contain `EDIT`, the editor SHALL be
editable and SHALL show above the fields that they are correcting the article of its author (named
by display name). The section chooser SHALL be read-only for them. When the article's `lastEditor`
differs from its `author`, the editor SHALL show to everyone who opens it that the article was
last changed by `lastEditor`, together with an action "Show changes". That action SHALL open the
comparison of the latest revision with its predecessor (see "Revision history"). All texts SHALL
be available in German and English.

#### Scenario: Section editor corrects a waiting article
- **WHEN** the section editor of `Sport` opens `reader`'s article in `Sport` that waits for their approval
- **THEN** the fields are editable, the editor says they are correcting `reader`'s article, the section cannot be changed, and Approve and Reject are offered

#### Scenario: Author sees the correction
- **WHEN** `chief` corrected `reader`'s published article and `reader` opens it
- **THEN** the editor says the article was last changed by `chief` and offers "Show changes", which shows `chief`'s changes against the previous revision

#### Scenario: Corrections switched off
- **WHEN** `article.corrections` is `false` and the publisher opens `reader`'s published article
- **THEN** the article is shown read-only without the correcting notice

### Requirement: Published articles waiting for their issue
Wherever the admin app shows an article's status (article lists, review queue, issue details,
editor), a `PUBLISHED` article whose `readerVisible` is `false` SHALL additionally be marked as
"wartet auf Ausgabe N" / "waits for issue N" (N being the number of its issue), or "in keiner
Ausgabe" / "in no issue" when it belongs to no issue, so nobody mistakes "published" for "online".

#### Scenario: Article collected in the planned issue
- **WHEN** a published article belongs to issue 2, which is not published, and a German-speaking writer opens the article list
- **THEN** the entry shows the published status together with "wartet auf Ausgabe 2"

#### Scenario: Live article
- **WHEN** a published article belongs to the published issue 1
- **THEN** the entry shows no waiting marker

### Requirement: Front-page weight in the editor
For a user holding `EDITOR_IN_CHIEF` or `PUBLISHER` the editor SHALL show a field "Titelseite" /
"Front page" with the article's front-page weight, whatever the article's status and whether it is
editable, with a short explanation that 1 is the lead story and smaller numbers come first. Entering
a number from 1 to 999 or clearing the field SHALL be saved at once through the weight endpoint,
separately from autosave, and SHALL NOT be part of undo and redo. A rejected value SHALL show the
server's message at the field. Other users SHALL NOT see the field. In the article lists a weighted
article SHALL show its weight as a small marker (e.g. "Titelseite 1") for every user.

#### Scenario: Chief puts an article on top
- **WHEN** an editor-in-chief opens a published article and enters 1 in "Titelseite"
- **THEN** the weight is saved, the article stays published without a new revision, and the article list shows "Titelseite 1"

#### Scenario: Reporter does not see the field
- **WHEN** a reporter opens their own draft
- **THEN** the editor shows no front-page field

#### Scenario: Clearing
- **WHEN** the publisher empties the field of an article with weight 2
- **THEN** the weight is removed and the list shows no marker
