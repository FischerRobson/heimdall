package com.fischer.heimdall.tracing;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** What TraceFilter captures from a host request, and what it hides. */
@QuarkusTest
class TraceCaptureTest {

    @Inject
    TraceRegistry traceRegistry;

    @Inject
    TraceContext traceContext;

    @BeforeEach
    void clear() {
        traceRegistry.clear();
    }

    @Test
    void bodiesAreCapturedWithSecretsRedacted() {
        given().contentType(ContentType.JSON)
                .body("{\"user\":\"ana\",\"password\":\"hunter2\"}")
                .post("/sample/login")
                .then().statusCode(200).body("user", equalTo("ana"));

        given().get("/heimdall/api/traces")
                .then()
                .body("[0].httpMethod", equalTo("POST"))
                .body("[0].status", is(200))
                .body("[0].requestBody", containsString("\"user\":\"ana\""))
                .body("[0].requestBody", containsString("\"password\":\"***\""))
                .body("[0].requestBody", not(containsString("hunter2")))
                // the response body is redacted too
                .body("[0].responseBody", containsString("\"token\":\"***\""))
                .body("[0].responseBody", not(containsString("abc.def.ghi")))
                .body("[0].responseHeaders.'content-type'", startsWith("application/json"));
    }

    @Test
    void authorizationHeaderAndSessionCookiesAreMasked() {
        given().header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature")
                .header("X-Short", "x")
                .cookie("session", "0123456789abcdef")
                .cookie("theme", "dark")
                .queryParam("name", "ana")
                .get("/sample/hello")
                .then().statusCode(200);

        given().get("/heimdall/api/traces")
                .then()
                .body("[0].requestHeaders.authorization", equalTo("Bearer eyJhbGciO..."))
                .body("[0].requestHeaders.'x-short'", equalTo("x"))
                .body("[0].cookies.session", equalTo("01234567..."))
                .body("[0].cookies.theme", equalTo("dark"));
    }

    @Test
    void shortAuthorizationIsFullyMasked() {
        given().header("Authorization", "Basic abc").get("/sample/hello").then().statusCode(200);

        given().get("/heimdall/api/traces")
                .then().body("[0].requestHeaders.authorization", equalTo("***"));
    }

    @Test
    void customTagsFromApplicationCode() {
        given().contentType(ContentType.JSON)
                .body("{\"user\":\"bia\"}")
                .post("/sample/login")
                .then().statusCode(200);

        given().get("/heimdall/api/traces")
                .then()
                .body("[0].spans.find { it.methodName == 'tagged' }.tags.user", equalTo("bia"))
                .body("[0].spans.find { it.methodName == 'tagged' }.tags.attempt", equalTo("1"))
                .body("[0].spans.find { it.methodName == 'tagged' }.tags.missing", equalTo("null"))
                .body("[0].spans.find { it.methodName == 'tagged' }.tags.' '", nullValue());
    }

    @Test
    void tagOutsideOfTraceIsNoOp() {
        assertFalse(traceContext.hasActiveTrace());
        assertDoesNotThrow(() -> Heimdall.tag("orderId", 42));
    }

    @Test
    void errorTracesCanBeListedSeparately() {
        given().get("/sample/hello").then().statusCode(200);
        given().get("/sample/boom").then().statusCode(500);

        given().get("/heimdall/api/traces")
                .then().body("size()", is(2));

        given().get("/heimdall/api/traces/errors")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].path", equalTo("/sample/boom"));
    }

    @Test
    void registryKeepsNewestFirstAndRespectsLimit() {
        TraceRegistry registry = new TraceRegistry();
        registry.limit = 2;

        registry.add(trace("a", "GET"));
        registry.add(trace("b", "POST"));
        registry.add(trace("c", "get"));
        registry.add(null); // ignored

        assertEquals(java.util.List.of("c", "b"),
                registry.getAll().stream().map(HeimdallTrace::getId).toList());
        // method filter is case-insensitive
        assertEquals(1, registry.getByMethod("GET").size());
    }

    private static HeimdallTrace trace(String id, String method) {
        return HeimdallTrace.builder().id(id).httpMethod(method).build();
    }
}
