package info.unterrainer.presserl.article;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import info.unterrainer.presserl.account.RoleHolders;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRoleStore;
import info.unterrainer.presserl.trust.TrustStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Loads the {@link Staffing} of a request: one database query for the contributors of the articles,
 * one for the section editors, one Keycloak round trip for the editors-in-chief and publishers and
 * one database query for the trust entries of the contributors.
 */
@ApplicationScoped
public class StaffingService {

    @Inject
    ArticleService articles;

    @Inject
    SectionRoleStore sectionRoles;

    @Inject
    RoleHolders roleHolders;

    @Inject
    TrustStore trustStore;

    /**
     * The staffing needed to decide the actions of the requesting user on {@code listed} and to find
     * their next approval level: every chain question of an author, contributor or corrector depends
     * on it, so it is loaded whenever the list is not empty; otherwise {@link Staffing#NOT_NEEDED}.
     * The answers do not depend on who asks, only the questions do.
     *
     * @throws info.unterrainer.presserl.account.AccountException {@code 503} when Keycloak is
     *                                                            unavailable
     */
    public Uni<Staffing> forArticles(Collection<ArticleEntity> listed) {
        if (listed.isEmpty()) {
            return Uni.createFrom().item(Staffing.NOT_NEEDED);
        }
        return articles.contributors(listed).flatMap(this::load);
    }

    private Uni<Staffing> load(Map<Long, Set<String>> contributors) {
        Set<String> subs = contributors.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        return sectionRoles.sectionEditors().flatMap(sectionEditors -> trustStore.scopesOf(subs)
                .flatMap(trusts -> roleHolders.approvers()
                        .map(holders -> new Staffing(sectionEditors,
                                holders.getOrDefault(NewspaperRole.EDITOR_IN_CHIEF, Set.of()),
                                holders.getOrDefault(NewspaperRole.PUBLISHER, Set.of()),
                                trusts, contributors))));
    }
}
