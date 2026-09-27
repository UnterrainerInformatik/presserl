## MODIFIED Requirements

### Requirement: Article lists
After login the admin app SHALL show the list "My articles" (`GET /api/articles?mine=true`) and
SHALL offer a second list "All articles" (`GET /api/articles`), which contains the articles the
server makes visible to the user. Each entry SHALL show headline (or a placeholder for an empty
headline), section (name with its colour marker), status, author and time of last change, newest
change first as returned by the server. An entry with a `pendingLevel` SHALL additionally show
that it waits for approval by that level. Selecting an entry SHALL open the article; an entry whose
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

### Requirement: Actions follow allowedActions
The editor SHALL show Publish, Submit, Withdraw, Approve, Reject, Take offline and Delete exactly
when the article's `allowedActions` contain `PUBLISH`, `SUBMIT`, `WITHDRAW`, `APPROVE`, `REJECT`,
`TAKE_OFFLINE` and `DELETE` respectively, and SHALL be editable exactly when they contain `EDIT`.
Publish, Submit and Approve are primary actions (bottom right). Publishing and submitting SHALL
first save pending changes. Deleting SHALL ask for confirmation in an in-app dialog and return to
the list. Rejecting SHALL open an in-app dialog asking for a note, SHALL NOT send an empty note and
SHALL show the server's message when it rejects the note. After an action the editor SHALL show
the returned article state. While the article has a `pendingLevel`, the editor SHALL show that it
waits for approval by that level.

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

## ADDED Requirements

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
