package sample;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import com.fischer.heimdall.tracing.HeimdallMonitor;

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

    /** Unhandled exception: Quarkus turns it into a 500 outside JAX-RS. */
    @GET
    @Path("/boom")
    public void boom() {
        sampleService.fail();
    }

    /** Echoes a JSON body back — exercises request/response body capture and redaction. */
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> login(Map<String, Object> body) {
        sampleService.tagged(String.valueOf(body.get("user")));
        return Map.of("user", String.valueOf(body.get("user")), "token", "abc.def.ghi");
    }
}
