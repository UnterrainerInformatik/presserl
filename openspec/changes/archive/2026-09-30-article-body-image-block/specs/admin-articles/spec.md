## MODIFIED Requirements

### Requirement: Editor fields at level standard
The editor SHALL offer single-line fields kicker, headline and subheadline (at most 200
characters each), a single-line lead (at most 1000 characters) and a body edited as a sequence of
blocks of the types paragraph, subhead, quote, bullet list and image. Blocks SHALL be addable after
any block, movable up and down and removable. Paragraph, quote and list items SHALL support the mark
bold and no other formatting; subheads are plain text. List blocks SHALL have at least one item.
The editor SHALL NOT let the user enter line breaks into single-line fields or exceed the
length limits.

#### Scenario: Write a structured body
- **WHEN** the author writes a paragraph with one bold word, adds a subhead, a quote and a list with two items
- **THEN** the saved body is format v1 with a paragraph containing a bold run, a subhead, a quote and a two-item list, and it is accepted by the server

#### Scenario: Reopen keeps content
- **WHEN** the author reopens an article whose body contains every block type and bold runs
- **THEN** the editor shows the same blocks, bold text and images with their captions, and saving without changes sends an identical body

#### Scenario: Line break in the headline
- **WHEN** the author presses Enter in the headline field
- **THEN** no line break is inserted

### Requirement: Revision history
The editor SHALL offer the article's revision history (`GET /api/articles/{id}/revisions`)
showing number, last change, publication time and which revision is live, and SHALL show a
selected revision read-only (`GET /api/articles/{id}/revisions/{n}`), including its lead image
preview and caption when it has one and the preview and caption of every image block of its body.

#### Scenario: View an earlier revision
- **WHEN** the author of an article with revisions `2` (working) and `1` (live) opens revision `1`
- **THEN** its content is shown read-only and marked as live

#### Scenario: Earlier revision with a different lead image
- **WHEN** revision `1` has lead image 17 and revision `2` has lead image 18, and the author opens revision `1`
- **THEN** the read-only view shows the preview of media 17 and revision `1`'s caption

#### Scenario: Earlier revision with a body image
- **WHEN** revision `1` has an image block with media 19 and caption `Our class` after its first paragraph, and the author opens revision `1`
- **THEN** the read-only view shows the preview of media 19 with the caption `Our class` after the first paragraph

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
Editing, saving and uploading SHALL stay possible without opening any explanation.

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

## ADDED Requirements

### Requirement: Image blocks in the editor
The editor's "Add block" menu SHALL offer "Image" next to the other block types, at every place a
block can be added and at every editor level. Choosing it SHALL open the device's file picker; the
chosen file SHALL be uploaded (`POST /api/media`) and, on success, an image block with the new
media and an empty caption SHALL be inserted where the menu was opened. Cancelling the picker SHALL
insert nothing. While the upload runs, the place of the new block SHALL show that it is busy; an
upload that fails SHALL insert nothing and SHALL show the plain messages of the lead-image field
(too large naming the maximum size, not a supported image type, image damaged or too big in pixels,
server not reachable).

An image block SHALL show a preview of its image (`thumbnail` rendition), a single-line caption
field (at most 300 characters, no line breaks) and a button to replace the image, which uploads a
new file and keeps the caption; a failed replacement SHALL keep the current image and show the same
messages. Image blocks SHALL be movable and removable like the other blocks, and adding, replacing,
moving, removing and caption changes SHALL be covered by undo and redo and saved by the autosave as
`{"type": "image", "mediaId": <id>, "caption": "..."}` (without `caption` when it is empty). When
the server answers a save with an error naming a field of an image block, the editor SHALL show
the message at that block. The read-only article view SHALL show image blocks as preview and caption.

#### Scenario: Insert an image after a paragraph
- **WHEN** the author opens "Add block" below the first paragraph, chooses "Image", picks a JPEG and types the caption `The finish line`
- **THEN** the image is uploaded, an image block with its preview appears below the first paragraph, and the next autosave sends a body with `{"type": "image", "mediaId": <new id>, "caption": "The finish line"}` at that position

#### Scenario: Cancel the file picker
- **WHEN** the author chooses "Image" and cancels the file picker
- **THEN** no block is added and nothing is saved

#### Scenario: Upload refused
- **WHEN** the author chooses "Image" and picks a HEIC file the server refuses with `415`
- **THEN** the editor says this kind of image is not supported and no block is added

#### Scenario: Replace the image of a block
- **WHEN** the author replaces the image of an image block with caption `Our class` by another photo
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
