package info.unterrainer.presserl.section;

import info.unterrainer.presserl.auth.CurrentUser;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Loads the {@link Newsroom} of a request: one indexed query for the user's section roles.
 */
@ApplicationScoped
public class NewsroomService {

    @Inject
    SectionRoleStore sectionRoles;

    public Uni<Newsroom> of(CurrentUser user) {
        return sectionRoles.rolesOf(user.sub()).map(roles -> new Newsroom(user, roles));
    }
}
