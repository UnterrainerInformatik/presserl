# admin-shell Specification

## Purpose

Delivers the administration web app from the backend and lets newsroom members log in and out
through the operator's Keycloak.

## Requirements

### Requirement: Admin app is served at /admin/
The system SHALL serve the administration web app at `/admin/` from the same origin as the API
and the reader. Requests to `/admin` SHALL redirect to `/admin/`.

#### Scenario: Opening the admin app
- **WHEN** a browser requests `GET /admin/`
- **THEN** the response is `200` HTML that loads the app's scripts and Wasm module from `/admin/`

#### Scenario: Missing trailing slash
- **WHEN** a browser requests `GET /admin`
- **THEN** it is redirected to `/admin/`

### Requirement: Admin pages allow only the needed origins
Admin pages SHALL be sent with a `Content-Security-Policy` that restricts all sources to `'self'`
except that `connect-src` additionally allows the origin of the configured OIDC issuer,
`script-src` additionally allows `'wasm-unsafe-eval'`, and `style-src` additionally allows the
hashes of the style elements the UI framework injects (listed with the admin bundle and verified
by the admin build). `'unsafe-inline'` SHALL NOT be allowed.

#### Scenario: CSP header on the admin app
- **WHEN** a browser requests `GET /admin/`
- **THEN** the `Content-Security-Policy` header has `default-src 'self'` and a `connect-src` listing `'self'` and the issuer origin only

#### Scenario: Framework styles are allowed by hash only
- **WHEN** the admin app starts in a browser
- **THEN** no CSP violation is reported, and `style-src` lists `'self'` plus the hashes from the admin bundle and no `'unsafe-inline'`

### Requirement: Login with authorization code and PKCE
The admin app SHALL obtain the OIDC configuration from `GET /api/client-config`, redirect an
unauthenticated user to the issuer with the authorization code flow and PKCE (`S256`), exchange the
code for tokens, and keep tokens only in memory. After login it SHALL show a header with the
newspaper name, the user's display name and roles as returned by `GET /api/me` (including
"Redakteur (ohne Ressort)" when `sectionlessReporter` is true). Below the header it SHALL open:

- the article list when `allowedActions` contains `WRITE_ARTICLES`;
- otherwise the "Images" view when it contains `USE_MEDIA`;
- otherwise a notice that the account holds no role for writing articles, together with the
  logout.

#### Scenario: Publisher logs in
- **WHEN** the bootstrapped publisher opens `/admin/`, is sent to Keycloak and enters valid credentials
- **THEN** they return to `/admin/`, see the newspaper name, their display name and the role `PUBLISHER` in the header, and the list "My articles"

#### Scenario: Reader logs in
- **WHEN** `reader`, who holds only `READER` and no section role, logs in to the admin app
- **THEN** the header shows their display name and the role `READER`, no navigation entries, and instead of an article list the notice that the account holds no role for writing articles; no request to `/api/articles` is made

#### Scenario: Sectionless reporter logs in
- **WHEN** a user whose `allowedActions` are `["USE_MEDIA"]` logs in
- **THEN** the app opens the "Images" view, the header shows "Redakteur (ohne Ressort)" and no navigation entries, and no request to `/api/articles` is made

#### Scenario: Login cancelled or failed
- **WHEN** Keycloak redirects back with an `error` parameter
- **THEN** the app shows a message that login failed and offers to try again

### Requirement: Logout
The admin app SHALL offer a logout that discards the tokens and ends the Keycloak session via the
issuer's end-session endpoint, returning to `/admin/`.

#### Scenario: Publisher logs out
- **WHEN** a logged-in user chooses logout
- **THEN** the Keycloak session ends and opening `/admin/` again requires entering credentials

### Requirement: Admin bundle caching survives deploys
Responses under `/admin/` SHALL carry an explicit `Cache-Control` header. Files with
content-hashed names (`*.wasm`) SHALL be sent with `public, max-age=31536000, immutable`. All
other responses under `/admin/` SHALL be sent with `no-cache`, so browsers and shared caches
revalidate them on every load and a new deploy takes effect on the next page load. Caching of
responses outside `/admin/` SHALL NOT be changed.

