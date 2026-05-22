package tracing;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Path("/traces")
@RequiredArgsConstructor
public class TraceResource {

    private final TraceRegistry traceRegistry;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<HeimdallTrace> getTraces() {
        return traceRegistry.getAll();
    }

    @GET
    @Path("/errors")
    @Produces(MediaType.APPLICATION_JSON)
    public List<HeimdallTrace> getErrors() {
        return traceRegistry.getErrors();
    }

    @GET
    @Path("/method/{method}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<HeimdallTrace> getByMethod(@QueryParam("method") String method) {
        return traceRegistry.getByMethod(method);
    }
}