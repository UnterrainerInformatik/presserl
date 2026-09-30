## ADDED Requirements

### Requirement: Media picker in the editor
The editor SHALL choose images through a media picker: a dialog over the editor that lists the
newspaper's media (`GET /api/media`), newest first, as tiles with the `thumbnail` rendition (a
placeholder while it is not produced yet), the uploader's display name, the upload date, the usage
count ("not used" for `0`) and up to three tags. The picker SHALL offer the search of the
**Images** view — tags, text, "Unused" and "Mine", combined — starting without filters every time
it opens, and SHALL load further pages when the user scrolls to the end. Selecting a tile SHALL
close the picker and hand the chosen media to the field that opened it; closing the picker
(close button, Escape or outside) SHALL choose nothing and change nothing. The picker SHALL offer
no upload and no camera. When the newspaper has no media, the picker SHALL say so and that images
are added in the **Images** view; when active filters match nothing, it SHALL say so and offer to
clear the filters. A list that fails to load SHALL show the error with a retry. The picker SHALL be
available at every editor level and only while the article is editable.

#### Scenario: Pick a colleague's photo
- **WHEN** a reporter opens the picker from the lead-image field and another reporter uploaded `Sportfest` photos earlier
- **THEN** the picker shows those photos among the newspaper's images with thumbnail, uploader, date and usage count

#### Scenario: Search in the picker
- **WHEN** the reporter types `Sportfest` as tag and switches on "Unused" in the picker
- **THEN** only unused images tagged `Sportfest` are shown, and scrolling to the end loads further matching pages

#### Scenario: Filters start empty
- **WHEN** the reporter closes the picker with an active filter and opens it again
- **THEN** the picker shows all images without filters

#### Scenario: Close without choosing
- **WHEN** the reporter opens the picker and presses Escape
- **THEN** the picker closes, the article is unchanged and nothing is saved

#### Scenario: Empty media library
- **WHEN** the newspaper has no images and the reporter opens the picker
- **THEN** the picker says there are no images yet and that they are added in the "Images" view, and offers no upload

### Requirement: Lead image from the media library
The editor SHALL offer a lead-image field at every editor level: a button to choose an image, which
opens the media picker and sets the picked media as the article's lead image; a preview of the
chosen image (its `thumbnail` rendition); a single-line caption field (at most 300 characters, no
line breaks); and buttons to replace and to remove the image. Replacing SHALL open the media
picker and keep the caption; closing the picker without a choice SHALL keep the current lead
image. The editor SHALL NOT upload files from the device. Setting, replacing and removing the lead
image SHALL be covered by undo and redo. The lead image and caption SHALL be saved by the autosave
like the other fields.

#### Scenario: Add a lead image
- **WHEN** the author chooses an image in the lead-image field, picks the photo `Sportfest 1` in the media picker and types the caption `Our cat Minka`
- **THEN** its preview is shown, no upload is sent, and the next autosave sends `leadImage` with that photo's media id and the caption

#### Scenario: Replace keeps the caption
- **WHEN** the author replaces the lead image with caption `Our class` by another image from the picker
- **THEN** the field shows the new preview, keeps the caption `Our class`, and the next autosave sends the new media id

#### Scenario: Picker closed without a choice
- **WHEN** the author opens the picker from "Replace" and closes it without selecting an image
- **THEN** the previous lead image stays and nothing is saved

#### Scenario: Remove the lead image
- **WHEN** the author removes the lead image
- **THEN** the preview and caption field disappear and the next autosave sends `leadImage` `null`

#### Scenario: Reopen keeps the lead image
- **WHEN** the author reopens an article with a lead image and caption
- **THEN** the editor shows its preview and caption, and saving without changes sends the same `leadImage`

### Requirement: Image blocks from the media library
The editor's "Add block" menu SHALL offer "Image" next to the other block types, at every place a
block can be added and at every editor level. Choosing it SHALL open the media picker; on a pick,
an image block with the picked media and an empty caption SHALL be inserted where the menu was
opened. Closing the picker without a choice SHALL insert nothing. The editor SHALL NOT upload files
from the device.

An image block SHALL show a preview of its image (`thumbnail` rendition), a single-line caption
field (at most 300 characters, no line breaks) and a button to replace the image, which opens the
media picker and keeps the caption; closing the picker without a choice SHALL keep the current
image. Image blocks SHALL be movable and removable like the other blocks, and adding, replacing,
moving, removing and caption changes SHALL be covered by undo and redo and saved by the autosave as
`{"type": "image", "mediaId": <id>, "caption": "..."}` (without `caption` when it is empty). When
the server answers a save with an error naming a field of an image block, the editor SHALL show
the message at that block. The read-only article view SHALL show image blocks as preview and caption.

#### Scenario: Insert an image after a paragraph
- **WHEN** the author opens "Add block" below the first paragraph, chooses "Image", picks the photo `Zieleinlauf` in the media picker and types the caption `The finish line`
- **THEN** an image block with its preview appears below the first paragraph without any upload, and the next autosave sends a body with `{"type": "image", "mediaId": <id of Zieleinlauf>, "caption": "The finish line"}` at that position

#### Scenario: Close the picker
- **WHEN** the author chooses "Image" and closes the media picker without selecting an image
- **THEN** no block is added and nothing is saved

