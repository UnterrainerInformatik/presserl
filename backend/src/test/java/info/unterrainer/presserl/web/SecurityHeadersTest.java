package info.unterrainer.presserl.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityHeadersTest {

    @ParameterizedTest
    @ValueSource(strings = { "/admin/0123456789abcdef0123.wasm", "/admin/skiko/abcdef.wasm" })
    void hashedWasmModulesAreImmutable(String path) {
        assertThat(SecurityHeaders.adminCacheControl(path)).isEqualTo("public, max-age=31536000, immutable");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/admin/", "/admin/composeApp.js", "/admin/styles.css",
            "/admin/composeResources/presserl.admin.generated.resources/values/strings.commonMain.cvr" })
    void stableNamesAreRevalidated(String path) {
        assertThat(SecurityHeaders.adminCacheControl(path)).isEqualTo("no-cache");
    }
}