#### Scenario: Entry files are revalidated
- **WHEN** a browser requests `GET /admin/`, `GET /admin/startup.js`, `GET /admin/composeApp.js` or `GET /admin/styles.css`
- **THEN** the response carries `Cache-Control: no-cache` and no `immutable`

#### Scenario: Hashed Wasm modules are cached long-term
- **WHEN** a browser requests a `.wasm` file under `/admin/`
- **THEN** the response carries `Cache-Control: public, max-age=31536000, immutable`

#### Scenario: Unchanged entry file is not downloaded again
- **WHEN** a browser revalidates `GET /admin/composeApp.js` with the `If-Modified-Since` value it received earlier
- **THEN** the response is `304` with `Cache-Control: no-cache`

#### Scenario: Deploy with a new Wasm build
- **WHEN** a new version is deployed whose `composeApp.js` references different `.wasm` file names and a user reloads `/admin/`
- **THEN** the browser fetches the new `composeApp.js` and loads the new `.wasm` files without a `404`

#### Scenario: Other paths keep their caching
- **WHEN** a client requests `GET /api/newspaper`
- **THEN** the `Cache-Control` header is the same as before this change

### Requirement: Admin bundle is sent pre-compressed
The `.wasm` and `.js` files of the admin bundle in the container image SHALL be accompanied by a
Brotli-compressed variant produced at build time with the highest compression quality. When a
client requests such a file and its `Accept-Encoding` header accepts `br`, the response SHALL carry
the compressed variant with `Content-Encoding: br` and the `Content-Type` of the original file.
Otherwise the response SHALL carry the original file without `Content-Encoding`. Responses for
these files SHALL carry `Vary: Accept-Encoding`. The `Cache-Control` values and conditional-request
behaviour of these responses SHALL be the same as for the original files. A file without a
compressed variant SHALL be served as before.

#### Scenario: Browser accepting Brotli
- **WHEN** a browser requests a `.wasm` file under `/admin/` with `Accept-Encoding: gzip, deflate, br`
- **THEN** the response carries `Content-Encoding: br`, `Content-Type: application/wasm`, `Vary: Accept-Encoding` and `Cache-Control: public, max-age=31536000, immutable`, and its body decompresses to the original file

#### Scenario: Client without Brotli
- **WHEN** a client requests the same `.wasm` file without `br` in `Accept-Encoding`
- **THEN** the response carries the original file, no `Content-Encoding` and `Vary: Accept-Encoding`

#### Scenario: Compressed entry script is revalidated
- **WHEN** a browser accepting Brotli requests `GET /admin/composeApp.js`
- **THEN** the response carries `Content-Encoding: br`, a JavaScript `Content-Type` and `Cache-Control: no-cache`

#### Scenario: Unchanged compressed entry script
- **WHEN** a browser accepting Brotli revalidates `GET /admin/composeApp.js` with the `If-Modified-Since` value it received earlier
- **THEN** the response is `304` with `Cache-Control: no-cache`

#### Scenario: File without a compressed variant
- **WHEN** a browser accepting Brotli requests `GET /admin/styles.css`
- **THEN** the response is the original file without `Content-Encoding`, as before

#### Scenario: Smaller download
- **WHEN** a browser with an empty cache opens `/admin/` on a deployed image
- **THEN** the Wasm modules are transferred Brotli-compressed at the build-time size, not uncompressed

### Requirement: Start-up failures show a notice instead of a blank page
Before the admin app starts, the page SHALL check that the browser supports WebAssembly and can
create a WebGL context. The app SHALL only be loaded when both checks pass. When a check fails,
the page SHALL show a readable HTML notice instead of a blank page, naming what is missing and how
to enable it; for WebGL the notice SHALL list allowing WebGL for the site in the browser, the
Firefox setting `webgl.disabled`, and exceptions in fingerprinting or canvas blockers. When the
checks pass but the app fails to load or throws an uncaught error before it has drawn its first
frame, the page SHALL show a generic notice that the app could not start, with a hint to reload.
The notice SHALL be in English when the browser's preferred language is English and in German
otherwise. The notice SHALL work under the admin Content-Security-Policy without inline scripts or
inline styles, and the policy SHALL NOT be relaxed for it.

