package info.unterrainer.presserl.reader;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import info.unterrainer.presserl.newspaper.TextSize;
import io.quarkus.qute.TemplateData;

/**
 * What every reader page shows around its content: masthead, text-size switch, section bar, footer and
 * the stylesheets.
 *
 * @param lang       language of the page ({@code de} or {@code en})
 * @param viewerName display name of the logged-in visitor, {@code null} when anonymous
 * @param textSize   effective text size ({@code s}, {@code m}, {@code l}, {@code xl})
 * @param customCss  whether the fork's {@code /theme/custom.css} is linked
 * @param sections   the section bar, empty when the page shows no content
 * @param path       path and query of the page, where the text-size switch returns to
 * @param issueLine  the issue named in the masthead, {@code null} for none
 * @param activeSectionId the section the front page is filtered by, {@code null} for none; its tags
 *                   link back to {@code /}
 * @param legalNotice whether the theme holds a legal notice, linked from the footer
 * @param icons      the favicons and Apple touch icon linked in the head
 */
@TemplateData
public record ReaderPage(String lang, String name, String subtitle, String viewerName, String textSize,
        boolean customCss, List<ReaderSection> sections, String path, IssueLine issueLine, Long activeSectionId,
        boolean legalNotice, Icons icons) {

    static final List<String> TEXT_SIZES = Arrays.stream(TextSize.values()).map(TextSize::value).toList();

    public ReaderPage {
        sections = List.copyOf(sections);
    }

    ReaderPage withSections(List<ReaderSection> sections) {
        return new ReaderPage(lang, name, subtitle, viewerName, textSize, customCss, sections, path, issueLine,
                activeSectionId, legalNotice, icons);
    }

    ReaderPage withIssueLine(IssueLine issueLine) {
        return new ReaderPage(lang, name, subtitle, viewerName, textSize, customCss, sections, path, issueLine,
                activeSectionId, legalNotice, icons);
    }

    ReaderPage withActiveSection(Long activeSectionId) {
        return new ReaderPage(lang, name, subtitle, viewerName, textSize, customCss, sections, path, issueLine,
                activeSectionId, legalNotice, icons);
    }

    /**
     * The choices of the text-size switch in order, the effective size marked as current.
     */
    public List<TextSizeChoice> textSizes() {
        return TEXT_SIZES.stream()
                .map(size -> new TextSizeChoice(size, size.toUpperCase(Locale.ROOT), size.equals(textSize)))
                .toList();
    }

    /**
     * The issue line of the masthead: label and publication date of {@code issue}.
     *
     * @param linked      whether the label links to the issue page
     * @param archiveLink whether the line links to {@code /issues} (more than one issue is live)
     */
    @TemplateData
    public record IssueLine(ReaderIssue issue, boolean linked, boolean archiveLink) {
    }

    /**
     * Icon URLs of the head: the fork theme's file where it has one, else the bundled Presserl icon.
     *
     * @param svg   SVG favicon
     * @param ico   ICO favicon (16, 32 and 48 px) for browsers without SVG favicons
     * @param touch 180×180 Apple touch icon
     */
    @TemplateData
    public record Icons(String svg, String ico, String touch) {
    }

    /**
     * One button of the text-size switch.
     */
    @TemplateData
    public record TextSizeChoice(String value, String label, boolean current) {
    }
}
