package info.unterrainer.presserl.newspaper;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/newspaper")
public class NewspaperResource {

    @Inject
    NewspaperSettings settings;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<NewspaperDto> get() {
        return settings.effective().map(NewspaperDto::of);
    }
}
