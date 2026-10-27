package connections.detect;

import connections.ConnectionInfo;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import tracing.HeimdallMonitor;

import java.time.Instant;
import java.util.*;

/**
 * Detects Agroal datasources — the connection pool Quarkus actually uses for JDBC
 * (HikariCP is never present in a regular Quarkus app).
 *
 * Reads the pool gauges Quarkus publishes to Micrometer ("agroal.*", tagged with the
 * datasource name), so there is no compile-time dependency on Agroal itself. Requires
 * datasource metrics to be enabled (quarkus.datasource.metrics.enabled=true or
 * quarkus.datasource.jdbc.enable-metrics=true).
 */
@ApplicationScoped
@HeimdallMonitor
public class AgroalConnection {

    private static final String METRIC_PREFIX = "agroal.";
    private static final String DEFAULT_DATASOURCE = "<default>";
    private static final int DEFAULT_MAX_SIZE = 20; // Quarkus default for jdbc.max-size

    @Inject
    Instance<MeterRegistry> meterRegistry;

    public void detect(List<ConnectionInfo> connectionInfoList) {
        if (!meterRegistry.isResolvable()) return;

        // datasource name -> metric name -> value
        Map<String, Map<String, Double>> byDatasource = new TreeMap<>();

        for (Meter meter : meterRegistry.get().getMeters()) {
            String metricName = meter.getId().getName();
            if (!metricName.startsWith(METRIC_PREFIX)) continue;

            String datasource = Optional.ofNullable(meter.getId().getTag("datasource"))
                    .orElse(DEFAULT_DATASOURCE);

            Iterator<Measurement> measurements = meter.measure().iterator();
            if (!measurements.hasNext()) continue;
            double value = measurements.next().getValue();
            if (Double.isNaN(value)) continue;

            byDatasource.computeIfAbsent(datasource, k -> new HashMap<>()).put(metricName, value);
        }

        for (Map.Entry<String, Map<String, Double>> entry : byDatasource.entrySet()) {
            connectionInfoList.add(toConnectionInfo(entry.getKey(), entry.getValue()));
        }
    }

    private ConnectionInfo toConnectionInfo(String datasource, Map<String, Double> metrics) {
        int active  = metric(metrics, "agroal.active.count");
        int idle    = metric(metrics, "agroal.available.count");
        int pending = metric(metrics, "agroal.awaiting.count");
        int maxUsed = metric(metrics, "agroal.max.used.count");
        int leaks   = metric(metrics, "agroal.leak.detection.count");
        int max     = maxPoolSize(datasource);

        double usage = max > 0
                ? Math.round((double) active / max * 1000.0) / 10.0
                : 0.0;

        // same keys as HikariCPConnection, so the UI renders both the same way
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeConnections", active);
        stats.put("idleConnections",   idle);
        stats.put("totalConnections",  active + idle);
        stats.put("pendingThreads",    pending);
        stats.put("maxConnections",    max);
        stats.put("maxUsed",           maxUsed);
        stats.put("leaksDetected",     leaks);
        stats.put("usagePercent",      usage);

        return ConnectionInfo.builder()
                .name(datasource)
                .type("database")
                .technology("Agroal")
                .status(pending > 0 || leaks > 0 ? "DEGRADED" : "UP")
                .stats(stats)
                .checkedAt(Instant.now())
                .build();
    }

    private int metric(Map<String, Double> metrics, String name) {
        return (int) Math.round(metrics.getOrDefault(name, 0.0));
    }

    // max pool size isn't exported as a metric — read it from config instead
    private int maxPoolSize(String datasource) {
        String key = DEFAULT_DATASOURCE.equals(datasource)
                ? "quarkus.datasource.jdbc.max-size"
                : "quarkus.datasource.\"" + datasource + "\".jdbc.max-size";
        try {
            return ConfigProvider.getConfig()
                    .getOptionalValue(key, Integer.class)
                    .orElse(DEFAULT_MAX_SIZE);
        } catch (Exception e) {
            return DEFAULT_MAX_SIZE;
        }
    }
}
