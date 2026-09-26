package info.unterrainer.presserl.reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.http.Cookie;

import io.restassured.RestAssured;

/**
 * A browser without JavaScript that drives the reader's code flow against the Dev Services Keycloak.
 * Every instance has its own cookies, so it is a fresh visitor.
 */
final class ReaderBrowser implements AutoCloseable {

    static final String SESSION_COOKIE = "q_session_reader";

    private final WebClient client = new WebClient();

    ReaderBrowser() {
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setCssEnabled(false);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.addRequestHeader("Accept-Language", "de");
    }

    static String url(String path) {
        return RestAssured.baseURI + ":" + RestAssured.port + path;
    }

    /**
     * Opens {@code path}, which must lead to the Keycloak login form, logs in and returns the page the
     * browser ends up on.
     */
    WebResponse login(String path, String username, String password) {
        Page page = open(path);
        if (!(page instanceof HtmlPage loginPage) || loginPage.getHtmlElementById("kc-form-login") == null) {
            throw new IllegalStateException("Expected the Keycloak login form, got " + page.getUrl());
        }
        HtmlForm form = loginPage.getHtmlElementById("kc-form-login");
        form.getInputByName("username").setValue(username);
        form.getInputByName("password").setValue(password);
        try {
            return loginPage.getHtmlElementById("kc-login").click().getWebResponse();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    WebResponse get(String path) {
        return open(path).getWebResponse();
    }

    /**
     * {@code path} with an {@code Authorization: Bearer} header on top of the browser's cookies.
     */
    WebResponse getWithBearer(String path, String token) {
        try {
            WebRequest request = new WebRequest(URI.create(url(path)).toURL());
            request.setAdditionalHeader("Authorization", "Bearer " + token);
            return client.getPage(request).getWebResponse();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The first response for {@code path}, without following redirects.
     */
    WebResponse getWithoutRedirect(String path) {
        client.getOptions().setRedirectEnabled(false);
        try {
            return get(path);
        } finally {
            client.getOptions().setRedirectEnabled(true);
        }
    }

    Cookie sessionCookie() {
        return client.getCookieManager().getCookie(SESSION_COOKIE);
    }

    private Page open(String path) {
        try {
            return client.getPage(url(path));
        } catch (IOException | FailingHttpStatusCodeException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
