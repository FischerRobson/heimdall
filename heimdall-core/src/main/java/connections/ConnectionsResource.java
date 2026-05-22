package connections;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import tracing.HeimdallMonitor;

@Path("/connections")
@RequiredArgsConstructor
@HeimdallMonitor
public class ConnectionsResource {

    private final ConnectionsService connectionsService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConnections() {
        return Response.ok(connectionsService.detectConnections()).build();
    }

}
