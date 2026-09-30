## ADDED Requirements

### Requirement: UI symbols render without downloaded fonts
The admin app SHALL render all of its own labels, symbols and markers (arrows, remove crosses,
undo/redo, external-link and pointer marks) without fetching fonts from any origin other than its
own. Symbols that the app's loaded fonts do not cover SHALL be drawn as graphics, not as text
characters. Every button that carries such a symbol SHALL keep its text label, so its accessible
name is unchanged.

#### Scenario: No font request on any admin screen
- **WHEN** a logged-in user opens the article list, the article editor, an issue, a section, the
  media browser, the accounts screen and the newspaper settings
- **THEN** the browser makes no request to `fonts.gstatic.com` or any other font host, and no CSP
  violation is reported

#### Scenario: Back button shows an arrow graphic
- **WHEN** a screen with a back button is shown
- **THEN** the button shows an arrow graphic in front of the label "Zurück" / "Back", and the
  arrow is visible (not blank and not a placeholder box)

#### Scenario: Accessible names keep their labels
- **WHEN** the move, remove, undo, redo, back and open-in-reader buttons are read by assistive
  technology
- **THEN** each button's name is its localized label, without a symbol character
