## MODIFIED Requirements

### Requirement: Header and editor bar fit narrow screens
On screens narrower than about 720 dp the header SHALL show the newspaper name, the user line and
"Log out" in a first row and the navigation entries in a second row that scrolls horizontally
when it does not fit. Wider screens SHALL keep the single-row header. On the same narrow screens
the editor's bottom bar (undo, redo, save state and the article actions) SHALL wrap as one group,
keeping the primary action at the end. Text in the header and in the bottom bar SHALL never be
squeezed into a column narrower than its words. A user line that does not fit on one line SHALL
wrap start-aligned, every line beginning at the same start edge as the newspaper name, with no
character cut off.

#### Scenario: Phone width
- **WHEN** a publisher opens the admin app in a 390 px wide window
- **THEN** the newspaper name reads horizontally, every navigation entry is reachable, and the page content below the header is visible and usable

#### Scenario: Long user line at phone width
- **WHEN** a user holding three section roles (e.g. "Lena Berger · Ressortleiter · Dorfleben, Redakteur · Sport, Redakteur · Kultur") opens the admin app in a 390 px wide window or on a phone
- **THEN** the user line wraps over several lines, each line starts at the newspaper name's start edge and shows its first character in full, and tapping any line opens "My account"

#### Scenario: Editor bar at phone width
- **WHEN** a publisher opens an article in the editor in a 390 px wide window
- **THEN** "Undo", "Redo", the save state and the actions are shown with unbroken words, wrapping onto further lines as needed

#### Scenario: Desktop width
- **WHEN** the window is 1280 px wide
- **THEN** the header is a single row as before
