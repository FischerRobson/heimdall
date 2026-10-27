package connections.detect;

import connections.ConnectionInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tracing.HeimdallMonitor;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@HeimdallMonitor
public class RabbitMQConnection {

    // one shared client (each HttpClient owns its own thread pool), with timeouts so an
    // unreachable management API can't hang /connections for minutes on every poll
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Inject
    BeanManager beanManager;

    @ConfigProperty(name = "rabbitmq-host")
    Optional<String> rabbitHost;

    @ConfigProperty(name = "rabbitmq-port", defaultValue = "5672")
    int rabbitPort;

    @ConfigProperty(name = "rabbitmq-username", defaultValue = "guest")
    String rabbitUsername;

    @ConfigProperty(name = "rabbitmq-password", defaultValue = "guest")
    String rabbitPassword;

    @ConfigProperty(name = "rabbitmq-management-port", defaultValue = "15672")
    int managementPort;

    public void detect(List<ConnectionInfo> connectionInfoList) {

        // strategy 1 — JMX MBeans
        List<ConnectionInfo> fromJmx = detectViaJmx();
        if (!fromJmx.isEmpty()) {
            connectionInfoList.addAll(fromJmx);
            return;
        }

        // strategy 2 — CDI bean detection
        boolean hasBeans = detectViaCdi();
        if (hasBeans) {
            List<ConnectionInfo> fromApi = detectViaManagementApi();
            if (!fromApi.isEmpty()) {
                connectionInfoList.addAll(fromApi);
            } else {
                connectionInfoList.add(buildFromConfig("UP"));
            }
            return;
        }

        // strategy 3 — config only
        if (rabbitHost.isPresent()) {
            List<ConnectionInfo> fromApi = detectViaManagementApi();
            if (!fromApi.isEmpty()) {
                connectionInfoList.addAll(fromApi);
            } else {
                connectionInfoList.add(buildFromConfig("UNKNOWN"));
            }
        }

        // nothing — RabbitMQ not configured, skip silently
    }

    private List<ConnectionInfo> detectViaJmx() {
        List<ConnectionInfo> result = new ArrayList<>();
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

        try {
            Set<ObjectName> rabbitBeans = mBeanServer.queryNames(
                    new ObjectName("com.rabbitmq:*"), null
            );

            if (rabbitBeans.isEmpty()) return result;

            for (ObjectName bean : rabbitBeans) {
                Map<String, Object> stats = new LinkedHashMap<>();

                double publishedMessages  = getDouble(mBeanServer, bean, "PublishedMessages");
                double consumedMessages   = getDouble(mBeanServer, bean, "ConsumedMessages");
                double failedToPublish    = getDouble(mBeanServer, bean, "FailedToPublishMessages");
                double acknowledgedMessages = getDouble(mBeanServer, bean, "AcknowledgedMessages");
                double rejectedMessages   = getDouble(mBeanServer, bean, "RejectedMessages");
                double connections        = getDouble(mBeanServer, bean, "Connections");
                double channels           = getDouble(mBeanServer, bean, "Channels");

                if (publishedMessages >= 0)    stats.put("publishedMessages",    (long) publishedMessages);
                if (consumedMessages >= 0)     stats.put("consumedMessages",     (long) consumedMessages);
                if (failedToPublish >= 0)      stats.put("failedToPublish",      (long) failedToPublish);
                if (acknowledgedMessages >= 0) stats.put("acknowledgedMessages", (long) acknowledgedMessages);
                if (rejectedMessages >= 0)     stats.put("rejectedMessages",     (long) rejectedMessages);
                if (connections >= 0)          stats.put("connections",          (int) connections);
                if (channels >= 0)             stats.put("channels",             (int) channels);

                if (rabbitHost.isPresent()) {
                    stats.put("host", rabbitHost.get());
                    stats.put("port", rabbitPort);
                }

                // use the raw double — the map stores it as Long, so casting the map
                // value to double threw ClassCastException and silently dropped detection
                String status = failedToPublish > 0 ? "DEGRADED" : "UP";

                result.add(ConnectionInfo.builder()
                        .name("RabbitMQ")
                        .type("messaging")
                        .technology("RabbitMQ")
                        .status(status)
                        .host(rabbitHost.orElse("unknown"))
                        .stats(stats)
                        .checkedAt(Instant.now())
                        .build());
            }

        } catch (Exception ignored) {}

        return result;
    }

    private List<ConnectionInfo> detectViaManagementApi() {
        List<ConnectionInfo> result = new ArrayList<>();

        if (rabbitHost.isEmpty()) return result;

        try {
            String url = String.format("http://%s:%d/api/overview",
                    rabbitHost.get(), managementPort);

            String credentials = Base64.getEncoder().encodeToString(
                    (rabbitUsername + ":" + rabbitPassword).getBytes()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + credentials)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> stats = parseOverview(response.body());
                stats.put("host", rabbitHost.get());
                stats.put("port", rabbitPort);
                stats.put("managementPort", managementPort);

                result.add(ConnectionInfo.builder()
                        .name("RabbitMQ")
                        .type("messaging")
                        .technology("RabbitMQ")
                        .status("UP")
                        .host(rabbitHost.get())
                        .stats(stats)
                        .checkedAt(Instant.now())
                        .build());
            }

        } catch (Exception ignored) {}

        return result;
    }

    private Map<String, Object> parseOverview(String json) {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("rawJson", json);

        // basic parsing without pulling in extra deps
        stats.put("queueTotals",    extractJsonValue(json, "queue_totals"));
        stats.put("messageStats",   extractJsonValue(json, "message_stats"));
        stats.put("objectTotals",   extractJsonValue(json, "object_totals"));
        stats.put("rabbitmqVersion", extractJsonString(json, "rabbitmq_version"));
        stats.put("erlangVersion",   extractJsonString(json, "erlang_version"));

        return stats;
    }

    private String extractJsonString(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start == -1) return "unknown";
            start += search.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            String search = "\"" + key + "\":{";
            int start = json.indexOf(search);
            if (start == -1) return "{}";
            start += search.length() - 1;
            int depth = 0;
            int end = start;
            for (int i = start; i < json.length(); i++) {
                if (json.charAt(i) == '{') depth++;
                if (json.charAt(i) == '}') depth--;
                if (depth == 0) { end = i + 1; break; }
            }
            return json.substring(start, end);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean detectViaCdi() {
        List<String> rabbitTypes = List.of(
                "io.smallrye.reactive.messaging.rabbitmq.RabbitMQConnector",
                "com.rabbitmq.client.Connection",
                "com.rabbitmq.client.Channel"
        );

        for (String typeName : rabbitTypes) {
            try {
                Class<?> clazz = Class.forName(typeName);
                if (!beanManager.getBeans(clazz).isEmpty()) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {}
        }

        return false;
    }

    private ConnectionInfo buildFromConfig(String status) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("host", rabbitHost.orElse("unknown"));
        stats.put("port", rabbitPort);

        return ConnectionInfo.builder()
                .name("RabbitMQ")
                .type("messaging")
                .technology("RabbitMQ")
                .status(status)
                .host(rabbitHost.orElse("unknown"))
                .stats(stats)
                .checkedAt(Instant.now())
                .build();
    }

    private double getDouble(MBeanServer server, ObjectName name, String attribute) {
        try {
            Object val = server.getAttribute(name, attribute);
            if (val instanceof Number) return ((Number) val).doubleValue();
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}