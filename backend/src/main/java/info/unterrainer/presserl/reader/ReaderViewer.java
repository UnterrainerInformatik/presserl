package info.unterrainer.presserl.reader;

import org.eclipse.microprofile.jwt.JsonWebToken;

import info.unterrainer.presserl.auth.CurrentUser;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * The visitor of a reader page as seen through the reader session.
 *
 * @param access      whether the visitor may read a private newspaper
 * @param displayName display name (username when empty); {@code null} for an anonymous visitor
 */
record ReaderViewer(Access access, String displayName) {

    enum Access {
        ANONYMOUS,
        /** Holds {@code READER}, {@code EDITOR_IN_CHIEF} or {@code PUBLISHER}. */
        ENTITLED,
        NOT_ENTITLED
    }

    static final ReaderViewer ANONYMOUS = new ReaderViewer(Access.ANONYMOUS, null);

    static ReaderViewer of(SecurityIdentity identity) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken token)) {
            return ANONYMOUS;
        }
        CurrentUser user = CurrentUser.of(token);
        return new ReaderViewer(user.roles().isEmpty() ? Access.NOT_ENTITLED : Access.ENTITLED, user.displayName());
    }

    boolean loggedIn() {
        return access != Access.ANONYMOUS;
    }
}
