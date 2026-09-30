package info.unterrainer.presserl.auth;

import org.eclipse.microprofile.jwt.JsonWebToken;

import info.unterrainer.presserl.section.NewsroomService;
import info.unterrainer.presserl.section.SectionRoleStore;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/me")
@Authenticated
public class MeResource {

    @Inject
    JsonWebToken token;

    @Inject
    SectionRoleStore sectionRoles;

    @Inject
    NewsroomService newsrooms;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<MeDto> get() {
        CurrentUser user = CurrentUser.of(token);
        // one after the other: both queries use the request's reactive session
        return newsrooms.of(user).flatMap(newsroom -> sectionRoles.namedRolesOf(user.sub())
                .map(roles -> new MeDto(user.username(), user.displayName(), user.roles(), roles,
                        newsroom.sectionlessReporter(), newsroom.allowedActions())));
    }
}
