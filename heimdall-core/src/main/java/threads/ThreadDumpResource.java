package threads;

import exceptions.ExceptionMappers;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import tracing.HeimdallMonitor;

import java.util.List;

@Path("/heimdall/api/threads")
@RequiredArgsConstructor
@HeimdallMonitor
public class ThreadDumpResource {

    private final ThreadDumpService threadDumpService;

    @GET
    @Path("/dump")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dump() {
        return Response.ok(threadDumpService.dump()).build();
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
