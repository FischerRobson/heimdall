package com.fischer.heimdall.metrics;

import com.fischer.heimdall.history.MetricsSnapshot;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import com.fischer.heimdall.tracing.HeimdallMonitor;

@ApplicationScoped
@RequiredArgsConstructor
@HeimdallMonitor
public class MetricsBroadcaster {

    private final OpenConnections openConnections;

    public void broadcast(MetricsSnapshot snapshot) {
        openConnections.findByEndpointId(MetricsSocket.ENDPOINT_ID).stream()
                .findAny()
                .ifPresent(connection -> connection.broadcast().sendTextAndAwait(snapshot));
    }
}
