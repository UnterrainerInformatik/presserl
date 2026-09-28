## MODIFIED Requirements

### Requirement: Printable account slip
After a successful creation or password reset the app SHALL show the account slip with the
newspaper name, the web address of the reader, the username and the generated password, together
with a note that the password is shown only now, and a QR code. The QR code SHALL encode
`<reader address>/qr?u=<username>#pw=<password>` (the reader address without a trailing slash;
username and password as they are, since both consist of lowercase letters, digits and dashes
only) and SHALL be accompanied by a short localized line saying that scanning it opens the login.
"Print" SHALL open the browser's print dialog for the slip alone, laid out to fit on one A4 page
without the app's navigation, with the QR code printed sharply in black on white. "Done" SHALL
return to the account list, which then contains the new account; neither the password nor the
QR code SHALL be shown anywhere else.

#### Scenario: Slip after creation
- **WHEN** the publisher creates the account `lena`
- **THEN** the slip shows the newspaper name, the reader address, `lena`, the four-word password and a QR code

#### Scenario: Slip after a password reset
- **WHEN** the publisher confirms "Reset password" for `reader`
- **THEN** the slip shows the newspaper name, the reader address, `reader`, the new four-word password and a QR code for the new password

#### Scenario: QR code content
- **WHEN** the slip for `lena` with password `tiger-wolke-apfel-leiter` is shown on a newspaper whose reader address is `https://zeitung.example.org`
- **THEN** the QR code decodes to `https://zeitung.example.org/qr?u=lena#pw=tiger-wolke-apfel-leiter`

#### Scenario: Print the slip
- **WHEN** the publisher chooses "Print" on the slip
- **THEN** the print preview shows only the slip's content, including the QR code, on one page

#### Scenario: Back to the list
- **WHEN** the publisher chooses "Done"
- **THEN** the account list is shown including `lena`, without any password or QR code
