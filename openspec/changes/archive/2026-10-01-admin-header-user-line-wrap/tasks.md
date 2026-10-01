## 1. Admin

- [x] 1.1 `ui/App.kt` `Header`: replace the user line's `TextButton` with a start-aligned clickable `Text` (D1): `Role.Button`, `heightIn(min = 44.dp)`, primary colour, `bodyMedium`, opens "My account"
- [x] 1.2 `./gradlew check` in `admin/` (common, Wasm and Android host tests stay green)

## 2. Verification

- [x] 2.1 Web at 390 px (Playwright against a local backend with an account holding three section roles): user line wraps start-aligned, no clipped characters, Tab reaches it and Enter opens "My account"; at 1280 px the header is one row as before
- [x] 2.2 Android debug build on the A54 against staging as Lena Berger: header lines start at the newspaper name's edge, tapping the user line opens "My account"
