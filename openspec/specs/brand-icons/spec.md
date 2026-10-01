# brand-icons Specification

## Purpose

Keeps Presserl's brand artwork in one place in the repository, so that the reader, the admin
apps and the store listing all show the same icon and change together.

## Requirements

### Requirement: Brand artwork has a single source
The repository SHALL keep the Presserl brand artwork in the top-level directory `icons/`: the
vector sources, the script that generates every variant from them, and the generated files —
a 512×512 store icon, a 1024×500 feature graphic in German and in English, a favicon as SVG,
a multi-size ICO (16, 32 and 48 px) and PNGs at 16, 32, 48, 180, 192 and 512 px. The motif SHALL
use the default theme's colours (paper `#fbf8f1`, ink `#1d1b18`, accent `#a8321d`, the eight
section colours) and the reader's bundled Playfair Display face for the "P"; the SVG sources SHALL
contain the letter as an outline, so they render without any font installed. A README in
`icons/` SHALL say how to regenerate the files and where each one is used.

#### Scenario: Regenerating the artwork
- **WHEN** a developer runs the generator as described in `icons/README.md`
- **THEN** every file listed above is written again from the sources, with the same sizes

#### Scenario: SVG without fonts
- **WHEN** `icons/favicon.svg` is rendered on a machine without Playfair Display
- **THEN** the "P" looks the same as on a machine that has the face

### Requirement: Every surface uses copies of the brand artwork
Each place that shows the brand icon — reader, admin web app, Android launcher, store listing —
SHALL use files copied or generated from `icons/`, never an independently drawn variant. The
repository SHALL provide a check that reports every consumer copy that differs from its source
in `icons/`.

#### Scenario: Copies are current
- **WHEN** the check runs after the artwork was regenerated but not distributed
- **THEN** it names each outdated copy and fails

#### Scenario: Copies match
- **WHEN** the check runs after distributing the files
- **THEN** it reports no difference and succeeds

### Requirement: Store graphics meet Google Play's format
The store icon SHALL be a 512×512 PNG filling the whole square (Google Play applies the mask) and
the feature graphics SHALL be 1024×500 PNGs without an alpha channel. The feature graphic SHALL
show the brand icon, the name "Presserl" and a short claim in the listing's language.

#### Scenario: Store graphic formats
- **WHEN** the store graphics are inspected
- **THEN** the icon is 512×512 PNG, each feature graphic is 1024×500 PNG with no alpha channel, and the German one carries German text, the English one English text
