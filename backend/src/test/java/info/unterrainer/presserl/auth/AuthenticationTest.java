package info.unterrainer.presserl.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.unterrainer.presserl.TestSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;

@QuarkusTest
class AuthenticationTest {

    @BeforeEach
    void ready() {
        TestSupport.awaitReady();
    }

    @Test
    void missingTokenIsRejected() {
        given().get("/api/me").then().statusCode(401);
    }

    @Test
    void tokenFromForeignIssuerIsRejected() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String foreign = Jwt.issuer("https://evil.example.org/realms/presserl")
                .audience("presserl-backend")
                .subject("intruder")
                .claim("preferred_username", "publisher")
                .groups("publisher")
                .jws().keyId("foreign")
                .sign(generator.generateKeyPair().getPrivate());

        given().auth().oauth2(foreign).get("/api/me").then().statusCode(401);
    }

    @Test
    void tokenWithoutBackendAudienceIsRejected() {
        String token = TestSupport.token(TestSupport.NO_AUDIENCE_CLIENT, "publisher", "publisher");

        given().auth().oauth2(token).get("/api/me").then().statusCode(401);
    }

    @Test
    void bootstrappedPublisherAsksWhoTheyAre() {
        given().auth().oauth2(TestSupport.token("publisher", "publisher")).get("/api/me").then()
                .statusCode(200)
                .body("username", equalTo("publisher"))
                .body("displayName", equalTo("publisher"))
                .body("roles", contains("PUBLISHER"))
                .body("allowedActions", contains("WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES",
                        "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER", "CONFIGURE_SPELL_CHECK"));
    }

    @Test
    void editorInChiefMayConfigureTheNewspaper() {
        given().auth().oauth2(TestSupport.token("chief", "chief")).get("/api/me").then()
                .statusCode(200)
                .body("roles", contains("EDITOR_IN_CHIEF"))
                .body("allowedActions", contains("WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES",
                        "MANAGE_ISSUES", "ADMINISTER_ACCOUNTS", "CONFIGURE_NEWSPAPER"));
    }

    @Test
    void userWithoutNewspaperGroupsHasNoRoles() {
        given().auth().oauth2(TestSupport.token("nogroups", "nogroups")).get("/api/me").then()
                .statusCode(200)
                .body("username", equalTo("nogroups"))
                .body("displayName", equalTo("No Groups"))
                .body("roles", empty())
                .body("sectionRoles", empty())
                .body("allowedActions", empty());
    }

    @Test
    void readerHasNoActions() {
        given().auth().oauth2(TestSupport.token("reader", "reader")).get("/api/me").then()
                .statusCode(200)
                .body("roles", contains("READER"))
                .body("allowedActions", empty());
    }

    @Test
    void clientConfigIsPublic() {
        given().get("/api/client-config").then()
                .statusCode(200)
                .body("oidc.issuer", equalTo(TestSupport.issuer()))
                .body("oidc.clientId", equalTo("presserl-admin"))
                .body("oidc.scopes", hasItem("openid"));
    }
}
