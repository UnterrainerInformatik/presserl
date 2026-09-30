## ADDED Requirements

### Requirement: Section colour markers follow the colour scheme
The admin app SHALL draw each of the eight section palette colours (markers and swatches) in a
variant that stays clearly visible on the current background: the existing tones on the light
scheme and brighter tones on the dark scheme. Both variants of one palette key SHALL be
recognisably the same hue, and the eight colours SHALL stay distinguishable from one another in
both schemes. The stored palette key SHALL NOT depend on the scheme.

#### Scenario: Markers on a dark background
- **WHEN** a user with a dark system preference opens the sections screen
- **THEN** every section marker is drawn in the brighter dark-scheme tone of its colour

#### Scenario: Picking a colour in dark
- **WHEN** a user with a dark system preference picks the green swatch and saves the section
- **THEN** the section is stored with the colour `green`, and a user with a light preference sees it with the light-scheme green marker
