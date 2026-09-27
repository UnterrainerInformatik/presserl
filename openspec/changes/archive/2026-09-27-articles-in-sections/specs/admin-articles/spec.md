## MODIFIED Requirements

### Requirement: Article lists
After login the admin app SHALL show the list "My articles" (`GET /api/articles?mine=true`) and
SHALL offer a second list "All articles" (`GET /api/articles`), which contains the articles the
server makes visible to the user. Each entry SHALL show headline (or a placeholder for an empty
headline), section (name with its colour marker), status, author and time of last change, newest
change first as returned by the server. Selecting an entry SHALL open the article; an entry whose
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

### Requirement: Create an article
The lists SHALL offer "New article", which creates an empty article (`POST /api/articles`
without `sectionId`, so the server files it under its default choice) and opens it in the editor.

#### Scenario: New article
- **WHEN** the publisher chooses "New article"
- **THEN** a draft is created and the editor opens with all fields empty and the section chosen by the server

## ADDED Requirements

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