#### Scenario: WebGL disabled
- **WHEN** a user whose browser has WebGL disabled opens `/admin/`
- **THEN** the page shows a notice explaining that the admin app needs WebGL and how to allow it, and the app is not loaded

#### Scenario: WebGL available
- **WHEN** a user whose browser supports WebAssembly and WebGL opens `/admin/`
- **THEN** the admin app starts as before and no notice is shown

#### Scenario: WebAssembly unsupported
- **WHEN** a user whose browser does not support WebAssembly opens `/admin/`
- **THEN** the page shows a notice that the browser is not supported and the app is not loaded

#### Scenario: App fails before its first frame
- **WHEN** the checks pass but loading the app fails or it throws an uncaught error before drawing its first frame
- **THEN** the page shows a notice that the app could not start, with a hint to reload

#### Scenario: Notice language
- **WHEN** a user whose browser prefers `en-GB` and has WebGL disabled opens `/admin/`
- **THEN** the notice is in English; with `de-AT` it is in German

#### Scenario: No CSP violation
- **WHEN** the notice is shown
- **THEN** the browser reports no Content-Security-Policy violation caused by the page's own scripts or styles

### Requirement: A loading indicator replaces the blank page during start-up
While the admin app is loading, the page SHALL show a loading indicator with a short text saying
that the administration is loading, instead of a blank page. The indicator SHALL appear only when
the app has not drawn its first frame within 500 ms of the start-up checks passing, and it SHALL be
removed as soon as the app draws its first frame. When a start-up notice is shown, the notice
SHALL replace the indicator. The indicator SHALL NOT be shown when a start-up check fails. Its text
SHALL follow the same language rule as the start-up notices (English when the browser prefers
English, German otherwise). Any animation SHALL stop when the user prefers reduced motion. The
indicator SHALL work under the admin Content-Security-Policy without inline scripts or inline
styles, and the policy SHALL NOT be relaxed for it.

#### Scenario: Slow first load
- **WHEN** a user with an empty browser cache opens `/admin/` and the app takes several seconds to load
- **THEN** the page shows the loading indicator after at most 500 ms until the app draws its first frame or navigates to the login

#### Scenario: Fast load does not flash
- **WHEN** the app draws its first frame within 500 ms
- **THEN** the loading indicator is never shown

#### Scenario: Indicator gives way to the app
- **WHEN** the loading indicator is shown and the app draws its first frame
- **THEN** the indicator is removed and the app is visible

#### Scenario: Indicator gives way to a notice
- **WHEN** the loading indicator is shown and loading the app fails
- **THEN** the indicator is removed and the "could not start" notice is shown

#### Scenario: Failed check shows no indicator
- **WHEN** a user whose browser has WebGL disabled opens `/admin/`
- **THEN** only the WebGL notice is shown, without a loading indicator

#### Scenario: Indicator language
- **WHEN** a user whose browser prefers `en-GB` sees the loading indicator
- **THEN** its text is English; with `de-AT` it is German

#### Scenario: No CSP violation
- **WHEN** the loading indicator is shown
- **THEN** the browser reports no Content-Security-Policy violation caused by the page's own scripts or styles

### Requirement: Localized user interface
All texts of the admin app SHALL come from localized resources in German and English. The app
SHALL use English when the browser's preferred language is English and German otherwise. Role and
status names SHALL be shown with their localized labels.

#### Scenario: German browser
- **WHEN** a user whose browser prefers `de-AT` opens the admin app
- **THEN** labels read e.g. "Schlagzeile", "Dachzeile" and "Abmelden"

#### Scenario: English browser
- **WHEN** a user whose browser prefers `en-GB` opens the admin app
- **THEN** labels read e.g. "Headline", "Kicker" and "Log out"

### Requirement: Header entries follow allowed actions, including images
The admin app SHALL decide which header entries it offers from `allowedActions` of `GET /api/me`
only, never from `roles`, `sectionRoles` or `sectionlessReporter`. The entries, in this order, are:

| Entry | Offered with |
|---|---|
| "Articles" | `WRITE_ARTICLES` |
| "Images" | `USE_MEDIA` |
| "Sections" | `MANAGE_SECTIONS` or `ASSIGN_SECTION_ROLES` |
| "Issues" | `MANAGE_ISSUES` |
| "Accounts" | `ADMINISTER_ACCOUNTS` |
| "Newspaper" | `CONFIGURE_NEWSPAPER` |

