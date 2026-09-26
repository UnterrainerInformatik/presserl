package info.unterrainer.presserl.auth;

import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Adds the newspaper roles ({@link NewspaperRole} names) derived from the token's {@code groups}
 * claim, so resources can use {@code @RolesAllowed("PUBLISHER")}.
 */
@ApplicationScoped
public class NewspaperRoleAugmentor implements SecurityIdentityAugmentor {

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken token)) {
            return Uni.createFrom().item(identity);
        }
        List<NewspaperRole> roles = NewspaperRole.fromGroups(groups(token));
        if (roles.isEmpty()) {
            return Uni.createFrom().item(identity);
        }
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        roles.forEach(role -> builder.addRole(role.name()));
        return Uni.createFrom().item(builder.build());
    }

    static Set<String> groups(JsonWebToken token) {
        Set<String> groups = token.getGroups();
        return groups == null ? Set.of() : groups;
    }
}
