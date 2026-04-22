package metrics;

import history.MetricsHistory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

@Path("/metrics")
@RequiredArgsConstructor
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
}
