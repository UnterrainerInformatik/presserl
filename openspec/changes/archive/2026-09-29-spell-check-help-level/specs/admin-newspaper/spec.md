## ADDED Requirements

### Requirement: Spell-check help choice
When `GET /api/client-config` reports `"spellCheck": true`, the newspaper settings screen SHALL
show a "Spell-check help" choice with "installation default" and the three levels "marks,
explanation and suggestions" (`suggestions`), "marks and explanation" (`messages`) and "marks
only" (`marks`), each with a one-line description, and the effective level from
`GET /api/newspaper`. The selected choice SHALL be "installation default" when `overrides` contains
no `spell-check.help`, and the overriding level otherwise. A user whose `allowedActions` contain
`CONFIGURE_SPELL_CHECK` SHALL be able to change it: choosing a level SHALL send it with
`PUT /api/newspaper/settings`, choosing "installation default" SHALL send `null` for
`spell-check.help`; success and refusal SHALL be handled like the text size. Any other user SHALL
see the choice disabled with a note that only the publisher can change it. When `spellCheck` is
`false` the choice SHALL NOT be shown. All texts SHALL be available in German and English.

#### Scenario: Publisher chooses marks only
- **WHEN** the publisher opens "Newspaper" on a fresh installation and chooses "marks only"
- **THEN** the app sends `PUT /api/newspaper/settings` with `{"spell-check.help": "marks"}` and afterwards shows "marks only" as selected and effective

#### Scenario: Editor-in-chief sees the level read-only
- **WHEN** an editor-in-chief opens "Newspaper" while the newspaper overrides `spell-check.help` with `messages`
- **THEN** "marks and explanation" is shown selected but disabled, with the note that only the publisher can change it, and no request is sent when they click it

#### Scenario: Spell check switched off
- **WHEN** the installation reports `"spellCheck": false` and the publisher opens "Newspaper"
- **THEN** no spell-check help choice is shown
