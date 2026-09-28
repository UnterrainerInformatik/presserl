## ADDED Requirements

### Requirement: Lead image on the article page
When the live revision of a published article has a lead image, the article page SHALL show it
after the headline block (kicker, headline, subheadline) and before the lead, as a figure with
class `lead-image` containing an `<img>` whose `src` is `/media/{id}/web`, whose `srcset` offers
the `thumbnail` and `web` renditions with their widths, and whose `width` and `height` attributes
are those of the `web` rendition. A non-empty caption SHALL be shown below the image as
`<figcaption class="lead-image__caption">`, escaped like all other text. Without a lead image no
figure SHALL be rendered. Only the live revision's lead image SHALL ever appear.

#### Scenario: Article with lead image and caption
- **WHEN** a visitor opens a published article whose live revision has media 17 as lead image with caption `Our cat Minka`
- **THEN** the page contains a `figure.lead-image` with an image from `/media/17/web` and the figcaption `Our cat Minka`

#### Scenario: Caption with markup
- **WHEN** the caption is `<b>Minka</b>`
- **THEN** the page shows the caption as text and contains no `<b>` element from it

#### Scenario: Unpublished image change
- **WHEN** the live revision has lead image 17 and a newer working revision has lead image 18
- **THEN** the article page shows media 17 and never references `/media/18/`

### Requirement: Lead images on the front page
On the front page the lead story SHALL show its live revision's lead image (if any) using the
`web` rendition, and every other story card SHALL show its lead image (if any) using the
`thumbnail` rendition, each with `width` and `height` of the rendition used and the caption as
`figcaption` when it is not empty. Stories without a lead image SHALL be rendered as before.

#### Scenario: Lead story with image
- **WHEN** the newest published article has lead image 17
- **THEN** the lead story contains an image from `/media/17/web`

#### Scenario: Card with image
- **WHEN** an older published article has lead image 18
- **THEN** its card contains an image from `/media/18/thumbnail`
