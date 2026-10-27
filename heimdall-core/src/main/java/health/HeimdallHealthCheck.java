package health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import history.MetricsHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tracing.HeimdallMonitor;

// Readiness, not Liveness: there are no snapshots during the first collection
// interval, and a failing liveness probe makes Kubernetes restart the pod
@Readiness
@ApplicationScoped
@HeimdallMonitor
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