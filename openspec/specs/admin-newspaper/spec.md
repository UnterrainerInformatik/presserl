# admin-newspaper Specification

## Purpose

Lets publishers and editors-in-chief change the newspaper-wide settings from the admin app; in
this change the default text size of the reader.

## Requirements

### Requirement: Newspaper settings screen
The "Newspaper" header entry SHALL open a settings screen that shows the effective default reader
text size from `GET /api/newspaper` and offers the choices "installation default", S, M, L and XL
(with their pixel sizes 17/19/22/26 px). The selected choice SHALL be "installation default" when
`overrides` contains no `reader.text-size`, and the overriding size otherwise. Choosing a size
SHALL send it with `PUT /api/newspaper/settings`; choosing "installation default" SHALL send
`null` for `reader.text-size`. After a successful save the screen SHALL show the effective values
from the response; a refused save SHALL show the server's message and keep the previous choice.
All texts SHALL be available in German and English.

#### Scenario: Setting a larger default
- **WHEN** a publisher opens "Newspaper" on a fresh installation and chooses L
- **THEN** the app sends `PUT /api/newspaper/settings` with `{"reader.text-size": "l"}` and afterwards shows L as selected and as effective size

#### Scenario: Back to the installation default
- **WHEN** the newspaper overrides `reader.text-size` with `l` and the publisher chooses "installation default"
- **THEN** the app sends `{"reader.text-size": null}` and afterwards shows "installation default" as selected with the effective size of the deployment

#### Scenario: Save refused
- **WHEN** the server answers the save with `403`
- **THEN** the screen shows an error message and the previous choice stays selected

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

### Requirement: Corrections switch
The newspaper settings screen SHALL show a choice "Corrections by higher levels" (German
"Korrekturen durch Vorgesetzte"). It SHALL carry a one-line description saying that section
editors, editors-in-chief and publishers may then correct the articles of those below them instead
of only rejecting them. The choices SHALL be "installation default", "allowed" (`true`) and "not
allowed" (`false`), shown with the effective value from `GET /api/newspaper`. The selected choice
SHALL be "installation default" when `overrides` contains no `article.corrections`, and the
overriding value otherwise.

A user whose `allowedActions` contain `CONFIGURE_CORRECTIONS` SHALL be able to change it: choosing
a value SHALL send it as JSON boolean with `PUT /api/newspaper/settings`, and choosing
"installation default" SHALL send `null` for `article.corrections`. Success and refusal SHALL be
handled like the text size. Any other user SHALL see the choice disabled, with a note that only the
publisher can change it. All texts SHALL be available in German and English.

#### Scenario: Publisher switches corrections off
- **WHEN** the publisher opens "Newspaper" on a fresh installation and chooses "not allowed"
- **THEN** the app sends `PUT /api/newspaper/settings` with `{"article.corrections": false}` and afterwards shows "not allowed" as selected and effective

#### Scenario: Editor-in-chief sees the switch read-only
- **WHEN** an editor-in-chief opens "Newspaper"
- **THEN** the corrections choice is shown with the effective value but disabled, with the note that only the publisher can change it
