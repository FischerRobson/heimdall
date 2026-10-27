package com.fischer.heimdall.beans;

import com.fischer.heimdall.tracing.TraceContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sample.SampleService;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MethodStatsTest {

    @Inject
    MethodStatsRegistry registry;

    @Inject
    TraceContext traceContext;

    @Inject
    SampleService sampleService;

    @BeforeEach
    void reset() {
        registry.reset();
    }

    @Test
    void countsCallsWithoutActiveTrace() {
        // e.g. a @Scheduled job or a message consumer — no HTTP request, no trace
        assertFalse(traceContext.hasActiveTrace());

        sampleService.greet("a", "x");
        sampleService.greet("b", "x");

        MethodStatsView greet = method("sample.SampleService", "greet");
        assertEquals(2, greet.calls());
        assertEquals(0, greet.errors());
        assertEquals(MethodStats.Health.ok, greet.health());
        assertTrue(greet.lastCalledAt() > 0);
        assertTrue(greet.minMs() <= greet.maxMs());
    }

    @Test
    void errorsMarkMethodAndBeanUnhealthy() {
        sampleService.greet("a", "x");
        assertThrows(IllegalStateException.class, sampleService::fail);
        assertThrows(AssertionError.class, sampleService::failWithError);

        BeanStatsView bean = bean("sample.SampleService");
        assertEquals(3, bean.calls());
        assertEquals(2, bean.errors());
        assertEquals(MethodStats.Health.error, bean.health());
        // worst methods first
        assertEquals(MethodStats.Health.error, bean.methods().get(0).health());

        assertEquals("IllegalStateException: boom", method("sample.SampleService", "fail").lastError());
        assertEquals("AssertionError", method("sample.SampleService", "failWithError").lastError());
    }

    @Test
    void slowHealthAboveThreshold() {
        MethodStats stats = new MethodStats();
        stats.record(30_000_000, null); // 30 ms

        assertEquals(MethodStats.Health.slow, stats.snapshot("m", 10).health());
        assertEquals(MethodStats.Health.ok, stats.snapshot("m", 50).health());
        assertEquals(30.0, stats.snapshot("m", 50).avgMs());
    }

    @Test
    void heimdallOwnBeansAreNotCounted() {
        given().get("/heimdall/api/beans/graph").then().statusCode(200);

        assertTrue(registry.snapshot().stream().noneMatch(b -> b.id().startsWith("com.fischer.heimdall.")));
    }

    @Test
    void statsEndpointAndReset() {
        given().queryParam("name", "ana").get("/sample/hello").then().statusCode(200);

        given().get("/heimdall/api/beans/stats")
                .then()
                .statusCode(200)
                .body("slowThresholdMs", is(500))
                // ids match the bean graph's node ids (fully qualified names)
                .body("beans.id", hasItems("sample.SampleResource", "sample.SampleService"))
                .body("beans.find { it.id == 'sample.SampleService' }.methods[0].method", equalTo("greet"))
                .body("beans.find { it.id == 'sample.SampleService' }.health", equalTo("ok"));

        given().delete("/heimdall/api/beans/stats").then().statusCode(204);
        given().get("/heimdall/api/beans/stats").then().body("beans.size()", is(0));
    }

    private BeanStatsView bean(String id) {
        return registry.snapshot().stream()
                .filter(b -> b.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stats for " + id));
    }

    private MethodStatsView method(String beanId, String method) {
        List<MethodStatsView> methods = bean(beanId).methods();
        return methods.stream()
                .filter(m -> m.method().equals(method))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stats for " + beanId + "#" + method));
    }
}
