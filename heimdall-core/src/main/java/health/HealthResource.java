package health;

import history.MetricsHistory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class HealthResource {

    private MetricsHistory metricsHistory;

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "snapshots", metricsHistory.getAll().size(),
                "version", "1.0.0"
        );
    }

}
