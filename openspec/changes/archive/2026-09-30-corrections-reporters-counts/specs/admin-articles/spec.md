## ADDED Requirements

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

## MODIFIED Requirements

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
