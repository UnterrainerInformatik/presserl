## Why

The phone-size check in android-app-qr-login (Pixel 7, 412 dp) found that the admin app works on
phones but is cramped. Header, the editor's status row and the editor's bottom bar stay fixed and
take about 40 % of the height, so with the keyboard open only a few lines of the paragraph being
written are visible. The navigation row also cuts off its last entry ("Acc…") without any hint
that more follows, issue dates have to be typed as `YYYY-MM-DD`, and the photo picker offers HEIC
photos that the server then refuses. Reporters are now testing the Android app through Play, so
the phone is becoming the main device for writing.

## What Changes

- On narrow screens (< 720 dp) the header becomes one compact row: the newspaper name and a menu
  button. The menu holds the navigation entries (current one marked), the user line (opens "My
  account") and "Log out". The second navigation row with its sideways scrolling goes away, so
  every entry is visible in the menu. Wide screens keep the single-row header.
- While the on-screen keyboard is shown on a narrow screen, the header is hidden completely.
- On narrow screens the editor's status row (back, status, revisions, reader link) and its
  informational banners scroll with the article instead of staying fixed. The conflict banner
  stays fixed, because it blocks saving.
- While the keyboard is shown on a narrow screen, the editor's bottom bar shrinks to one line with
  icon-only undo and redo (accessible names kept) and the save state. The article actions come back
  as soon as the keyboard closes.
- Issue publication dates get a calendar button next to the text field. It opens a date picker,
  and the chosen date fills the field. Typing `YYYY-MM-DD` still works, so desktop use stays
  unchanged.
- On Android, chosen or taken images that are neither JPEG, PNG nor WebP (in practice HEIC/HEIF)
  are converted to JPEG on the device before upload, with orientation applied. Images the device
  cannot decode still fail with the usual "not a supported image type" message.

## Non-goals

- No changes to the backend or the REST contract. The server keeps accepting only JPEG, PNG and
  WebP.
- No collapse-on-scroll animation for the header. The compact row is small enough to stay.
- No layout changes for wide screens (≥ 720 dp) other than the date picker button.
- No change to the web file picker. It already offers only JPEG, PNG and WebP, and browsers
  convert HEIC themselves where they offer it.
- iOS stays out of scope (separate M8 entry).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `admin-shell`: "Header and editor bar fit narrow screens" changes to a compact header with a
  menu, a hidden header and a compact editor bar while the keyboard is shown, and an editor status
  row that scrolls with the article.
- `admin-issues`: the publication date (new issue dialog and issue details) can be chosen with a
  date picker as well as typed.
- `admin-android`: images that are not JPEG, PNG or WebP are converted to JPEG on the device
  before upload.

## Impact

- Platforms: **admin** only (shared Compose code plus the Android source set). Backend, reader,
  deploy and docs are unaffected. `ai/primer/endpoints.md` is unchanged.
- Code: `ui/App.kt` (Header), `ui/editor/EditorScreen.kt` (status row, banners, BottomBar),
  `ui/issue/IssueScreens.kt` + `IssueDates.kt` (date picker, epoch-day conversion),
  `androidMain/.../media/FilePicker.android.kt` (conversion), new localized strings (de/en).
- Dependencies: none new. Material3's `DatePicker` is already part of `compose-material3`, and
  Android's `ImageDecoder`/`Bitmap.compress` are platform APIs.
- Release: shipped with the next Android internal release and admin web build.
