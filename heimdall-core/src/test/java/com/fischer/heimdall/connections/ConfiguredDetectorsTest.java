package com.fischer.heimdall.connections;

import com.fischer.heimdall.connections.detect.RabbitMQConnection;
import com.fischer.heimdall.connections.detect.RedisConnection;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Detectors that fall back to configuration: RabbitMQ through its management API
 * (served here by a local HTTP server) and Redis from quarkus.redis.hosts alone.
 */
@QuarkusTest
@TestProfile(ConfiguredDetectorsTest.Profile.class)
class ConfiguredDetectorsTest {

    // The profile is read by JUnit's class loader and the test runs in Quarkus' — this
    // class is initialized twice, so the port is shared through a system property instead
    // of a plain static (two random ports otherwise).
    static final int MANAGEMENT_PORT = Integer.parseInt((String) System.getProperties()
            .computeIfAbsent("heimdall.test.rabbitmq-management-port", k -> String.valueOf(freePort())));

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "rabbitmq-host", "localhost",
                    "rabbitmq-port", "5673",
                    "rabbitmq-username", "heimdall",
                    "rabbitmq-password", "s3cret",
                    "rabbitmq-management-port", String.valueOf(MANAGEMENT_PORT),
                    "quarkus.redis.hosts", "redis://cache:6380");
        }
    }

    static HttpServer managementApi;
    static final AtomicInteger status = new AtomicInteger(200);
    static final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    static final String OVERVIEW = """
            {"management_version":"3.13.0","rabbitmq_version":"3.13.0","erlang_version":"26.2",\
            "message_stats":{"publish":10,"deliver_get":8},\
            "queue_totals":{"messages":2,"messages_ready":1},\
            "object_totals":{"connections":1,"channels":2,"queues":3}}""";

    @Inject
    RabbitMQConnection rabbit;

    @Inject
    RedisConnection redis;

    @BeforeAll
    static void startManagementApi() throws IOException {
        managementApi = HttpServer.create(new InetSocketAddress("localhost", MANAGEMENT_PORT), 0);
        managementApi.createContext("/api/overview", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = OVERVIEW.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        managementApi.start();
    }

    @AfterAll
    static void stopManagementApi() {
        managementApi.stop(0);
    }

    @Test
    void rabbitReadFromManagementApi() {
        status.set(200);

        List<ConnectionInfo> found = new ArrayList<>();
        rabbit.detect(found);

        assertEquals(1, found.size());
        ConnectionInfo info = found.get(0);
        assertEquals("UP", info.getStatus());
        assertEquals("localhost", info.getHost());
        assertEquals("3.13.0", info.getStats().get("rabbitmqVersion"));
        assertEquals("26.2", info.getStats().get("erlangVersion"));
        assertEquals("{\"messages\":2,\"messages_ready\":1}", info.getStats().get("queueTotals"));
        assertEquals("{\"connections\":1,\"channels\":2,\"queues\":3}", info.getStats().get("objectTotals"));
        assertEquals(5673, info.getStats().get("port"));
        assertEquals(MANAGEMENT_PORT, info.getStats().get("managementPort"));
        // the whole overview document used to be shown in the UI as "rawJson"
        assertFalse(info.getStats().containsKey("rawJson"));

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("heimdall:s3cret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, lastAuthorization.get());
    }

    @Test
    void rabbitFallsBackToConfigWhenApiRefuses() {
        status.set(401);

        List<ConnectionInfo> found = new ArrayList<>();
        rabbit.detect(found);

        assertEquals(1, found.size());
        assertEquals("UNKNOWN", found.get(0).getStatus());
        assertEquals(Map.of("host", "localhost", "port", 5673), found.get(0).getStats());
    }

    @Test
    void redisFromConfigOnly() {
        List<ConnectionInfo> found = new ArrayList<>();
        redis.detect(found);

        assertEquals(1, found.size());
        assertEquals("Redis", found.get(0).getTechnology());
        assertEquals("UNKNOWN", found.get(0).getStatus());
        assertEquals("redis://cache:6380", found.get(0).getHost());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