When fewer than two entries remain, the header SHALL show no entries. Values of `allowedActions`
the app does not know SHALL be ignored. The entries are only visibility; the server enforces
access.

#### Scenario: Publisher sees every entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "USE_MEDIA", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"]` is logged in
- **THEN** the header shows "Articles", "Images", "Sections", "Issues", "Accounts" and "Newspaper"

#### Scenario: Section editor has no issues entry
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "USE_MEDIA", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"]` is logged in
- **THEN** the header shows "Articles", "Images", "Sections" and "Accounts" and no "Issues"

#### Scenario: Reporter sees articles and images
- **WHEN** a user whose `allowedActions` are `["WRITE_ARTICLES", "USE_MEDIA"]` is logged in
- **THEN** the header shows "Articles" and "Images" and the article list is shown

#### Scenario: Sectionless reporter gets no entries
- **WHEN** a user whose `allowedActions` are `["USE_MEDIA"]` is logged in
- **THEN** the header shows no entries and the "Images" view is shown

#### Scenario: Unknown actions are ignored
- **WHEN** `GET /api/me` answers `allowedActions` `["WRITE_ARTICLES", "USE_MEDIA", "REVIEW"]`
- **THEN** the app starts normally and the header shows "Articles" and "Images"

### Requirement: Keyboard use survives leaving a text field
After keyboard focus moves from a text field to a control that is not a text field (for example
with Tab), the admin app SHALL keep receiving keys: Tab, Shift+Tab, Enter, Space and Escape SHALL
keep working on the newly focused control without clicking into the app first.

#### Scenario: Tab from a text field onto a button
- **WHEN** a user types in the subheadline field, presses Tab onto its question-mark button and then presses Escape
- **THEN** the explanation opens on focus, closes on Escape, and a further Tab moves focus on

### Requirement: Header and editor bar fit narrow screens
On screens narrower than about 720 dp the header SHALL be a single compact row with the newspaper
name and a menu button. The menu SHALL list every navigation entry the user has (the current one
marked), the user line, which opens "My account", and "Log out". Choosing an entry SHALL close the
menu. With no navigation entries, the menu SHALL still offer the user line and "Log out". Wider
screens SHALL keep the single-row header with the user line, the navigation entries and "Log out"
side by side.

On narrow screens the editor's status row (back, status, revisions, reader link) and its notice
banners SHALL scroll together with the article. The conflict banner SHALL stay fixed above the
article. The editor's bottom bar (undo, redo, save state and the article actions) SHALL wrap as one
group, keeping the primary action at the end.

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
- **WHEN** a publisher opens an article in the editor in a 390 px wide window without a keyboard shown
- **THEN** "Undo", "Redo", the save state and the actions are shown with unbroken words, wrapping onto further lines as needed

#### Scenario: Desktop width
- **WHEN** the window is 1280 px wide
- **THEN** the header is a single row with the user line, the navigation entries and "Log out", and the editor's status row stays fixed above the article as before

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

### Requirement: User line opens My account
The header's user line (display name and roles) SHALL be a button that opens the "My account"
screen, for every logged-in user, independent of `allowedActions` and of the number of navigation
entries. A pending deletion request (`deletionRequestedAt` of `GET /api/me`) SHALL be marked on the
user line.

#### Scenario: Sectionless reporter opens My account
- **WHEN** a user whose `allowedActions` are `["USE_MEDIA"]` taps the user line
- **THEN** the "My account" screen is shown

#### Scenario: Pending request marked
- **WHEN** a user with a pending deletion request is logged in
- **THEN** the user line shows the "deletion requested" marker

### Requirement: Admin app shows the Presserl favicon
The admin web app's page SHALL link the Presserl favicon as SVG and as ICO, served from the admin
app's own path, so the browser tab shows the brand icon. The icons SHALL load under the admin
pages' Content-Security-Policy without a violation.

#### Scenario: Favicon in the admin tab
- **WHEN** a browser opens `/admin/`
- **THEN** the page links an SVG and an ICO favicon under `/admin/`, both answer `200`, and no CSP violation is reported
