package info.unterrainer.presserl.reader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

/**
 * Writes articles straight into the database so publication dates can be set exactly.
 */
final class ReaderFixtures {

    static final String EMPTY_BODY = "{\"version\": 1, \"blocks\": []}";

    private final DataSource dataSource;

    ReaderFixtures(DataSource dataSource) {
        this.dataSource = dataSource;
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
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    void revision(long articleId, int number, String kicker, String headline, String subheadline, String lead,
            String body, Instant publishedAt) {
        String sql = "INSERT INTO article_revision (article_id, number, kicker, headline, subheadline, lead, body, "
                + "created_at, updated_at, published_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), ?)";
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
