## ADDED Requirements

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
