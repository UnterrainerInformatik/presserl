package info.unterrainer.presserl.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReaderTenantScopeTest {

    private static final List<String> READER_PATHS = List.of("/", "/login", "/logout", "/articles/*", "/media/*");

    @ParameterizedTest
    @ValueSource(strings = { "/", "/login", "/logout", "/articles/7", "/articles/a/b", "/media/17/web" })
    void readerPaths(String path) {
        assertThat(ReaderTenantScope.matches(READER_PATHS, path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/me", "/api/newspaper", "/admin/", "/q/health/ready", "/login/x", "/articles",
            "/reader/reader.css", "/media", "/api/media/17/renditions/web" })
    void otherPaths(String path) {
        assertThat(ReaderTenantScope.matches(READER_PATHS, path)).isFalse();
    }
}
