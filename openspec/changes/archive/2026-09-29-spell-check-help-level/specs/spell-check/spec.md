## ADDED Requirements

### Requirement: Help level limits the answer
`POST /api/spell-check` SHALL shape every match by the newspaper's effective `spell-check.help`
at the time of the request: with `suggestions` a match carries its message and replacements as
described for the endpoint; with `messages` every match SHALL carry an empty `replacements` list;
with `marks` every match SHALL carry an empty `replacements` list and an empty `message` (`""`).
`offset` and `length` and the set of reported matches SHALL be the same on every level.

#### Scenario: Messages only
- **WHEN** the newspaper's `spell-check.help` is `messages` and a reporter sends `{"text": "Der Hund ist gros."}`
- **THEN** the response is `200` with a match at offset 13, length 4, a German message and `replacements` equal to `[]`

#### Scenario: Marks only
- **WHEN** the newspaper's `spell-check.help` is `marks` and a reporter sends `{"text": "Der Hund ist gros."}`
- **THEN** the response is `200` with a match at offset 13, length 4, `message` equal to `""` and `replacements` equal to `[]`

#### Scenario: Level changed between two checks
- **WHEN** the publisher changes `spell-check.help` from `suggestions` to `marks` and a reporter sends the same text again
- **THEN** the second response carries no message and no replacements
