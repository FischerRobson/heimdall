package metrics;

import exceptions.ExceptionMappers;
import history.MetricsHistory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import tracing.HeimdallMonitor;

@Path("/metrics")
@RequiredArgsConstructor
@HeimdallMonitor
public class MetricsResource {

    private final MetricsService metricsService;
    private final MetricsHistory metricsHistory;

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
