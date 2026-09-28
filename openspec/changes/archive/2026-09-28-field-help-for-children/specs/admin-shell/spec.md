## ADDED Requirements

### Requirement: Keyboard use survives leaving a text field
After keyboard focus moves from a text field to a control that is not a text field (for example
with Tab), the admin app SHALL keep receiving keys: Tab, Shift+Tab, Enter, Space and Escape SHALL
keep working on the newly focused control without clicking into the app first.

#### Scenario: Tab from a text field onto a button
- **WHEN** a user types in the subheadline field, presses Tab onto its question-mark button and then presses Escape
- **THEN** the explanation opens on focus, closes on Escape, and a further Tab moves focus on

### Requirement: Header and editor bar fit narrow screens
On screens narrower than about 720 dp the header SHALL show the newspaper name, the user line and
"Log out" in a first row and the navigation entries in a second row that scrolls horizontally
when it does not fit. Wider screens SHALL keep the single-row header. On the same narrow screens
the editor's bottom bar (undo, redo, save state and the article actions) SHALL wrap as one group,
keeping the primary action at the end. Text in the header and in the bottom bar SHALL never be
squeezed into a column narrower than its words.

#### Scenario: Phone width
- **WHEN** a publisher opens the admin app in a 390 px wide window
- **THEN** the newspaper name reads horizontally, every navigation entry is reachable, and the page content below the header is visible and usable

#### Scenario: Editor bar at phone width
- **WHEN** a publisher opens an article in the editor in a 390 px wide window
- **THEN** "Undo", "Redo", the save state and the actions are shown with unbroken words, wrapping onto further lines as needed

#### Scenario: Desktop width
- **WHEN** the window is 1280 px wide
- **THEN** the header is a single row as before
