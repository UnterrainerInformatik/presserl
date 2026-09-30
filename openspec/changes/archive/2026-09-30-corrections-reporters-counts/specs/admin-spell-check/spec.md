## MODIFIED Requirements

### Requirement: Checked fields
When `GET /api/client-config` reports `"spellCheck": true`, the admin app SHALL check these editable
fields:

- kicker, headline, subheadline and lead;
- the lead-image caption;
- every paragraph, subhead, quote and list item of the body;
- the caption of every image block of the body;
- the rejection note;
- the section name.

It SHALL NOT check usernames, first and last names, dates, pass-phrases or read-only views.

A field SHALL be checked about one second after the user stops changing it and when an article is
opened in the editable editor. Only the text as it is when the answer arrives SHALL be marked; an
answer for text that has changed meanwhile SHALL be discarded. Choosing a suggestion in any editor
field SHALL be its own undo step. When `spellCheck` is `false` the app SHALL send no spell-check
requests.

#### Scenario: Typing in the headline
- **WHEN** a reporter types "Der Hund ist gros" into the headline and pauses
- **THEN** after about a second `gros` is marked, and one spell-check request was sent for the headline

#### Scenario: Name field unchecked
- **WHEN** an editor-in-chief types a first name into the new-account form
- **THEN** no spell-check request is sent

#### Scenario: Read-only article
- **WHEN** a reporter opens their submitted, read-only article containing a misspelled word
- **THEN** nothing is marked and no spell-check request is sent

#### Scenario: Spell check switched off
- **WHEN** the installation reports `"spellCheck": false` and a reporter writes an article
- **THEN** no spell-check request is sent and nothing is marked

#### Scenario: Image-block caption
- **WHEN** a reporter types "Unser Klasenfoto" into the caption of an image block and pauses
- **THEN** `Klasenfoto` is marked in that caption only, and the caption of another image block and the lead-image caption stay unmarked

#### Scenario: Suggestion in an image-block caption is its own undo step
- **WHEN** the reporter picks the suggestion `Klassenfoto` for that caption and then chooses undo
- **THEN** the caption shows `Unser Klasenfoto` again, and the text typed before stays
