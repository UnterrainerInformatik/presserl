package info.unterrainer.presserl.reader;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

/**
 * Reader chrome texts; German is the default, {@link ReaderMessagesEn} the English localization.
 */
@MessageBundle(locale = "de")
public interface ReaderMessages {

    @Message("Noch keine Artikel veröffentlicht.")
    String emptyNote();

    @Message("Diese Zeitung ist privat.")
    String privateNote();

    @Message("Anmelden")
    String login();

    @Message("Dieses Konto hat keinen Zugang zu dieser Zeitung.")
    String noAccessNote();

    @Message("Angemeldet als {name}")
    String loggedInAs(String name);

    @Message("Abmelden")
    String logout();

    @Message("Von {name}")
    String byline(String name);

    @Message("Veröffentlicht am {date}")
    String published(String date);

    @Message("Aktualisiert am {date}")
    String updated(String date);

    @Message("Seite nicht gefunden")
    String notFoundTitle();

    @Message("Diese Seite gibt es nicht oder nicht mehr.")
    String notFoundText();

    @Message("Zur Titelseite")
    String backToFrontPage();
}
