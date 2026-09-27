## ADDED Requirements

### Requirement: Delete a section
`DELETE /api/sections/{id}` SHALL be available to users holding `PUBLISHER` or `EDITOR_IN_CHIEF`;
other authenticated users SHALL get `403`. An unknown id SHALL be answered with `404`. While at
least one article (in any status) belongs to the section, the system SHALL answer `409` with the
error body and change nothing. Otherwise it SHALL delete the section together with all section
roles held in it, set the positions of the remaining sections to `0, 1, 2, …` in their previous
order, and answer `204` with an empty body. Deleting the only remaining section SHALL be allowed.

#### Scenario: Editor-in-chief deletes an empty section
- **WHEN** sections `Sport`, `Kultur` and `Wetter` exist in this order, `Kultur` has no articles and `chief` deletes `Kultur`
- **THEN** the response is `204` and `GET /api/sections` lists `Sport` at position `0` and `Wetter` at position `1`

#### Scenario: Section still contains articles
- **WHEN** `Sport` contains one draft and the publisher deletes `Sport`
- **THEN** the response is `409` with the error body, and `Sport` and its draft are unchanged

#### Scenario: Section roles go with the section
- **WHEN** `reader` is `REPORTER` in `Kultur` only, `Kultur` has no articles and the publisher deletes `Kultur`
- **THEN** `reader` holds no section role any more

#### Scenario: Section editor may not delete
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and deletes `Sport`
- **THEN** the response is `403` and `Sport` still exists

#### Scenario: Unknown section
- **WHEN** the publisher deletes a section id that does not exist
- **THEN** the response is `404`

### Requirement: Every article has a section in the database
The database SHALL reject articles without a section. Articles left without a section by an
earlier version SHALL be filed, when the database is upgraded, under the first section by
position; when no section exists then, a section `General` (slug `general`, colour `red`,
position `0`) SHALL be created for them.

#### Scenario: Upgrade with an unfiled article
- **WHEN** the database holds sections `Sport` and `Kultur` in this order and one article without a section, and the backend starts with this version
- **THEN** the article belongs to `Sport`
