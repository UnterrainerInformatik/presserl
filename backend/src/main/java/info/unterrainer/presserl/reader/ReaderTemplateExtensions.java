package info.unterrainer.presserl.reader;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateExtension.TemplateAttribute;
import io.quarkus.qute.TemplateInstance;

/**
 * Date formatting for reader templates in the locale set on the template instance.
 */
@TemplateExtension
class ReaderTemplateExtensions {

    private ReaderTemplateExtensions() {
    }

    /**
     * The local date in the long form of the page's language, e.g. {@code 20. September 2026}.
     */
    static String longDate(Instant instant, @TemplateAttribute(TemplateInstance.LOCALE) Object locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale instanceof Locale l ? l : ReaderResource.GERMAN)
                .format(ReaderArticle.localDate(instant));
    }

    /**
     * The local date as ISO 8601, for {@code <time datetime>}.
     */
    static String isoDate(Instant instant) {
        return ReaderArticle.localDate(instant).toString();
    }
}
