package sample;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import tracing.HeimdallMonitor;

/**
 * Stand-in for a host-application endpoint. Its path has no /heimdall/api prefix —
 * the tests use it to check that Heimdall no longer moves the host app's routes.
 */
@Path("/sample")
@HeimdallMonitor
public class SampleResource {

    @Inject
    SampleService sampleService;

    @GET
    @Path("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(@QueryParam("name") String name) {
        return sampleService.greet(name, "secret");
    }
}
