package info.unterrainer.presserl.reader;

import java.net.URI;
import java.util.Optional;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;

import info.unterrainer.presserl.newspaper.SettingValueConverter;
import info.unterrainer.presserl.newspaper.TextSize;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;

/**
 * The reader's text-size switch, a plain form post that works without JavaScript and without login
 * (outside the reader OIDC tenant paths, so also for anonymous visitors of a private newspaper). The
 * choice is kept in a first-party cookie for a year; the visitor is sent back to the page they came from.
 */
@Path("/text-size")
public class TextSizeResource {

    static final String COOKIE = "presserl_text_size";
    static final int ONE_YEAR = 365 * 24 * 60 * 60;

    @Inject
    ReaderConfig config;

    /**
     * {@code 303} to {@code next} when it is a same-origin path, to {@code /} otherwise; the cookie is
     * only set for an allowed size.
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public RestResponse<Void> choose(@RestForm String size, @RestForm String next) {
        ResponseBuilder<Void> response = ResponseBuilder.<Void> seeOther(URI.create(LoginTarget.of(next)))
                .header(HttpHeaders.CACHE_CONTROL, ReaderResource.NO_STORE);
        parse(size).ifPresent(textSize -> response.cookie(new NewCookie.Builder(COOKIE)
                .value(textSize.value())
                .path("/")
                .maxAge(ONE_YEAR)
                .httpOnly(true)
                .secure(config.reader().cookieSecure())
                .sameSite(NewCookie.SameSite.LAX)
                .build()));
        return response.build();
    }

    /**
     * The size named by a form or cookie value; nothing for a missing or unknown value.
     */
    static Optional<TextSize> parse(String value) {
        return value == null ? Optional.empty() : SettingValueConverter.parse(TextSize.class, value.trim());
    }
}
