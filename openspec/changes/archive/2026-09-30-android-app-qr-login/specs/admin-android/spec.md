## Purpose

The administration app on Android: one app for every presserl installation that connects to a
newspaper by scanning the account slip's QR code (or by its address) and then offers the same
screens as the web admin app.

## ADDED Requirements

### Requirement: Android app offers the admin app's screens
The Android app SHALL offer, after login, the same screens, navigation and permissions as the web
admin app (see `admin-shell` and the other `admin-*` capabilities), talking to the chosen server's
`/api` with bearer tokens. Tokens SHALL be kept in memory only.

#### Scenario: Reporter works on a phone
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "USE_MEDIA"]` logs in to the Android app
- **THEN** the header shows the newspaper name, their display name and roles, and the list "My articles" opens, as in the web app

#### Scenario: Article written on the phone appears on the web
- **WHEN** a reporter creates and edits an article in the Android app
- **THEN** the article with its changes is listed when the same user opens the web admin app

### Requirement: Start screen chooses the server
Without a known server the Android app SHALL show a start screen that offers "Scan account slip",
entering a server address, and a link to the app's privacy policy. A server address SHALL be
accepted only when it uses `https` (a debug build additionally accepts `http`) and
`GET <address>/api/client-config` answers with an OIDC configuration; otherwise the app SHALL say
that no presserl newspaper was found at that address and stay on the start screen. The accepted
address SHALL be remembered across app restarts until the user logs out.

#### Scenario: Address of a newspaper
- **WHEN** the user enters `https://presserl.example.org` on the start screen
- **THEN** the app fetches `https://presserl.example.org/api/client-config` and continues with the login page of the issuer it names

#### Scenario: Address without presserl
- **WHEN** the user enters an address whose `/api/client-config` does not answer with an OIDC configuration
- **THEN** the app shows that no presserl newspaper was found there and stays on the start screen

#### Scenario: Plain http rejected
- **WHEN** the user of a release build enters `http://presserl.example.org`
- **THEN** the app refuses the address and asks for an `https` address

#### Scenario: Privacy policy
- **WHEN** the user taps the privacy policy link on the start screen
- **THEN** the privacy policy opens in the phone's browser, in German when the phone's language is German and in English otherwise

### Requirement: Scanning the account slip logs in without typing
"Scan account slip" SHALL open a QR scanner. A scanned code SHALL be accepted only when it has the
form `<base>/qr?u=<username>#pw=<pass-phrase>` with a non-empty username that satisfies the
username rules of account creation and a non-empty pass-phrase. For an accepted code the app SHALL
check `<base>` as for an entered address, open the issuer's login page and submit username and
pass-phrase there exactly once, without the user typing anything. Any other code SHALL be rejected
with a message that it is not a presserl account slip, and no request SHALL be sent for it. The
pass-phrase SHALL NOT be sent anywhere but to the issuer's login form.

#### Scenario: Child scans the slip
- **WHEN** a child with the slip of account `anna` on `https://presserl.example.org` taps "Scan account slip" and scans its QR code
- **THEN** the app connects to `https://presserl.example.org`, logs in as `anna` without further input and shows her start view

#### Scenario: Foreign QR code
- **WHEN** the user scans a QR code containing `https://example.com/some/page`
- **THEN** the app says the code is not a presserl account slip and stays on the start screen

#### Scenario: Scan cancelled
- **WHEN** the user closes the scanner without scanning
- **THEN** the app returns to the start screen unchanged

### Requirement: Rejected slip credentials fall back to the login page
When the issuer rejects the scanned credentials (the login page shows up again with an error
after the submission), the app SHALL NOT submit them again, SHALL delete any stored credentials,
and SHALL leave the login page open for typing together with a message that the slip's
pass-phrase was not accepted (for example because the password was reset).

#### Scenario: Slip after a password reset
- **WHEN** a child scans an old slip whose pass-phrase was replaced by a password reset
- **THEN** the credentials are submitted once, the login page stays open with the notice that the slip's pass-phrase was not accepted, and no second submission follows

### Requirement: Login by address uses the issuer's login page
After connecting by address the app SHALL show the issuer's login page inside the app, where the
user logs in by typing as in the browser. The app SHALL use the authorization code flow with PKCE
(`S256`), the admin client from `GET /api/client-config` and a redirect URI under
`<base>/admin/`, so the issuer needs no configuration beyond the web admin app's. A login error
returned by the issuer SHALL be shown with the offer to try again.

#### Scenario: Adult logs in by address
- **WHEN** the publisher connects to `https://presserl.example.org` and enters username and password on the login page
- **THEN** the app is logged in and shows their start view

#### Scenario: No extra redirect URI needed
- **WHEN** the realm's `presserl-admin` client allows only the redirect URIs `https://presserl.example.org/admin/*`
- **THEN** login in the Android app succeeds

### Requirement: Scanned credentials keep the app logged in
Credentials from an accepted scan SHALL be stored on the device only after they led to a
successful login, encrypted with a key held in the Android Keystore and excluded from backups.
When the app starts with stored credentials it SHALL log in with them as after a scan. A login by
address SHALL store no credentials; it SHALL last as long as the issuer's session.

#### Scenario: Restart after a scan login
- **WHEN** a child who logged in by scanning closes the app and opens it the next day
- **THEN** the app logs in again without scanning or typing

#### Scenario: Restart after a login by address
- **WHEN** a user who logged in by address reopens the app after the issuer's session has ended
- **THEN** the issuer's login page asks for username and password again

### Requirement: Logout forgets the account and the server
Logout in the Android app SHALL discard the tokens, end the issuer session, delete stored
credentials and the remembered server address, and return to the start screen.

#### Scenario: Handing the phone to someone else
- **WHEN** a logged-in child chooses logout and another child scans their own slip
- **THEN** the app is logged in as the second child, and the first child's credentials are no longer stored

### Requirement: System back navigates within the app
The system back action SHALL do what the app's own back or cancel action on the current screen
does; on the start view after login and on the start screen it SHALL leave the app. Unsaved input
SHALL be handled as by the screen's own back action.

#### Scenario: Back from the editor
- **WHEN** a reporter opens an article from "My articles" and presses the system back button
- **THEN** the app returns to "My articles"

### Requirement: Images from the photo library or the camera
On Android, "choose images" SHALL open the system photo picker with multiple selection, and "take
photo" SHALL open the camera. The app SHALL NOT request permission to read all photos or files.
Chosen and taken images SHALL be uploaded as in the web app.

#### Scenario: Upload from the gallery
- **WHEN** a reporter chooses two photos in the photo picker
- **THEN** both are uploaded and appear in "Images"

#### Scenario: Photo taken with the camera
- **WHEN** a reporter takes a photo with "take photo"
- **THEN** it is uploaded and appears in "Images"

### Requirement: Printing the account slip on Android
On Android, printing an account slip SHALL open the system print dialog with the slip alone,
showing the same content as the web print (newspaper name, heading, rows with the large
pass-phrase, QR code with its hint line, note), so it can be printed or saved as PDF.

#### Scenario: Slip printed from the phone
- **WHEN** an editor creates an account in the Android app and chooses to print the slip
- **THEN** the system print dialog shows a one-page slip whose QR code, scanned with the app, logs in the new account
