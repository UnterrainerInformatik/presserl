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

    /**
     * The byline of an article whose author's account was deleted.
     */
    @Message("Ehemaliges Redaktionsmitglied")
    String bylineFormer();

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

    @Message("Schriftgröße")
    String textSizeLabel();

    @Message("{#when size}{#is 's'}klein{#is 'm'}mittel{#is 'l'}groß{#is 'xl'}sehr groß{/when}")
    String textSizeName(String size);

    @Message("Ressorts")
    String sectionsLabel();

    @Message("Ausgabe {number}")
    String issueLabel(int number);

    @Message("Ausgaben")
    String issuesTitle();

    @Message("Alle Ausgaben")
    String allIssues();

    @Message("Noch keine Ausgaben erschienen.")
    String noIssuesNote();

    @Message("Diese Ausgabe enthält noch keine Artikel.")
    String issueEmptyNote();

    @Message("Drucken")
    String print();

    @Message("Zurück zum Artikel")
    String backToArticle();

    @Message("Zurück zur Ausgabe")
    String backToIssue();

    @Message("In „{name}“ gibt es noch keine Artikel.")
    String sectionEmptyNote(String name);

    @Message("Impressum")
    String legalNotice();

    @Message("Konto löschen")
    String accountDeletion();

    @Message("Die Konten dieser Zeitung legt ihre Redaktion an, gelöscht werden sie von den Herausgebern der Zeitung.")
    String accountDeletionWho();

    @Message("Die Löschung des eigenen Kontos lässt sich in der presserl-App unter „Mein Konto“ beantragen. Man kann "
            + "auch die Herausgeber oder den Betreiber der Zeitung direkt darum bitten.")
    String accountDeletionHow();

    /**
     * Followed by a link to the legal notice.
     */
    @Message("Die Kontaktdaten stehen im")
    String accountDeletionContact();

    @Message("Artikel und Bilder des Kontos bleiben erhalten und tragen dann die Angabe „ehemaliges "
            + "Redaktionsmitglied“. Das Löschen lässt sich nicht rückgängig machen.")
    String accountDeletionContent();

    @Message("Was die App auf einem Handy speichert, wird durch Abmelden oder Deinstallieren der App entfernt.")
    String accountDeletionDevice();
}
