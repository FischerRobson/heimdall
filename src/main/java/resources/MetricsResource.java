package resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import services.MetricsService;

import java.util.List;
import java.util.Map;

@Path("/metrics")
public class MetricsResource {

    private final MetricsService metricsService;

    public MetricsResource(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetrics() {
        return Response.ok(metricsService.getMetrics()).build();
    }
}
