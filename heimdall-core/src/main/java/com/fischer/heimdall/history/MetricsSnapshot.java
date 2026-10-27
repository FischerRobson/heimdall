package com.fischer.heimdall.history;

import lombok.Getter;
import com.fischer.heimdall.metrics.Metrics;

import java.time.Instant;

@Getter
public class MetricsSnapshot {
    private final Instant timestamp;
    private final Metrics metrics;

    public MetricsSnapshot(Metrics metrics) {
        this.metrics = metrics;
        this.timestamp = Instant.now();
    }
}
