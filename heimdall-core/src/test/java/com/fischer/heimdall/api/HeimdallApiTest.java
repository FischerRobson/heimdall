package com.fischer.heimdall.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fischer.heimdall.tracing.TraceRegistry;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class HeimdallApiTest {

    @Inject
    TraceRegistry traceRegistry;

    @BeforeEach
    void clearTraces() {
        traceRegistry.clear();
    }

    @Test
    void metricsEndpointLivesUnderHeimdallPrefix() {
        given().when().get("/heimdall/api/metrics")
                .then()
                .statusCode(200)
                .body("memoryInfo.maxAllowed", greaterThan(0f))
                .body("jvmInfo.pid", greaterThan(0));
    }

    @Test
    void hostAppRoutesAreNotPrefixed() {
        // regression: a global quarkus.rest.path moved every host endpoint under /heimdall/api
        given().queryParam("name", "ana")
                .when().get("/sample/hello")
                .then().statusCode(200).body(equalTo("hello ana"));

        given().when().get("/heimdall/api/sample/hello")
                .then().statusCode(404);
    }

    @Test
    void hostRequestsAreTracedButHeimdallOwnAreNot() {
        given().queryParam("name", "ana").get("/sample/hello").then().statusCode(200);
        // dashboard polling — must not end up in the registry
        given().get("/heimdall/api/metrics").then().statusCode(200);
        given().get("/heimdall/api/connections").then().statusCode(200);

        given().when().get("/heimdall/api/traces")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].path", equalTo("/sample/hello"))
                .body("[0].queryParams.name", equalTo("ana"))
                // resource span + nested service span, with the password argument masked
                .body("[0].spans.methodName", hasItems("hello", "greet"))
                .body("[0].spans.find { it.methodName == 'greet' }.tags.password", equalTo("***"));
    }

    @Test
    void tracesCanBeFilteredByMethodPathParam() {
        given().get("/sample/hello").then().statusCode(200);

        given().when().get("/heimdall/api/traces/method/GET")
                .then().statusCode(200).body("size()", is(1));

        given().when().get("/heimdall/api/traces/method/POST")
                .then().statusCode(200).body("size()", is(0));
    }

    @Test
    void beanGraphUsesFullyQualifiedIds() {
        given().when().get("/heimdall/api/beans/graph")
                .then()
                .statusCode(200)
                .body("nodes.find { it.id == 'sample.SampleService' }.name", equalTo("SampleService"))
                .body("edges.find { it.from == 'sample.SampleResource' }.to", equalTo("sample.SampleService"));
    }

    @Test
    void threadDumpsAreKeptInHistory() {
        String first  = given().get("/heimdall/api/threads/dump").then().statusCode(200).extract().path("id");
        String second = given().get("/heimdall/api/threads/dump").then().statusCode(200).extract().path("id");

        given().when().get("/heimdall/api/threads/dumps")
                .then()
                .statusCode(200)
                .body("[0].id", equalTo(second))   // newest first
                .body("[1].id", equalTo(first))
                .body("[0].stateCounts.size()", greaterThan(0))
                .body("[0].threads", nullValue()); // summaries carry no stack traces

        given().get("/heimdall/api/threads/dumps/" + first)
                .then().statusCode(200).body("threads.size()", greaterThan(0));

        given().get("/heimdall/api/threads/dumps/does-not-exist")
                .then().statusCode(404);
    }

    @Test
    void threadDumpAndHealthRespond() {
        given().get("/heimdall/api/threads/dump")
                .then().statusCode(200).body("threadCount", greaterThan(0));

        given().get("/heimdall/api/health")
                .then().statusCode(200).body("status", equalTo("UP"));
    }
}
