## MODIFIED Requirements

### Requirement: Header and editor bar fit narrow screens
On screens narrower than about 720 dp the header SHALL be a single compact row with the newspaper
name and a menu button. The menu SHALL list every navigation entry the user has (the current one
marked), the user line, which opens "My account", and "Log out". Choosing an entry SHALL close the
menu. With no navigation entries, the menu SHALL still offer the user line and "Log out". Wider
screens SHALL keep the single-row header with the user line, the navigation entries and "Log out"
side by side.

On narrow screens the editor's status row (back, status, revisions, reader link) and its notice
banners SHALL scroll together with the article. The conflict banner SHALL stay fixed above the
article. The editor's bottom bar SHALL be a single line: undo and redo as icons (keeping "Undo" and
"Redo" as their accessible names), the save state, and the article actions with the primary action
at the end. When that line does not fit the screen width, the whole bar SHALL be scaled down
uniformly until it fits rather than wrap onto further lines; its tap targets SHALL scale with it.

On narrow screens in the Android app, while the on-screen keyboard is shown, the header SHALL be
hidden. The editor's bottom bar SHALL then shrink to a single line holding undo and redo as icons
(keeping "Undo" and "Redo" as their accessible names) and the save state. The article actions SHALL
come back as soon as the keyboard closes.

Text in the header, the menu and the bottom bar SHALL never be squeezed into a column narrower than
its words. A newspaper name that does not fit SHALL wrap. A user line that does not fit on one line
SHALL wrap start-aligned with no character cut off.

#### Scenario: Phone width
- **WHEN** a publisher opens the admin app in a 390 px wide window
- **THEN** the header is one row with the newspaper name reading horizontally and a menu button, and the page content below it is visible and usable

#### Scenario: Every navigation entry reachable
- **WHEN** a publisher holding all newspaper-wide actions opens the menu at phone width
- **THEN** the menu lists Articles, Images, Sections, Issues, Accounts and Newspaper in full, the current entry is marked, and choosing "Accounts" opens the accounts screen and closes the menu

#### Scenario: Long user line at phone width
- **WHEN** a user holding three section roles (e.g. "Lena Berger · Ressortleiter · Dorfleben, Redakteur · Sport, Redakteur · Kultur") opens the menu in a 390 px wide window or on a phone
- **THEN** the user line wraps over several lines, each line starts at the same start edge and shows its first character in full, and tapping it opens "My account"

#### Scenario: Reporter without navigation
- **WHEN** a sectionless reporter, who has no navigation entries, opens the menu at phone width
- **THEN** the menu shows the user line and "Log out"

#### Scenario: Editor status row scrolls away
- **WHEN** a publisher scrolls down in a long article in the editor at phone width
- **THEN** the back button, status and notice banners scroll out of view with the article, and the bottom bar stays

#### Scenario: Conflict stays visible
- **WHEN** the editor shows the conflict banner at phone width and the user scrolls down
- **THEN** the conflict banner stays visible above the article

#### Scenario: Typing on the phone
- **WHEN** a reporter taps into a paragraph in the editor on an Android phone and the keyboard opens
- **THEN** the header is hidden, the bottom bar is one line with undo and redo icons and the save state, and more of the article is visible than with the keyboard closed

#### Scenario: Keyboard closes
- **WHEN** the reporter closes the keyboard
- **THEN** the header and the full bottom bar with the article actions are shown again

#### Scenario: Editor bar at phone width
- **WHEN** a publisher opens a published article in the editor in a 390 px wide window without a keyboard shown
- **THEN** the bottom bar is a single line with the undo and redo icons, the save state, "Delete", "Take offline" and "Publish", every word unbroken and nothing cut off

#### Scenario: Editor bar too wide for the phone
- **WHEN** the bottom bar's single line is wider than the screen (e.g. a publisher with several article actions on a narrow phone, in German)
- **THEN** the bar is scaled down as a whole so that it fits on one line, its buttons still respond to taps where they are drawn, and it never wraps onto a second line

#### Scenario: Desktop width
- **WHEN** the window is 1280 px wide
- **THEN** the header is a single row with the user line, the navigation entries and "Log out", and the editor's status row stays fixed above the article as before
