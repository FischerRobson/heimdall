package collectors;

import history.MetricsHistory;
import history.MetricsSnapshot;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import metrics.Metrics;
import metrics.MetricsService;
import tracing.HeimdallMonitor;

@ApplicationScoped
@RequiredArgsConstructor
@HeimdallMonitor
public class MetricsCollector {

    private final MetricsService metricsService;
    private final MetricsHistory metricsHistory;

    @Scheduled(every = "${heimdall.scheduler.interval:3s}")
    void collect() {
        Metrics metrics = metricsService.getMetrics();
        MetricsSnapshot metricsSnapshot = new MetricsSnapshot(metrics);
        metricsHistory.add(metricsSnapshot);
    }

}
