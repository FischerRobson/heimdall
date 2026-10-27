package com.fischer.heimdall.metrics;

import com.fischer.heimdall.health.HeimdallHealthCheck;
import com.fischer.heimdall.history.MetricsHistory;
import com.fischer.heimdall.history.MetricsSnapshot;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MetricsApiTest {

    @Inject
    MetricsHistory metricsHistory;

    @Inject
    MetricsService metricsService;

    @Inject
    @Readiness
    HeimdallHealthCheck healthCheck;

    @Test
    void historyReturnsCollectedSnapshots() {
        metricsHistory.add(new MetricsSnapshot(metricsService.getMetrics()));

        given().get("/heimdall/api/metrics/history")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].metrics.memoryInfo.maxAllowed", greaterThan(0f));
    }

    @Test
    void garbageCollectionCanBeTriggered() {
        given().post("/heimdall/api/metrics/gc")
                .then()
                .statusCode(200)
                .body("before.inUsage", notNullValue())
                .body("after.inUsage", notNullValue())
                .body("durationMs", greaterThanOrEqualTo(0));
    }

    @Test
    void heapDumpIsStreamedAsAttachment() {
        Response response = given().queryParam("live", true).get("/heimdall/api/metrics/heapdump");

        assertEquals(200, response.statusCode());
        assertTrue(response.header("Content-Disposition").matches("attachment; filename=\"heimdall-heap-\\d+-\\d{8}-\\d{6}\\.hprof\""),
                response.header("Content-Disposition"));
        byte[] body = response.asByteArray();
        assertEquals(Long.parseLong(response.header("Content-Length")), body.length);
        // .hprof files start with "JAVA PROFILE 1.0.x"
        assertTrue(new String(body, 0, 12).startsWith("JAVA PROFILE"));
    }

    @Test
    void readinessReportsCollectingOnceSnapshotsExist() {
        metricsHistory.add(new MetricsSnapshot(metricsService.getMetrics()));

        HealthCheckResponse response = healthCheck.call();
        assertEquals("heimdall", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("collecting", response.getData().orElseThrow().get("status"));

        given().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("checks.find { it.name == 'heimdall' }.status", equalTo("UP"));
    }

    @Test
    void healthEndpointCountsSnapshots() {
        metricsHistory.add(new MetricsSnapshot(metricsService.getMetrics()));

        given().get("/heimdall/api/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("snapshots", greaterThan(0));
    }
}
