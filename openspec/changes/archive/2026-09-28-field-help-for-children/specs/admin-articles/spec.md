## ADDED Requirements

### Requirement: Field explanations for children
The editor SHALL show a question-mark button next to the label of each of these parts: section,
kicker, headline, subheadline, lead image, caption, lead, and the block types paragraph, subhead,
quote and bullet list, wherever the editable editor shows that part's label, at every editor
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
  image placeholder with caption, lead and a short body with one paragraph, one subhead, one quote
  and one bullet list, in the order the reader shows them, with the explained part visibly
  highlighted and the other parts shown unhighlighted.

The lead-image explanation SHALL additionally tell the child, in the same plain language, that
every person who can be recognised in the photo must be asked first whether it may be shown in
the newspaper (for children, their parents too); that if someone says no or cannot be asked, the
child chooses another photo or has the faces made unrecognisable (pixelated), asking an adult for
help; and that only photos the child took or may use are allowed. Editing, saving and uploading
SHALL stay possible without opening any explanation.

#### Scenario: Explain the kicker
- **WHEN** a reporter clicks the question mark next to "Dachzeile" in the German interface
- **THEN** a short explanation of the kicker opens together with the sample article, in which the kicker line is highlighted and the other parts are not

#### Scenario: Same sample for every part
- **WHEN** the reporter opens the explanation of the headline and then that of the lead
- **THEN** both show the same sample article, first with the headline highlighted, then with the lead highlighted

#### Scenario: Image rights on the lead image
- **WHEN** the reporter opens the explanation of the lead image
- **THEN** it explains the lead image, highlights the sample's image placeholder, and says to ask recognisable people (and their parents) first, to choose another photo or pixelate faces with an adult's help otherwise, and to use only own or permitted photos

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
