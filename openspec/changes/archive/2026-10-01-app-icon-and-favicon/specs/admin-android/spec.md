## ADDED Requirements

### Requirement: Launcher icon shows the Presserl motif
The Android app's launcher icon SHALL be an adaptive icon showing the brand motif from `icons/`:
the newspaper front page on the accent red background, with the whole front page inside the
adaptive icon's safe zone so no launcher mask cuts it. It SHALL provide a monochrome layer that
shows the front page with its "P" as a single-colour shape for themed icons.

#### Scenario: Launcher with a round mask
- **WHEN** the app is installed on a launcher that masks icons as circles
- **THEN** the front page with the "P" is fully visible on the red background

#### Scenario: Themed icons
- **WHEN** themed icons are enabled on Android 13 or later
- **THEN** the launcher shows the monochrome front page with the "P" recognisable
