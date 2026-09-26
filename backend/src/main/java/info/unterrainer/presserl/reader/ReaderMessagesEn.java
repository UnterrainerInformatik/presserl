package info.unterrainer.presserl.reader;

import io.quarkus.qute.i18n.Localized;
import io.quarkus.qute.i18n.Message;

/**
 * English reader chrome texts.
 */
@Localized("en")
public interface ReaderMessagesEn extends ReaderMessages {

    @Override
    @Message("No articles published yet.")
    String emptyNote();

    @Override
    @Message("This newspaper is private.")
    String privateNote();

    @Override
    @Message("By {name}")
    String byline(String name);

    @Override
    @Message("Published on {date}")
    String published(String date);

    @Override
    @Message("Updated on {date}")
    String updated(String date);

    @Override
    @Message("Page not found")
    String notFoundTitle();

    @Override
    @Message("This page does not exist or is no longer available.")
    String notFoundText();

    @Override
    @Message("To the front page")
    String backToFrontPage();
}
