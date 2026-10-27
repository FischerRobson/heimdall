package com.fischer.heimdall.connections;

import com.fischer.heimdall.connections.detect.HikariCPConnection;
import com.fischer.heimdall.connections.detect.KafkaConnection;
import com.fischer.heimdall.connections.detect.RabbitMQConnection;
import com.fischer.heimdall.connections.detect.RedisConnection;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Detectors that read client metrics from JMX, fed with fake MBeans. */
@QuarkusTest
class JmxDetectorsTest {

    @Inject
    KafkaConnection kafka;

    @Inject
    HikariCPConnection hikari;

    @Inject
    RedisConnection redis;

    @Inject
    RabbitMQConnection rabbit;

    final FakeMBeans mbeans = new FakeMBeans();

    @AfterEach
    void unregister() {
        mbeans.close();
    }

    @Test
    void nothingDetectedWithoutClients() {
        assertTrue(detect(kafka::detect).isEmpty());
        assertTrue(detect(hikari::detect).isEmpty());
        assertTrue(detect(redis::detect).isEmpty());
        assertTrue(detect(rabbit::detect).isEmpty());
    }

    // --- Kafka ------------------------------------------------------------------

    @Test
    void kafkaConsumerMergesFetchAndCoordinatorMetrics() {
        mbeans.register("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=orders-consumer", Map.of(
                        "records-lag-max", 12.0,
                        "fetch-rate", 3.14159,
                        "bytes-consumed-rate", 1024.0,
                        "records-per-request-avg", 5.0))
                .register("kafka.consumer:type=consumer-coordinator-metrics,client-id=orders-consumer", Map.of(
                        "commit-rate", 0.5,
                        "assigned-partitions", 3.0));

        ConnectionInfo consumer = single(detect(kafka::detect));
        assertEquals("orders-consumer", consumer.getName());
        assertEquals("Kafka Consumer", consumer.getTechnology());
        assertEquals("UP", consumer.getStatus());
        assertEquals(12.0, consumer.getStats().get("recordsLagMax"));
        assertEquals(3.14, consumer.getStats().get("fetchRate"));
        assertEquals(3, consumer.getStats().get("assignedPartitions"));
        assertEquals(0.5, consumer.getStats().get("commitRate"));
    }

    @Test
    void kafkaConsumerWithHighLagIsDegraded() {
        mbeans.register("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=slow",
                Map.of("records-lag-max", 5000.0));

        assertEquals("DEGRADED", single(detect(kafka::detect)).getStatus());
    }

    @Test
    void kafkaPerTopicBeansDoNotOverwriteClientTotals() {
        // the client publishes one bean per client and one per topic, both matching the query
        mbeans.register("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=c1",
                        Map.of("records-lag-max", 10.0, "bytes-consumed-rate", 900.0))
                .register("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=c1,topic=orders",
                        Map.of("bytes-consumed-rate", 100.0));

        ConnectionInfo consumer = single(detect(kafka::detect));
        assertEquals(900.0, consumer.getStats().get("bytesConsumedRate"));
    }

    @Test
    void kafkaMetricsWithoutDataAreSkipped() {
        // the Kafka client reports NaN / -Infinity until it has fetched something
        mbeans.register("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=idle",
                Map.of("records-lag-max", Double.NEGATIVE_INFINITY, "fetch-rate", Double.NaN));

        ConnectionInfo consumer = single(detect(kafka::detect));
        assertEquals("UP", consumer.getStatus());
        assertFalse(consumer.getStats().containsKey("recordsLagMax"));
        assertFalse(consumer.getStats().containsKey("fetchRate"));
    }

    @Test
    void kafkaProducerWithErrorsIsDegraded() {
        mbeans.register("kafka.producer:type=producer-metrics,client-id=ok-producer", Map.of(
                        "record-send-rate", 10.0, "record-error-rate", 0.0,
                        "request-latency-avg", 4.567, "request-latency-max", 20.0, "byte-rate", 2048.0))
                .register("kafka.producer:type=producer-metrics,client-id=bad-producer", Map.of(
                        "record-send-rate", 10.0, "record-error-rate", 0.25));

        List<ConnectionInfo> producers = detect(kafka::detect);
        assertEquals(2, producers.size());
        assertEquals("UP", byName(producers, "ok-producer").getStatus());
        assertEquals(4.57, byName(producers, "ok-producer").getStats().get("latencyAvgMs"));
        assertEquals("DEGRADED", byName(producers, "bad-producer").getStatus());
        assertEquals("Kafka Producer", byName(producers, "bad-producer").getTechnology());
    }

    // --- HikariCP ---------------------------------------------------------------

