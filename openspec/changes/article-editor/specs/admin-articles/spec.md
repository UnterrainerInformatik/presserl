## Purpose

Lets newsroom members manage and write articles in the administration app at editor level
`standard`, on top of the `/api/articles` endpoints and body format v1.

## ADDED Requirements

### Requirement: Article lists
After login the admin app SHALL show the list "My articles" (`GET /api/articles?mine=true`) and
SHALL offer a second list "All articles" (`GET /api/articles`). Each entry SHALL show headline
(or a placeholder for an empty headline), status, author and time of last change, newest change
first as returned by the server. Selecting an entry SHALL open the article; an entry whose
`allowedActions` lack `EDIT` SHALL open read-only.

#### Scenario: Publisher sees own articles
- **WHEN** the publisher, who has one draft and one published article, logs in
- **THEN** "My articles" lists both with their status and the placeholder for a draft without headline

#### Scenario: Editor-in-chief opens a foreign article
- **WHEN** an editor-in-chief opens a published article of the publisher from "All articles"
- **THEN** the article is shown read-only with the action "Take offline" only

### Requirement: Create an article
The lists SHALL offer "New article", which creates an empty article (`POST /api/articles`) and
opens it in the editor.

#### Scenario: New article
- **WHEN** the publisher chooses "New article"
- **THEN** a draft is created and the editor opens with all fields empty

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
The editor SHALL show Publish (primary button, bottom right), Take offline and Delete exactly
when the article's `allowedActions` contain `PUBLISH`, `TAKE_OFFLINE` and `DELETE`
respectively, and SHALL be editable exactly when they contain `EDIT`. Publishing SHALL first
save pending changes. Deleting SHALL ask for confirmation in an in-app dialog and return to the
list. After an action the editor SHALL show the returned article state.

#### Scenario: Solo publisher publishes a draft
- **WHEN** the publisher publishes their own draft with a headline
- **THEN** the article is `PUBLISHED`, Publish disappears and Take offline appears

#### Scenario: Publish without headline
- **WHEN** the publisher publishes a draft with an empty headline
- **THEN** the editor shows the server's message at the headline field and the article stays a draft

#### Scenario: Delete a draft
- **WHEN** the author chooses Delete on a never-published draft and confirms
- **THEN** the article is deleted and the list no longer shows it

### Requirement: Revision history
The editor SHALL offer the article's revision history (`GET /api/articles/{id}/revisions`)
showing number, last change, publication time and which revision is live, and SHALL show a
selected revision read-only (`GET /api/articles/{id}/revisions/{n}`).

#### Scenario: View an earlier revision
- **WHEN** the author of an article with revisions `2` (working) and `1` (live) opens revision `1`
- **THEN** its content is shown read-only and marked as live

### Requirement: Link to the reader
For an article with status `PUBLISHED` the editor SHALL offer "View in reader", opening
`/articles/{id}` of the same origin in a new browser tab.

#### Scenario: Published article
- **WHEN** the author chooses "View in reader" on a published article
- **THEN** a new tab opens `/articles/{id}`

#### Scenario: Draft
- **WHEN** the author edits a draft that was never published
- **THEN** "View in reader" is not offered
