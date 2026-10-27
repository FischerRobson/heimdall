package com.fischer.heimdall.collectors;

import com.fischer.heimdall.history.MetricsHistory;
import com.fischer.heimdall.history.MetricsSnapshot;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import com.fischer.heimdall.metrics.Metrics;
import com.fischer.heimdall.metrics.MetricsBroadcaster;
import com.fischer.heimdall.metrics.MetricsService;
import com.fischer.heimdall.tracing.HeimdallMonitor;

@ApplicationScoped
@RequiredArgsConstructor
@HeimdallMonitor
public class MetricsCollector {

    private final MetricsService metricsService;
    private final MetricsHistory metricsHistory;
    private final MetricsBroadcaster metricsBroadcaster;

    @Scheduled(every = "${heimdall.scheduler.interval:3s}")
    void collect() {
        Metrics metrics = metricsService.getMetrics();
        MetricsSnapshot metricsSnapshot = new MetricsSnapshot(metrics);
        metricsHistory.add(metricsSnapshot);
        metricsBroadcaster.broadcast(metricsSnapshot);
    }

}
