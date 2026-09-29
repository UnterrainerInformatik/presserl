## Purpose

Shows German spelling, grammar and punctuation findings in the admin app's prose fields while the
user writes, with an explanation and suggestions a child can act on.

## ADDED Requirements

### Requirement: Checked fields
When `GET /api/client-config` reports `"spellCheck": true`, the admin app SHALL check these
editable fields: kicker, headline, subheadline, lead, lead-image caption, every paragraph, subhead,
quote and list item of the body, the rejection note and the section name. It SHALL NOT check
usernames, first and last names, dates, pass-phrases or read-only views. A field SHALL be checked
about one second after the user stops changing it and when an article is opened in the editable
editor, and only the text as it is when the answer arrives SHALL be marked — an answer for text
that has changed meanwhile SHALL be discarded. When `spellCheck` is `false` the app SHALL send no
spell-check requests.

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

### Requirement: Findings are marked in the text
Every finding SHALL be shown in the field itself by drawing the affected text red and underlined,
in plain fields and in rich-text blocks alike. Marking SHALL NOT change the field's content: it
SHALL NOT trigger a save, add an undo step or change bold runs, and the saved article SHALL be
identical with and without marks.

#### Scenario: Mark in a bold paragraph
- **WHEN** a paragraph contains a bold misspelled word and the check marks it
- **THEN** the word is shown bold, red and underlined, and no save request is sent

#### Scenario: Undo after marking
- **WHEN** the reporter types a word, it is marked, and the reporter chooses undo
- **THEN** undo removes the typed word, not the mark

### Requirement: Explanation and suggestions at the cursor
When the cursor of the focused field is inside a finding (by clicking or tapping the marked word or
by moving there with the keyboard), a row below the field SHALL show the finding's message, one
button per suggestion and an *Ignore* button; it SHALL disappear when the cursor leaves the
finding or the field loses focus. Choosing a suggestion SHALL replace exactly the marked text,
keep the bold state of the replaced text, place the cursor after the replacement and count as a
normal edit (undoable, autosaved). *Ignore* SHALL remove every finding for that same word in all
fields until the editor or the form is closed. The row's buttons SHALL be reachable with the
keyboard and carry accessible labels.

#### Scenario: Accept a suggestion
- **WHEN** the reporter clicks the marked `gros` in "Der Hund ist gros" and chooses the suggestion `groß`
- **THEN** the headline reads "Der Hund ist groß", the mark and the row disappear, the article is saved, and undo restores "gros"

#### Scenario: Ignore a name
- **WHEN** the lead contains the marked name "Minka" twice and the reporter chooses *Ignore* on one of them
- **THEN** neither occurrence is marked any more, also after further typing, until the editor is closed

#### Scenario: Finding without suggestions
- **WHEN** the cursor is inside a finding that has no replacements
- **THEN** the row shows the message and *Ignore* only

### Requirement: Checker unavailable
When a spell-check request fails (`503`, network error or timeout), the app SHALL keep the field's
previous marks for unchanged text, show once per editor or form a small notice that the spell
check is currently unavailable, try again after the next change but not more often than once a
minute per field, and SHALL NOT block or delay typing, saving or any other action.

#### Scenario: LanguageTool stopped while writing
- **WHEN** LanguageTool is stopped and the reporter keeps typing into the lead
- **THEN** the notice "Rechtschreibprüfung gerade nicht verfügbar" appears once, typing and autosave work as before, and marking resumes after LanguageTool is back and the reporter changes the field

### Requirement: Localized spell-check texts
The notice, the *Ignore* button and the accessible labels SHALL be localized in German and English
like all other admin texts. The finding messages SHALL be shown as the server returns them.

#### Scenario: English interface
- **WHEN** the interface language is English and the checker is unavailable
- **THEN** the notice reads "Spell check is currently unavailable" and the button reads "Ignore"
