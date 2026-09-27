## MODIFIED Requirements

### Requirement: Default section
The section named by the setting `section.default` (compared ignoring case) SHALL be the default
section. At startup the system SHALL create it (at the last position, with the default palette
colour) when no section exists at all. `POST /api/articles` without `sectionId` SHALL file the
article under the default section when it exists and the requesting user may write there,
otherwise under the first section by position the user may write in. When no section exists at
all, the default section SHALL be created and used.

#### Scenario: Fresh installation
- **WHEN** the backend starts with no section and `section.default` resolving to `General`
- **THEN** a section `General` exists afterwards

#### Scenario: Existing articles are filed
- **WHEN** articles without a section exist from an earlier version, sections `Sport` and `Kultur` exist in this order, and the backend starts
- **THEN** every such article belongs to `Sport` (the database upgrade files them, see sections "Every article has a section in the database")

#### Scenario: Sections exist at startup
- **WHEN** sections `Sport` and `Kultur` exist, none is named like the default section, and the backend starts
- **THEN** no section is created

#### Scenario: Publisher creates without section
- **WHEN** sections `General` and `Sport` exist and the publisher posts `{}` to `/api/articles`
- **THEN** the article belongs to `General`

#### Scenario: Reporter creates without section
- **WHEN** `reader` is `REPORTER` in `Kultur` only, sections `General`, `Sport` and `Kultur` exist, and `reader` posts `{}` to `/api/articles`
- **THEN** the article belongs to `Kultur`

#### Scenario: Default section was renamed
- **WHEN** the section `General` was renamed to `Allerlei`, sections `Allerlei` and `Sport` exist in this order and the publisher posts `{}` to `/api/articles`
- **THEN** no section is created and the article belongs to `Allerlei`

#### Scenario: All sections gone
- **WHEN** no section exists and the publisher posts `{}` to `/api/articles`
- **THEN** a section `General` is created and the article belongs to it
