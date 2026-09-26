package info.unterrainer.presserl.auth;

import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * The requesting user as seen in the bearer token.
 *
 * @param sub         stable identity ({@code sub} claim)
 * @param username    {@code preferred_username}, falling back to {@code sub}
 * @param displayName {@code name}, falling back to the username
 * @param roles       newspaper roles from the {@code groups} claim
 */
public record CurrentUser(String sub, String username, String displayName, List<NewspaperRole> roles) {

    public static CurrentUser of(JsonWebToken token) {
        String username = token.getClaim("preferred_username");
        if (username == null) {
            username = token.getSubject();
        }
        String name = token.getClaim("name");
        return new CurrentUser(token.getSubject(), username, name == null || name.isBlank() ? username : name,
                NewspaperRole.fromGroups(NewspaperRoleAugmentor.groups(token)));
    }

    public boolean has(NewspaperRole role) {
        return roles.contains(role);
    }
}
