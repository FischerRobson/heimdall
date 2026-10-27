package com.fischer.heimdall.connections;

import com.fischer.heimdall.connections.detect.AgroalConnection;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AgroalConnectionTest {

    @Inject
    MeterRegistry registry;

    @Inject
    AgroalConnection agroalConnection;

    @Test
    void readsPoolGaugesPerDatasource() {
        // simulate the gauges Quarkus publishes for an Agroal datasource named "orders"
        Gauge.builder("agroal.active.count", () -> 3).tag("datasource", "orders").register(registry);
        Gauge.builder("agroal.available.count", () -> 2).tag("datasource", "orders").register(registry);
        Gauge.builder("agroal.awaiting.count", () -> 1).tag("datasource", "orders").register(registry);

        List<ConnectionInfo> result = new ArrayList<>();
        agroalConnection.detect(result);

        ConnectionInfo orders = result.stream()
                .filter(c -> "orders".equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("datasource 'orders' not detected: " + result));

        assertEquals("Agroal", orders.getTechnology());
        assertEquals("database", orders.getType());
        assertEquals("DEGRADED", orders.getStatus(), "threads awaiting a connection should degrade");
        assertEquals(3, orders.getStats().get("activeConnections"));
        assertEquals(2, orders.getStats().get("idleConnections"));
        assertEquals(5, orders.getStats().get("totalConnections"));
        assertEquals(20, orders.getStats().get("maxConnections"));
        assertEquals(15.0, orders.getStats().get("usagePercent"));
    }
}
