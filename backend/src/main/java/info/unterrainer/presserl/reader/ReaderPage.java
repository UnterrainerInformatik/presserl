package info.unterrainer.presserl.reader;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import info.unterrainer.presserl.newspaper.TextSize;
import io.quarkus.qute.TemplateData;

/**
 * What every reader page shows around its content: masthead, text-size switch, section bar and the
 * stylesheets.
 *
 * @param lang       language of the page ({@code de} or {@code en})
 * @param viewerName display name of the logged-in visitor, {@code null} when anonymous
 * @param textSize   effective text size ({@code s}, {@code m}, {@code l}, {@code xl})
 * @param customCss  whether the fork's {@code /theme/custom.css} is linked
 * @param sections   the section bar, empty when the page shows no content
 * @param path       path and query of the page, where the text-size switch returns to
 */
@TemplateData
public record ReaderPage(String lang, String name, String subtitle, String viewerName, String textSize,
        boolean customCss, List<ReaderSection> sections, String path) {

    static final List<String> TEXT_SIZES = Arrays.stream(TextSize.values()).map(TextSize::value).toList();

    public ReaderPage {
        sections = List.copyOf(sections);
    }

    ReaderPage withSections(List<ReaderSection> sections) {
        return new ReaderPage(lang, name, subtitle, viewerName, textSize, customCss, sections, path);
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
     * One button of the text-size switch.
     */
    @TemplateData
    public record TextSizeChoice(String value, String label, boolean current) {
    }
}
