package info.unterrainer.presserl.section;

import info.unterrainer.presserl.auth.CurrentUser;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Loads the {@link Newsroom} of a request: one indexed query for the user's section roles and one
 * for the sectionless-reporter marker.
 */
@ApplicationScoped
public class NewsroomService {

    @Inject
    SectionRoleStore sectionRoles;

    @Inject
    SectionlessReporterStore sectionlessReporters;

    public Uni<Newsroom> of(CurrentUser user) {
        // one after the other: both queries use the request's reactive session
        return sectionRoles.rolesOf(user.sub()).flatMap(roles -> sectionlessReporters.isMarked(user.sub())
                .map(marked -> new Newsroom(user, roles, marked)));
    }
}
