## ADDED Requirements

### Requirement: Admin app follows the system colour scheme
The admin app SHALL use a dark colour scheme when the operating system or browser prefers a dark
colour scheme, and a light colour scheme otherwise. When the preference changes while the app is
open, the app SHALL switch without a reload and without losing unsaved input. Text, controls,
dialogs, banners and spell-check marks SHALL stay readable in both schemes. The admin app SHALL
NOT offer its own switch; the system preference is the only input. The start-up loading indicator
and the start-up notices SHALL follow the same preference, so that a user with a dark preference
does not see a white page before the app draws. Content that must stay light regardless of the
scheme SHALL do so: the QR code on the account slip SHALL stay black on white, and the printed
account slip SHALL stay black on white.

#### Scenario: Dark system preference
- **WHEN** a user whose system prefers a dark colour scheme opens `/admin/` and logs in
- **THEN** the app shows a dark background with light text

#### Scenario: Light system preference
- **WHEN** a user whose system prefers a light colour scheme, or states no preference, opens `/admin/`
- **THEN** the app shows a light background with dark text, as before

#### Scenario: Preference changes while the app is open
- **WHEN** the user is editing an article and switches the system from light to dark
- **THEN** the app turns dark without a reload and the editor keeps the unsaved text

#### Scenario: Start-up screens in dark
- **WHEN** a user whose system prefers a dark colour scheme opens `/admin/` and the loading indicator or a start-up notice is shown
- **THEN** it is shown light-on-dark instead of on a white page

#### Scenario: QR code stays scannable
- **WHEN** a user with a dark preference creates an account and the account slip with its QR code is shown
- **THEN** the QR code is drawn black on white with its quiet zone

#### Scenario: Printed slip stays black on white
- **WHEN** a user with a dark preference prints the account slip
- **THEN** the printed page is black text on white
