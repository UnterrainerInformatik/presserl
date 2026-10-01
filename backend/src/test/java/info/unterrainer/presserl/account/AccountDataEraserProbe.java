package info.unterrainer.presserl.account;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * Test-only endpoint: runs {@link AccountDataEraser#erase} for a sub, as the recovery of a deletion
 * whose commit failed would.
 */
@Path("/test/erase/{sub}")
public class AccountDataEraserProbe {

    @Inject
    AccountDataEraser eraser;

    @POST
    public Uni<Void> erase(@PathParam("sub") String sub) {
        return eraser.erase(sub);
    }
}
