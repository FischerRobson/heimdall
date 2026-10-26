package metrics;

import history.MetricsSnapshot;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import tracing.HeimdallMonitor;

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