    @Test
    void hikariPoolReadsPoolAndConfigBeans() {
        // the names and attributes HikariCP really registers (register-mbeans=true):
        // the pool name is inside the "type" value, the max size lives on the config bean
        mbeans.register("com.zaxxer.hikari:type=Pool (orders-db)", Map.of(
                        "ActiveConnections", 4, "IdleConnections", 6,
                        "TotalConnections", 10, "ThreadsAwaitingConnection", 0))
                .register("com.zaxxer.hikari:type=PoolConfig (orders-db)", Map.of(
                        "MaximumPoolSize", 20));

        ConnectionInfo pool = single(detect(hikari::detect));
        assertEquals("orders-db", pool.getName());
        assertEquals("HikariCP", pool.getTechnology());
        assertEquals("UP", pool.getStatus());
        assertEquals(4, pool.getStats().get("activeConnections"));
        assertEquals(20, pool.getStats().get("maxConnections"));
        assertEquals(20.0, pool.getStats().get("usagePercent"));
    }

    @Test
    void hikariPoolWithWaitingThreadsIsDegraded() {
        mbeans.register("com.zaxxer.hikari:type=Pool (busy)", Map.of(
                "ActiveConnections", 10, "IdleConnections", 0,
                "TotalConnections", 10, "ThreadsAwaitingConnection", 3));

        ConnectionInfo pool = single(detect(hikari::detect));
        assertEquals("DEGRADED", pool.getStatus());
        // no config bean -> max unknown, usage not computed
        assertEquals(0, pool.getStats().get("maxConnections"));
        assertEquals(0.0, pool.getStats().get("usagePercent"));
    }

    @Test
    void connectionsEndpointSurvivesHikariPool() {
        mbeans.register("com.zaxxer.hikari:type=Pool (api-db)", Map.of(
                "ActiveConnections", 1, "IdleConnections", 1,
                "TotalConnections", 2, "ThreadsAwaitingConnection", 0));

        given().get("/heimdall/api/connections")
                .then()
                .statusCode(200)
                .body("find { it.technology == 'HikariCP' }.name", equalTo("api-db"));
    }

    // --- Redis ------------------------------------------------------------------

    @Test
    void redisClientsFromJmx() {
        mbeans.register("io.lettuce:type=Pool,name=cache", Map.of(
                        "ActiveCount", 2, "IdleCount", 6, "MaxConnections", 8))
                .register("org.redisson:type=Connections", Map.of(
                        "UsedConnections", 1, "FreeConnections", 9));

        List<ConnectionInfo> found = detect(redis::detect);
        assertEquals(2, found.size());

        ConnectionInfo lettuce = byName(found, "cache");
        assertEquals("Lettuce", lettuce.getTechnology());
        assertEquals("cache", lettuce.getType());
        assertEquals(2, lettuce.getStats().get("activeCount"));
        assertEquals(8, lettuce.getStats().get("maxConnections"));

        ConnectionInfo redisson = byName(found, "Redis"); // no name key -> default name
        assertEquals("Redisson", redisson.getTechnology());
        assertEquals(9, redisson.getStats().get("freeConnections"));
    }

    // --- RabbitMQ ---------------------------------------------------------------

    @Test
    void rabbitFromJmx() {
        mbeans.register("com.rabbitmq:type=Metrics", Map.of(
                "PublishedMessages", 100L, "ConsumedMessages", 90L, "FailedToPublishMessages", 0L,
                "Connections", 1, "Channels", 3));

        ConnectionInfo info = single(detect(rabbit::detect));
        assertEquals("RabbitMQ", info.getTechnology());
        assertEquals("UP", info.getStatus());
        assertEquals(100L, info.getStats().get("publishedMessages"));
        assertEquals(3, info.getStats().get("channels"));
        assertEquals("unknown", info.getHost());
    }

    @Test
    void rabbitWithFailedPublishesIsDegraded() {
        mbeans.register("com.rabbitmq:type=Metrics", Map.of("FailedToPublishMessages", 2L));

        assertEquals("DEGRADED", single(detect(rabbit::detect)).getStatus());
    }

    // ---------------------------------------------------------------------------

    private static List<ConnectionInfo> detect(Consumer<List<ConnectionInfo>> detector) {
        List<ConnectionInfo> result = new ArrayList<>();
        detector.accept(result);
        return result;
    }

    private static ConnectionInfo single(List<ConnectionInfo> found) {
        assertEquals(1, found.size(), () -> "expected one connection, got " + found.size());
        return found.get(0);
    }

    private static ConnectionInfo byName(List<ConnectionInfo> found, String name) {
        return found.stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no connection named " + name));
    }
}
