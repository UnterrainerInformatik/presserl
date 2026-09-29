# spell-check Specification

## Purpose

Lets writers check German text for spelling, grammar and punctuation mistakes through the backend,
which forwards the text to a LanguageTool service inside the installation.

## Requirements

### Requirement: Spell-check endpoint for writers
`POST /api/spell-check` SHALL accept `{"text": <string>}` from a user whose `allowedActions`
contain `WRITE_ARTICLES` and answer `200` with `{"matches": [...]}`, one entry per finding in
text order, each with `offset` and `length` in UTF-16 code units of `text`, a `message` explaining
the finding in German, and `replacements`, at most five suggested replacement strings (possibly
none). Only spelling, casing, grammar, punctuation and typography findings SHALL be reported;
style advice SHALL NOT. An empty or whitespace-only text SHALL answer `200` with no matches
without contacting the checker. A missing or non-string `text`, or a text longer than 10,000 code
points, SHALL answer `400` with the error body naming the field `text`. A user without
`WRITE_ARTICLES` SHALL get `403` with an empty body, a missing or invalid token `401`.

#### Scenario: Misspelled word
- **WHEN** a reporter sends `{"text": "Der Hund ist gros."}`
- **THEN** the response is `200` with a match at offset 13, length 4, a German message and `groß` among the replacements

#### Scenario: Capitalised noun
- **WHEN** a reporter sends `{"text": "Wir haben einen hund."}`
- **THEN** a match covers `hund` and its replacements contain `Hund`

#### Scenario: Correct text
- **WHEN** a reporter sends `{"text": "Heute scheint die Sonne."}`
- **THEN** the response is `200` with an empty `matches` list

#### Scenario: Offsets after an emoji
- **WHEN** a reporter sends a text starting with an emoji followed by a misspelled word
- **THEN** the match's offset counts the emoji as two code units, so it points at the misspelled word in a JavaScript or Kotlin string

#### Scenario: Reader is refused
- **WHEN** an account holding only `READER` calls the endpoint
- **THEN** the response is `403` with an empty body

#### Scenario: Too long
- **WHEN** a reporter sends a text of 10,001 code points
- **THEN** the response is `400` with an error naming `text`, and the checker is not contacted

### Requirement: Checker unavailable or switched off
When `presserl.spell-check.enabled` is `false`, or LanguageTool does not answer within five
seconds or answers with an error, `POST /api/spell-check` SHALL answer `503` with the error body
(field `null`). The backend's readiness SHALL NOT depend on LanguageTool, and no other endpoint
SHALL be affected. The checked text SHALL NOT be written to the log.

#### Scenario: LanguageTool down
- **WHEN** the `languagetool` container is stopped and a reporter calls the endpoint
- **THEN** the response is `503` within about five seconds, `/q/health/ready` stays `UP`, and saving articles keeps working

#### Scenario: Switched off
- **WHEN** `PRESSERL_SPELL_CHECK_ENABLED=false` and a reporter calls the endpoint
- **THEN** the response is `503` and LanguageTool is not contacted

### Requirement: Spell-check configuration
The backend SHALL read `presserl.spell-check.enabled` (default `true`), `presserl.spell-check.url`
(default `http://languagetool:8010`) and `presserl.spell-check.language` (a LanguageTool language
code, default `de-DE`), each settable per deployment through the environment
(`PRESSERL_SPELL_CHECK_ENABLED`, `PRESSERL_SPELL_CHECK_URL`, `PRESSERL_SPELL_CHECK_LANGUAGE`). The
language SHALL be passed to LanguageTool with every check.

#### Scenario: Austrian German
- **WHEN** `PRESSERL_SPELL_CHECK_LANGUAGE=de-AT` and a reporter sends `{"text": "Im Jänner war es kalt."}`
- **THEN** `Jänner` is not reported

### Requirement: Client configuration announces the spell check
`GET /api/client-config` SHALL contain `"spellCheck": true` when `presserl.spell-check.enabled` is
`true` and `"spellCheck": false` otherwise, independent of whether LanguageTool is currently
reachable.

#### Scenario: Default installation
- **WHEN** the admin app fetches `/api/client-config` from an installation with default settings
- **THEN** the response contains `"spellCheck": true`

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
