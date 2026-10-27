package metrics;

import exceptions.ErrorResponse;
import exceptions.ExceptionMappers;
import history.MetricsHistory;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import tracing.HeimdallMonitor;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Path("/heimdall/api/metrics")
@RequiredArgsConstructor
@HeimdallMonitor
public class MetricsResource {

    private final MetricsService metricsService;
    private final MetricsHistory metricsHistory;
    private final HeapDumpService heapDumpService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetrics() {
        return Response.ok(metricsService.getMetrics()).build();
    }

    @GET
    @Path("/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetricsHistory() {
        return Response.ok(metricsHistory.getAll()).build();
    }

    @POST
    @Path("/gc")
    @Produces(MediaType.APPLICATION_JSON)
    public Response triggerGc() {
        return Response.ok(metricsService.triggerGarbageCollection()).build();
    }

    /**
     * Downloads a heap dump (.hprof). GET so a plain link can stream it straight to disk —
     * heap dumps can be gigabytes, too big to buffer in the browser.
     * Pauses the JVM while it is written; meant for dev/staging, not production.
     */
    @GET
    @Path("/heapdump")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response heapDump(@QueryParam("live") @DefaultValue("true") boolean live) throws IOException {
        java.nio.file.Path file;
        try {
            file = heapDumpService.dump(live);
        } catch (IllegalStateException e) {
            return error(Response.Status.CONFLICT, e);
        } catch (UnsupportedOperationException e) {
            return error(Response.Status.NOT_IMPLEMENTED, e);
        }

        // safety net in case the response is never written (client disconnects early)
        file.toFile().deleteOnExit();
        long size = Files.size(file);
        StreamingOutput body = out -> {
            try {
                Files.copy(file, out);
            } finally {
                Files.deleteIfExists(file);
            }
        };

        String filename = "heimdall-heap-" + ProcessHandle.current().pid() + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".hprof";

        return Response.ok(body)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Length", size)
                .build();
    }

    private Response error(Response.Status status, RuntimeException e) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(status, e.getMessage(), e.getClass().getSimpleName()))
                .build();
    }

    // scoped to this resource only — a global @Provider ExceptionMapper would also
    // intercept exceptions thrown by the host application's own endpoints
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
