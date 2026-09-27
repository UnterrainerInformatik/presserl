package info.unterrainer.presserl.article;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import info.unterrainer.presserl.account.RoleHolders;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRoleStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Loads the {@link Staffing} of a request: one database query for the section editors and one
 * Keycloak round trip for the editors-in-chief and publishers.
 */
@ApplicationScoped
public class StaffingService {

    @Inject
    SectionRoleStore sectionRoles;

    @Inject
    RoleHolders roleHolders;

    /**
     * The staffing needed to decide the actions of the requesting user on {@code articles}: loaded
     * only when the user authored one of them and does not hold {@code PUBLISHER} (a publisher's own
     * chain is always empty, and only an author's chain is asked for); otherwise
     * {@link Staffing#NOT_NEEDED}.
     */
    public Uni<Staffing> forArticles(Newsroom newsroom, Collection<ArticleEntity> articles) {
        String sub = newsroom.user().sub();
        if (newsroom.user().has(NewspaperRole.PUBLISHER)
                || articles.stream().noneMatch(article -> Objects.equals(article.authorSub, sub))) {
            return Uni.createFrom().item(Staffing.NOT_NEEDED);
        }
        return load();
    }

    /**
     * The staffing needed to find the next level after an approval; always loaded.
     */
    public Uni<Staffing> forApproval() {
        return load();
    }

    private Uni<Staffing> load() {
        return sectionRoles.sectionEditors().flatMap(sectionEditors -> roleHolders.approvers()
                .map(holders -> new Staffing(sectionEditors,
                        holders.getOrDefault(NewspaperRole.EDITOR_IN_CHIEF, Set.of()),
                        holders.getOrDefault(NewspaperRole.PUBLISHER, Set.of()))));
    }
}
