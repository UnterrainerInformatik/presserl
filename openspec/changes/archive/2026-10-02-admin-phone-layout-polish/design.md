## Context

See proposal.md (Why). Current state of the code involved:

- `ui/App.kt` `Header` uses `BoxWithConstraints` with `NARROW_HEADER = 720.dp`. Below that width it
  has two rows: identity + "Log out", then a `horizontalScroll` row of `NavButton`s.
- `ui/editor/EditorScreen.kt` `Editor` is a `Column` with a fixed `FlowRow` status row, fixed
  banners, the article in a `weight(1f)` `verticalScroll` column and a fixed `BottomBar`. The bottom
  bar has its own `NARROW_BAR = 720.dp` switch.
- On Android, `AndroidApp` wraps everything in `safeDrawingPadding()`, so the content already shrinks
  above the keyboard. Nothing in the app reacts to whether the keyboard is shown.
- `ui/issue/IssueScreens.kt` `DateField` is an `OutlinedTextField` parsed by `IssueDates.parseDate`.
- `FilePicker.android.kt` hands out `PickableFile`s that read their bytes lazily. The photo picker
  uses `PickVisualMedia.ImageOnly`, which includes HEIC/HEIF. The camera (`TakePicture`) always
  writes JPEG.
- Every UI symbol is an `ImageVector` in `ui/Icons.kt` (requirement "UI symbols render without
  downloaded fonts"), so the menu and calendar symbols have to be drawn there too.

## Goals / Non-Goals

**Goals:**
- One shared "compact layout" decision, so the header and the editor switch at the same width.
- Keyboard awareness only where the platform reports it reliably (Android). Other platforms see
  "keyboard hidden".
- Keep the pure logic (date conversion, image-type sniffing, renaming) in `commonMain` and unit-test
  it there.

**Non-Goals:**
- No animated collapse-on-scroll and no shared scroll-state plumbing across screens.
- No HEIC support in the web app or on the server.

## Decisions

### D1 — One `LocalCompactLayout` for header and editor
`LoggedIn` measures its width once (`BoxWithConstraints`, < 720 dp) and provides it through a
`CompositionLocal`. `Header`, `Editor` and `BottomBar` read it instead of measuring themselves, and
`NARROW_HEADER`/`NARROW_BAR` become one constant.
*Alternative:* keep a `BoxWithConstraints` per component. The editor's status row cannot measure
the window from inside its own column, and the two thresholds could drift apart.

### D2 — Compact header: name + menu button with a `DropdownMenu`
The row holds the newspaper name (`weight(1f)`, wraps) and an `IconButton` with a new
`Icons.Menu` vector (content description "Menu"/"Menü"). The `DropdownMenu` items are:
- the navigation entries (selected one with a leading check vector and the selected-item colour),
- a divider,
- the user line as a wrapping, start-aligned item that opens "My account",
- "Log out".

Every item closes the menu.
*Alternative:* a wrapping `FlowRow` of nav buttons. The user picked the compact menu, which also
keeps the header to a single row.

### D3 — Keyboard visibility as an expect/actual
`@Composable expect fun keyboardVisible(): Boolean`:
- Android actual: `WindowInsets.isImeVisible`.
- wasmJs actual: `false`. Mobile browsers resize the viewport instead, and Compose for Web reports no
  IME insets reliably.

`hideChrome = compact && keyboardVisible()`. `LoggedIn` skips `Header` + divider while
`hideChrome` is true.
*Alternative:* `WindowInsets.ime.getBottom(density) > 0` in common code. It needs the same
platform caveat and reads less clearly.

### D4 — Editor notices move into the scroll column when compact
Extract the status `FlowRow` and the notice banners (errors, correcting, last changed by, locked,
waiting/read-only, rejection) into `EditorNotices`. When compact, it is the first child of the
scrolling column; otherwise it stays above the column as today. The conflict banner is rendered
separately and always stays fixed, because while it shows nothing can be saved.

### D5 — Compact bottom bar while typing
When `hideChrome` is true, `BottomBar` renders one `Row`:
- undo and redo as `IconButton`s with the existing `Icons.Undo`/`Icons.Redo` vectors and
  `contentDescription` = the "Undo"/"Redo" strings,
- the save-state text (single line, ellipsis allowed here only because it is a status, not a word
  list; it stays short in practice).

The article actions are not composed until the keyboard closes. The dialogs they open are not
affected, because opening a dialog moves focus out of the text field and closes the keyboard first.

### D6 — Date picker next to the text field
`DateField` gets a trailing `IconButton` with a new `Icons.Calendar` vector that opens Material3
`DatePickerDialog` + `DatePicker` (`rememberDatePickerState(initialSelectedDateMillis = …)`). Two
pure helpers go into `IssueDates.kt`:
- `isoToEpochMillis(iso): Long` (UTC midnight),
- `epochMillisToIso(millis): String`,

implemented with the days-from-civil algorithm (no kotlinx-datetime dependency). On confirm the ISO
string goes through the existing `onChange`, so validation and saving stay as they are. An empty or
invalid field passes `null` (the picker opens at the current month, nothing selected).
*Alternative:* replace typing with a read-only field. That is worse on desktop and would change the
existing requirement.

### D7 — Convert non-server image types on the Android device
In `commonMain` media:
- `serverImageType(bytes): Boolean` sniffs the JPEG (`FF D8 FF`), PNG (8-byte signature) and WebP
  (`RIFF....WEBP`) magic.
- `jpegName(name)` replaces or appends the extension with `.jpg`.

In `FilePicker.android.kt` `contentFile`:
- if `contentResolver.getType(uri)` is not one of `image/jpeg|png|webp`, the displayed name becomes
  `jpegName(name)`;
- `read()` returns the original bytes when `serverImageType` holds;
- otherwise it decodes with `ImageDecoder` (API 28+, applies EXIF orientation; `BitmapFactory`
  below 28) and compresses to JPEG at quality 90 on `Dispatchers.IO`;
- if decoding fails, it returns the original bytes, so the server answers 415 and the dialog shows
  the existing "not a supported image type" text.

The camera path is untouched (always JPEG).
*Alternatives:*
- `PickVisualMedia.SingleMimeType("image/jpeg")` would hide HEIC photos entirely, which is confusing
  and allows only one type.
- Server-side HEIC decoding needs a native library on the server; that is out of scope.

## Risks / Trade-offs

- [Size shown in the upload dialog is the original HEIC size, not the converted JPEG's] → The
  dialog only shows it for orientation. The max-size check is done by the server on the bytes
  actually sent, so the result is correct either way.
- [Converted JPEGs lose EXIF metadata (camera, GPS)] → Acceptable and arguably desirable for
  published media; the orientation is applied to the pixels, so nothing turns sideways.
- [Large HEIC (e.g. 50 MP) needs ~200 MB as a bitmap] → `ImageDecoder` target-size cap: decode at
  most 20000 px per side / 50 MP (the server limits). Above that, upload the original bytes and
  let the server refuse it. Memory is still high for big photos; accepted for now.
- [Android 8.0/8.1 (API 26/27) cannot decode HEIC] → Falls back to the original bytes and the
  usual 415 message, which is today's behaviour.
- [Material3 `DatePicker` strings may not be localized to German in Compose Multiplatform] →
  Check in the web app with a German browser during apply. If English only, record it in
  open-proposals instead of building a custom picker.
- [Hiding the header while typing removes navigation] → Navigation while typing is not needed.
  Closing the keyboard (back gesture) brings it back immediately.

## Migration Plan

Client-only change: ships with the next admin web build and Android internal release. Rollback is
reverting the commit; no data or contract involved.
