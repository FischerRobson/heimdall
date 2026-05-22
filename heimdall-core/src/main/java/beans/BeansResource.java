package beans;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import tracing.HeimdallMonitor;

@Path("/beans")
@RequiredArgsConstructor
@HeimdallMonitor
public class BeansResource {

    private final BeansService beansService;

    @GET
    @Path("/graph")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGraph() {
        return Response.ok(beansService.getGraph()).build();
    }

}
