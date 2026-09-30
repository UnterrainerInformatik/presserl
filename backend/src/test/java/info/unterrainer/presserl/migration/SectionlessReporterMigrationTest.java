package info.unterrainer.presserl.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * V15 (sectionless reporter) on a separate schema: migrated to V15, the marker table exists, is empty
 * and has the account id as primary key.
 */
@QuarkusTest
class SectionlessReporterMigrationTest {

    private static final String SCHEMA = "migration_v15_test";

    @Inject
    DataSource dataSource;

    @BeforeEach
    @AfterEach
    void dropSchema() {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void createsTheEmptyMarkerTable() {
        migrate("15");

        assertThat(strings("SELECT count(*) FROM " + SCHEMA + ".sectionless_reporter")).containsExactly("0");
        assertThat(strings("SELECT column_name || ':' || is_nullable FROM information_schema.columns WHERE table_schema = '"
                + SCHEMA + "' AND table_name = 'sectionless_reporter' ORDER BY ordinal_position"))
                .containsExactly("account_id:NO", "assigned_by:NO", "assigned_at:NO");
        execute("INSERT INTO " + SCHEMA + ".sectionless_reporter VALUES ('a', 'b', now())");
        assertThatThrownBy(() -> execute("INSERT INTO " + SCHEMA + ".sectionless_reporter VALUES ('a', 'c', now())"))
                .hasMessageContaining("sectionless_reporter_pkey");
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> strings(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
