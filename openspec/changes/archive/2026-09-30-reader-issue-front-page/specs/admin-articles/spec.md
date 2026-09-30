## ADDED Requirements

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

## MODIFIED Requirements

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
