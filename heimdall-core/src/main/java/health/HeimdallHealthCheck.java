package health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import history.MetricsHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Liveness
@ApplicationScoped
public class HeimdallHealthCheck implements HealthCheck {

    @Inject
    MetricsHistory metricsHistory;

    @Override
    public HealthCheckResponse call() {
        int snapshots = metricsHistory.getAll().size();

        return HealthCheckResponse
                .named("heimdall")
                .status(snapshots > 0)
                .withData("snapshots", snapshots)
                .withData("status", snapshots > 0 ? "collecting" : "initializing")
                .build();
    }
}