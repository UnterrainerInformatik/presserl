## Why

On a phone the admin header's user line (display name and roles, e.g. "Lena Berger · Ressortleiter ·
Dorfleben, Redakteur · Sport, Redakteur · Kultur") wraps onto several lines, and wrapped lines are
cut off at the left edge ("ena Berger", "edakteur"). Users with more than one section role see a
broken header on every screen, and the Play store screenshots (android-play-publishing, task 3.4)
would show it.

## What Changes

- The user line wraps start-aligned inside the header; every line is fully visible at phone width.
- It stays the control that opens "My account": clickable, exposed as a button to accessibility
  services, at least 44 dp tall.
- Checked by screenshot at phone width: the web app at 390 px (Playwright) and the Android app on
  the A54. The admin has no Compose UI test setup yet; introducing one is out of scope (see design).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `admin-shell`: "Header and editor bar fit narrow screens" gains the rule that a wrapping user line
  is start-aligned and fully visible, with a phone-width scenario.

## Non-goals

- Shortening or abbreviating the role list.
- Any other header layout change (navigation row, "Log out", wide-screen single row).
- The "My account" screen itself.

## Impact

- **admin** only: `admin/composeApp/src/commonMain/kotlin/info/unterrainer/presserl/admin/ui/App.kt`
  (header identity block). Web (Wasm) and Android both use it.
- backend, reader, deploy, docs: none. No REST contract change.
