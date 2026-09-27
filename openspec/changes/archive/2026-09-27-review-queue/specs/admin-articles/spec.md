## ADDED Requirements

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
