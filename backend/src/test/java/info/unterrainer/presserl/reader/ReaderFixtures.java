package info.unterrainer.presserl.reader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

/**
 * Writes articles straight into the database so publication dates can be set exactly. By default every
 * article joins the live issue ({@link #liveIssue()}), so a {@code PUBLISHED} one is visible to readers;
 * {@link #withoutIssue} leaves new articles without issue. Tests using the default call
 * {@code TestSupport.resetIssues} afterwards.
 */
final class ReaderFixtures {

    static final String EMPTY_BODY = "{\"version\": 1, \"blocks\": []}";

    private final DataSource dataSource;
    private final boolean joinLiveIssue;

    ReaderFixtures(DataSource dataSource) {
        this(dataSource, true);
    }

    private ReaderFixtures(DataSource dataSource, boolean joinLiveIssue) {
        this.dataSource = dataSource;
        this.joinLiveIssue = joinLiveIssue;
    }

    /**
     * Fixtures whose new articles belong to no issue; the test puts them into issues itself.
     */
    static ReaderFixtures withoutIssue(DataSource dataSource) {
        return new ReaderFixtures(dataSource, false);
    }

    /**
     * The published issue with the highest number; one numbered after the highest is created and
     * published when none is.
     */
    long liveIssue() {
        String existing = "SELECT id FROM issue WHERE published ORDER BY number DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(existing)) {
                if (result.next()) {
                    return result.getLong(1);
                }
            }
            try (ResultSet result = statement.executeQuery("SELECT coalesce(max(number), 0) + 1 FROM issue")) {
                result.next();
                return issue(result.getInt(1), true, null);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Appends the article to the end of the issue.
     */
    void appendTo(long issueId, long articleId) {
        execute("UPDATE article SET issue_id = " + issueId + ", issue_position = (SELECT coalesce(max(issue_position), "
                + "-1) + 1 FROM article WHERE issue_id = " + issueId + ") WHERE id = " + articleId);
    }

    /**
     * Sets the front-page weight, {@code null} to clear it.
     */
    void weight(long articleId, Integer weight) {
        execute("UPDATE article SET front_page_weight = " + weight + " WHERE id = " + articleId);
    }

    /**
     * Switches the issue live or back.
     */
    void publishIssue(long issueId, boolean published) {
        execute("UPDATE issue SET published = " + published + ", published_at = "
                + (published ? "now()" : "NULL") + " WHERE id = " + issueId);
    }

    void deleteAllArticles() {
        execute("DELETE FROM article");
    }

    /**
     * A published article by {@code Anna} whose single revision went live at {@code publishedAt}.
     */
    long published(String headline, Instant publishedAt) {
        long id = article("PUBLISHED", "anna", "Anna", 1, publishedAt);
        revision(id, 1, "", headline, "", "", EMPTY_BODY, publishedAt);
        return id;
    }

    /**
     * A published article by {@code Anna} in {@code sectionId}.
     */
    long publishedIn(long sectionId, String headline, Instant publishedAt) {
        long id = article(sectionId, "PUBLISHED", "anna", "Anna", 1, publishedAt);
        revision(id, 1, "", headline, "", "", EMPTY_BODY, publishedAt);
        return id;
    }

    /**
     * Deletes every article and every media record (the objects stay in the store).
     */
    void deleteAllArticlesAndMedia() {
        execute("DELETE FROM article");
        execute("DELETE FROM media");
    }

    /**
     * Sets the lead image of revision {@code number}.
     */
    void leadImage(long articleId, int number, long mediaId, String caption) {
        String sql = "UPDATE article_revision SET lead_image_media_id = ?, lead_image_caption = ? "
                + "WHERE article_id = ? AND number = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, mediaId);
            statement.setString(2, caption);
            statement.setLong(3, articleId);
            statement.setInt(4, number);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("no revision " + number + " of article " + articleId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets the status of an article, e.g. {@code OFFLINE}.
     */
    void status(long articleId, String status) {
        execute("UPDATE article SET status = '" + status + "' WHERE id = " + articleId);
    }

    /**
     * Deletes every issue; articles keep existing without issue.
     */
    void deleteAllIssues() {
        execute("UPDATE article SET issue_id = NULL, issue_position = NULL");
        execute("DELETE FROM issue");
    }

    /**
     * An issue, published or not, with an optional publication date.
     */
    long issue(int number, boolean published, LocalDate publicationDate) {
        String sql = "INSERT INTO issue (number, publication_date, published, published_at) VALUES (?, ?, ?, ?) "
                + "RETURNING id";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, number);
            statement.setObject(2, publicationDate);
            statement.setBoolean(3, published);
            statement.setTimestamp(4, published ? Timestamp.from(Instant.now()) : null);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Puts the articles into the issue in this order.
     */
    void inIssue(long issueId, long... articleIds) {
        for (int position = 0; position < articleIds.length; position++) {
            execute("UPDATE article SET issue_id = " + issueId + ", issue_position = " + position + " WHERE id = "
                    + articleIds[position]);
        }
    }

    /**
     * A section at {@code position} with palette colour {@code color}.
     */
    long section(String name, String color, int position) {
        String sql = "INSERT INTO section (name, slug, color, position, created_at) VALUES (?, ?, ?, ?, now()) "
                + "RETURNING id";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, name.toLowerCase());
            statement.setString(3, color);
            statement.setInt(4, position);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * An article in the first section by position ({@link #sectionId()}); {@code liveRevision} and
     * {@code publishedAt} may be {@code null}. A {@code SUBMITTED} article waits for
     * {@code SECTION_EDITOR}, as the database requires.
     */
    long article(String status, String username, String displayName, Integer liveRevision, Instant publishedAt) {
        return article(sectionId(), status, username, displayName, liveRevision, publishedAt);
    }

    private long article(long sectionId, String status, String username, String displayName, Integer liveRevision,
            Instant publishedAt) {
        String sql = "INSERT INTO article (status, author_sub, author_username, author_display_name, live_revision, "
                + "published_at, created_at, updated_at, section_id, pending_level) VALUES (?, ?, ?, ?, ?, ?, now(), now(), ?, ?) "
                + "RETURNING id";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, "sub-" + username);
            statement.setString(3, username);
            statement.setString(4, displayName);
            statement.setObject(5, liveRevision);
            statement.setTimestamp(6, publishedAt == null ? null : Timestamp.from(publishedAt));
            statement.setLong(7, sectionId);
            statement.setString(8, "SUBMITTED".equals(status) ? "SECTION_EDITOR" : null);
            long id;
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                id = result.getLong(1);
            }
            if (joinLiveIssue) {
                appendTo(liveIssue(), id);
            }
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    void revision(long articleId, int number, String kicker, String headline, String subheadline, String lead,
            String body, Instant publishedAt) {
        String sql = "INSERT INTO article_revision (article_id, number, kicker, headline, subheadline, lead, body, "
                + "created_at, updated_at, published_at, author_sub, author_username, author_display_name) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), ?, a.author_sub, a.author_username, a.author_display_name "
                + "FROM article a WHERE a.id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, articleId);
            statement.setInt(2, number);
            statement.setString(3, kicker);
            statement.setString(4, headline);
            statement.setString(5, subheadline);
            statement.setString(6, lead);
            statement.setString(7, body);
            statement.setTimestamp(8, publishedAt == null ? null : Timestamp.from(publishedAt));
            statement.setLong(9, articleId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The first section by position; a section is created when none exists (other tests delete
     * all sections, and every article needs one).
     */
    private long sectionId() {
        String existing = "SELECT id FROM section ORDER BY position, id LIMIT 1";
        String insert = "INSERT INTO section (name, slug, color, position, created_at) "
                + "VALUES ('Reader fixtures', 'reader-fixtures', 'red', 0, now()) RETURNING id";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(existing)) {
                if (result.next()) {
                    return result.getLong(1);
                }
            }
            try (ResultSet result = statement.executeQuery(insert)) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
