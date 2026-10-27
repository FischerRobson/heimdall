package com.fischer.heimdall.beans;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-method call statistics for every {@code @HeimdallMonitor} bean of the host app.
 * Fed by HeimdallInterceptor on every call, whether or not an HTTP trace is active,
 * so scheduler and messaging calls are counted too.
 */
@ApplicationScoped
public class MethodStatsRegistry {

    // class FQN -> method name -> stats. Overloads of a method share one entry.
    private final Map<String, Map<String, MethodStats>> stats = new ConcurrentHashMap<>();

    @ConfigProperty(name = "heimdall.monitor.slow-threshold", defaultValue = "500")
    long slowThresholdMs;

    public void record(String className, String methodName, long durationNanos, Throwable failure) {
        stats.computeIfAbsent(className, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(methodName, k -> new MethodStats())
                .record(durationNanos, failure);
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    /** Beans with the worst health first, then the busiest. */
    public List<BeanStatsView> snapshot() {
        return stats.entrySet().stream()
                .map(e -> toView(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(BeanStatsView::health).reversed()
                        .thenComparing(Comparator.comparingLong(BeanStatsView::calls).reversed()))
                .toList();
    }

    public void reset() {
        stats.clear();
    }

    private BeanStatsView toView(String className, Map<String, MethodStats> methods) {
        List<MethodStatsView> rows = methods.entrySet().stream()
                .map(e -> e.getValue().snapshot(e.getKey(), slowThresholdMs))
                .sorted(Comparator.comparing(MethodStatsView::health).reversed()
                        .thenComparing(Comparator.comparingLong(MethodStatsView::calls).reversed()))
                .toList();

        long calls = rows.stream().mapToLong(MethodStatsView::calls).sum();
        long errors = rows.stream().mapToLong(MethodStatsView::errors).sum();
        // call-weighted average, so a hot fast method isn't drowned out by a rare slow one
        double avgMs = calls == 0 ? 0
                : Math.round(rows.stream().mapToDouble(r -> r.avgMs() * r.calls()).sum() / calls * 100) / 100.0;
        double maxMs = rows.stream().mapToDouble(MethodStatsView::maxMs).max().orElse(0);
        MethodStats.Health health = rows.stream().map(MethodStatsView::health)
                .max(Comparator.naturalOrder()).orElse(MethodStats.Health.ok);

        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return new BeanStatsView(className, simpleName, calls, errors, avgMs, maxMs, health, rows);
    }
}
