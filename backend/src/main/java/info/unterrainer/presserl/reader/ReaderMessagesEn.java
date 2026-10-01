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
    @Message("Log in")
    String login();

    @Override
    @Message("This account has no access to this newspaper.")
    String noAccessNote();

    @Override
    @Message("Logged in as {name}")
    String loggedInAs(String name);

    @Override
    @Message("Log out")
    String logout();

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

    @Override
    @Message("Text size")
    String textSizeLabel();

    @Override
    @Message("{#when size}{#is 's'}small{#is 'm'}medium{#is 'l'}large{#is 'xl'}extra large{/when}")
    String textSizeName(String size);

    @Override
    @Message("Sections")
    String sectionsLabel();

    @Override
    @Message("Issue {number}")
    String issueLabel(int number);

    @Override
    @Message("Issues")
    String issuesTitle();

    @Override
    @Message("All issues")
    String allIssues();

    @Override
    @Message("No issues published yet.")
    String noIssuesNote();

    @Override
    @Message("This issue has no articles yet.")
    String issueEmptyNote();

    @Override
    @Message("Print")
    String print();

    @Override
    @Message("Back to the article")
    String backToArticle();

    @Override
    @Message("Back to the issue")
    String backToIssue();

    @Override
    @Message("There are no articles in “{name}” yet.")
    String sectionEmptyNote(String name);

    @Override
    @Message("Legal notice")
    String legalNotice();

    @Override
    @Message("Former newsroom member")
    String bylineFormer();

    @Override
    @Message("Delete an account")
    String accountDeletion();

    @Override
    @Message("Accounts of this newspaper are created by its newsroom and deleted by the newspaper's publishers.")
    String accountDeletionWho();

    @Override
    @Message("To have your own account deleted, request it in the presserl app under “My account”, or ask the "
            + "newspaper's publishers or operator directly.")
    String accountDeletionHow();

    @Override
    @Message("Their contact details are in the")
    String accountDeletionContact();

    @Override
    @Message("The account's articles and images stay and are then credited to “former newsroom member”. Deleting "
            + "cannot be undone.")
    String accountDeletionContent();

    @Override
    @Message("The data the app stores on a phone is removed by logging out or uninstalling the app.")
    String accountDeletionDevice();
}
