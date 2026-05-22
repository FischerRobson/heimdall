package health;

import history.MetricsHistory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import tracing.HeimdallMonitor;

import java.util.Map;

@RequiredArgsConstructor
@HeimdallMonitor
@Path("/health")
public class HealthResource {

    private final MetricsHistory metricsHistory;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        var map = Map.of(
                "status", "UP",
                "snapshots", metricsHistory.getAll().size(),
                "version", "1.0.0"
        );
        return Response.ok(map).build();
    }

}
