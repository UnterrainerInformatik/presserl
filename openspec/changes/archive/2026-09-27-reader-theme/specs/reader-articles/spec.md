## ADDED Requirements

### Requirement: Stories and articles show their section
Every story on the front page and the article page SHALL show the name of the article's section
together with a marker in the section's colour. Section pages do not exist yet, so the section
name SHALL NOT be a link.

#### Scenario: Story in a section
- **WHEN** a published article belongs to section `Sport` with colour `blue` and a visitor requests `GET /`
- **THEN** the article's story shows `Sport` with a marker using the `blue` section colour

#### Scenario: Article page
- **WHEN** a visitor opens the article page of a published article in section `Sport`
- **THEN** the page shows `Sport` with its colour marker above the headline
