package tracing;

import exceptions.ExceptionMappers;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;

@Path("/heimdall/api/traces")
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
    public List<HeimdallTrace> getByMethod(@PathParam("method") String method) {
        return traceRegistry.getByMethod(method);
    }

    // scoped to this resource only — see MetricsResource for rationale
    @ServerExceptionMapper
    public Response mapIllegalArgument(IllegalArgumentException e) {
        return ExceptionMappers.badRequest(e);
    }

    @ServerExceptionMapper
    public Response mapNotFound(NotFoundException e) {
        return ExceptionMappers.notFound();
    }

    @ServerExceptionMapper
    public Response mapNullPointer(NullPointerException e) {
        return ExceptionMappers.nullPointer();
    }

    @ServerExceptionMapper
    public Response mapException(Exception e) {
        return ExceptionMappers.internalError(e);
    }
}