#### Scenario: Same image twice
- **WHEN** the author picks the lead image's media again for an image block
- **THEN** the block is inserted with that media id and both uses are saved

#### Scenario: Replace the image of a block
- **WHEN** the author replaces the image of an image block with caption `Our class` by another image from the picker
- **THEN** the block shows the new preview, keeps the caption `Our class`, and the next autosave sends the new media id

#### Scenario: Undo an inserted image
- **WHEN** the author inserts an image block and chooses undo
- **THEN** the image block is gone and the body is saved without it

#### Scenario: Move an image up
- **WHEN** the author moves an image block above the paragraph before it
- **THEN** the image block stands before that paragraph and the next autosave sends that order

#### Scenario: Read-only article with an image block
- **WHEN** a reporter opens their submitted article whose body contains an image block with caption `Our class`
- **THEN** the read-only view shows the image's preview and the caption at its place in the body

## MODIFIED Requirements

### Requirement: Field explanations for children
The editor SHALL show a question-mark button next to the label of each of these parts: section,
kicker, headline, subheadline, lead image, caption, lead, and the block types paragraph, subhead,
quote, bullet list and image, wherever the editable editor shows that part's label, at every editor
level. The read-only article view, which shows the article without field labels, SHALL stay
unchanged. Pointing at the button, focusing it with the keyboard, or tapping or clicking it
SHALL open an explanation of that part; tapping or clicking the button again, pressing Escape, or
tapping or clicking outside SHALL close it, and focus SHALL stay on or return to the button. At
most one explanation SHALL be open at a time. The button SHALL carry an accessible label naming
the part (for example "What is the kicker?"), and opening it SHALL NOT change the article, move
the cursor out of an unrelated field's text, or trigger a save.

Each explanation SHALL contain, in the user's interface language (German or English):
- one or two short sentences written for a reader of about ten years, saying what the part is for;
- the same sample article for every part, showing section, kicker, headline, subheadline, a lead
  image placeholder with caption, lead and a short body with one paragraph, one subhead, one quote,
  one bullet list and one image placeholder with caption, in the order the reader shows them, with
  the explained part visibly highlighted and the other parts shown unhighlighted.

The lead-image explanation and the image-block explanation SHALL additionally tell the child, in the
same plain language, that every person who can be recognised in the photo must be asked first
whether it may be shown in the newspaper (for children, their parents too); that if someone says no
or cannot be asked, the child chooses another photo or has the faces made unrecognisable
(pixelated), asking an adult for help; and that only photos the child took or may use are allowed.
Editing, saving and choosing images SHALL stay possible without opening any explanation.

#### Scenario: Explain the kicker
- **WHEN** a reporter clicks the question mark next to "Dachzeile" in the German interface
- **THEN** a short explanation of the kicker opens together with the sample article, in which the kicker line is highlighted and the other parts are not

#### Scenario: Same sample for every part
- **WHEN** the reporter opens the explanation of the headline and then that of the lead
- **THEN** both show the same sample article, first with the headline highlighted, then with the lead highlighted

#### Scenario: Image rights on the lead image
- **WHEN** the reporter opens the explanation of the lead image
- **THEN** it explains the lead image, highlights the sample's image placeholder, and says to ask recognisable people (and their parents) first, to choose another photo or pixelate faces with an adult's help otherwise, and to use only own or permitted photos

#### Scenario: Image rights on an image block
- **WHEN** the reporter opens the question mark on an image block
- **THEN** it explains that an image block shows a photo inside the text, highlights the image placeholder in the sample's body, and gives the same image-rights advice as the lead-image explanation

#### Scenario: Block type explained
- **WHEN** the reporter opens the question mark on a quote block
- **THEN** the explanation of the quote opens with the sample's quote highlighted

#### Scenario: Keyboard and Escape
- **WHEN** the reporter moves keyboard focus onto the question mark of the subheadline and then presses Escape
- **THEN** the explanation opens on focus, closes on Escape, and focus stays on the question mark

#### Scenario: English interface
- **WHEN** the interface language is English and the reporter opens the explanation of the caption
- **THEN** the explanation and the sample article are in English

#### Scenario: Read-only article
- **WHEN** a reporter opens their submitted article, which is shown read-only
- **THEN** the article is shown as before, without question-mark buttons

#### Scenario: Opening help does not save
- **WHEN** the reporter opens and closes several explanations without typing
- **THEN** no save request is sent and the save state is unchanged

#### Scenario: Screen-reader label
- **WHEN** a screen reader reads the question mark next to "Vorspann"
- **THEN** it announces a label naming the lead, such as "Was ist der Vorspann?"

## REMOVED Requirements

### Requirement: Lead image in the editor
**Reason**: Replaced by "Lead image from the media library": the lead image is chosen from the
newspaper's media through the media picker instead of being uploaded from the device, so the
upload scenarios "File too large" and "Unsupported file" no longer apply to the editor.
**Migration**: None for data; uploads happen in the "Images" view ("Upload images", "Take photo"),
whose size and type messages stay unchanged.

### Requirement: Image blocks in the editor
**Reason**: Replaced by "Image blocks from the media library": "Add block → Image" and "Replace"
open the media picker instead of the device's file picker, so the scenarios "Cancel the file
picker" and "Upload refused" no longer apply.
**Migration**: None for data; saved image blocks keep their `mediaId` and caption.
