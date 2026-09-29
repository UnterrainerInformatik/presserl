## MODIFIED Requirements

### Requirement: Explanation and suggestions at the cursor
When the cursor of the focused field is inside a finding (by clicking or tapping the marked word or
by moving there with the keyboard), a row below the field SHALL show the finding's message, one
button per suggestion and an *Ignore* button; a finding with an empty message SHALL show no
message, and a finding without replacements no suggestion buttons, so a finding with neither shows
*Ignore* only. The row SHALL disappear when the cursor leaves the finding or the field loses
focus. Choosing a suggestion SHALL replace exactly the marked text, keep the bold state of the
replaced text, place the cursor after the replacement and count as a normal edit (undoable,
autosaved). *Ignore* SHALL remove every finding for that same word in all fields until the editor
or the form is closed. The row's buttons SHALL be reachable with the keyboard and carry accessible
labels.

#### Scenario: Accept a suggestion
- **WHEN** the reporter clicks the marked `gros` in "Der Hund ist gros" and chooses the suggestion `groß`
- **THEN** the headline reads "Der Hund ist groß", the mark and the row disappear, the article is saved, and undo restores "gros"

#### Scenario: Ignore a name
- **WHEN** the lead contains the marked name "Minka" twice and the reporter chooses *Ignore* on one of them
- **THEN** neither occurrence is marked any more, also after further typing, until the editor is closed

#### Scenario: Finding without suggestions
- **WHEN** the cursor is inside a finding that has no replacements
- **THEN** the row shows the message and *Ignore* only

#### Scenario: Finding without message
- **WHEN** the newspaper's `spell-check.help` is `marks` and the cursor is inside the marked `gros`
- **THEN** the row shows *Ignore* only, without any message or suggestion
