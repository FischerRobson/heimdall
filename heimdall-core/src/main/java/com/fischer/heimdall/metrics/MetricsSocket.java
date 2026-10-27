package com.fischer.heimdall.metrics;

import com.fischer.heimdall.history.MetricsHistory;
import com.fischer.heimdall.history.MetricsSnapshot;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import lombok.RequiredArgsConstructor;

import java.util.List;

@WebSocket(path = "/heimdall/ws/metrics", endpointId = MetricsSocket.ENDPOINT_ID)
@RequiredArgsConstructor
public class MetricsSocket {

    public static final String ENDPOINT_ID = "heimdall-metrics";

    private final MetricsHistory metricsHistory;

    @OnOpen
    List<MetricsSnapshot> onOpen() {
        return metricsHistory.getAll();
    }
}
