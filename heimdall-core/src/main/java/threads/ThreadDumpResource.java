package threads;

import exceptions.ExceptionMappers;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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
    private final ThreadDumpHistory threadDumpHistory;

    /** Captures a new dump (and stores it in the history). */
    @GET
    @Path("/dump")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dump() {
        return Response.ok(threadDumpService.captureAndStore()).build();
    }

    /** Previously captured dumps, newest first, without stack traces. */
    @GET
    @Path("/dumps")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ThreadDumpSummary> history() {
        return threadDumpHistory.summaries();
    }

    @GET
    @Path("/dumps/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ThreadDump byId(@PathParam("id") String id) {
        return threadDumpHistory.get(id).orElseThrow(NotFoundException::new);
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
