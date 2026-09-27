package info.unterrainer.presserl.article;

/**
 * The levels of the approval chain, bottom to top; {@link #compareTo} follows that order.
 * {@code SECTION_EDITOR} means the section editors of the article's section.
 */
public enum ApprovalLevel {
    SECTION_EDITOR,
    EDITOR_IN_CHIEF,
    PUBLISHER
}
