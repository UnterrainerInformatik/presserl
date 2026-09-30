package info.unterrainer.presserl.section;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import info.unterrainer.presserl.account.RoleHolders;
import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * An account that loses its last section role through member removal or section deletion becomes a
 * sectionless reporter, unless it holds {@code PUBLISHER} or {@code EDITOR_IN_CHIEF}. The role edit
 * applies the same rule itself, from the requested newspaper roles. Runs in the caller's transaction,
 * after the section roles were removed; needs no permission to assign the marker.
 */
@ApplicationScoped
public class LastSectionRule {

    private static final Logger LOG = Logger.getLogger(LastSectionRule.class);

    @Inject
    SectionRoleStore sectionRoles;

    @Inject
    SectionlessReporterStore markers;

    @Inject
    RoleHolders roleHolders;

    /**
     * @param accountIds the accounts that just lost a section role
     * @param actor      the user whose change removed the roles
     * @throws info.unterrainer.presserl.account.AccountException {@code 503} when Keycloak is
     *                                                            unavailable
     */
    public Uni<Void> apply(Collection<String> accountIds, CurrentUser actor) {
        if (accountIds.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return sectionRoles.holdingAnyRole(accountIds).flatMap(holding -> {
            List<String> orphans = accountIds.stream().distinct().filter(id -> !holding.contains(id)).toList();
            if (orphans.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            return roleHolders.approvers().flatMap(holders -> {
                Set<String> editors = holders.getOrDefault(NewspaperRole.EDITOR_IN_CHIEF, Set.of());
                Set<String> publishers = holders.getOrDefault(NewspaperRole.PUBLISHER, Set.of());
                Uni<Void> marking = Uni.createFrom().voidItem();
                for (String orphan : orphans) {
                    if (!editors.contains(orphan) && !publishers.contains(orphan)) {
                        marking = marking.flatMap(done -> markers.set(orphan, actor.sub()).invoke(inserted -> {
                            if (inserted) {
                                LOG.infof("Account %s lost its last section role and became a sectionless reporter "
                                        + "(change by '%s')", orphan, actor.username());
                            }
                        }).replaceWithVoid());
                    }
                }
                return marking;
            });
        });
    }
}
