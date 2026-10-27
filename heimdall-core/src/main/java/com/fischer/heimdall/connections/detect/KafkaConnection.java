package com.fischer.heimdall.connections.detect;

import com.fischer.heimdall.connections.ConnectionInfo;
import jakarta.enterprise.context.ApplicationScoped;
import com.fischer.heimdall.tracing.HeimdallMonitor;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@HeimdallMonitor
public class KafkaConnection {

    public void detect(List<ConnectionInfo> connectionInfoList) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

        List<ConnectionInfo> consumers = detectConsumers(mBeanServer);
        List<ConnectionInfo> producers = detectProducers(mBeanServer);

        connectionInfoList.addAll(consumers);
        connectionInfoList.addAll(producers);
    }

    private List<ConnectionInfo> detectConsumers(MBeanServer mBeanServer) {
        List<ConnectionInfo> result = new ArrayList<>();

        try {
            Set<ObjectName> consumerBeans = mBeanServer.queryNames(
                    new ObjectName("kafka.consumer:type=consumer-fetch-manager-metrics,*"), null
            );

            Map<String, Map<String, Object>> byClient = new LinkedHashMap<>();

            for (ObjectName bean : consumerBeans) {
                String clientId = bean.getKeyProperty("client-id");
                if (clientId == null) continue;
                // per-topic / per-partition beans match the same query; only the
                // client-level bean holds the totals, the others would overwrite them
                if (bean.getKeyProperty("topic") != null || bean.getKeyProperty("partition") != null) continue;

                byClient.computeIfAbsent(clientId, k -> new LinkedHashMap<>());
                Map<String, Object> stats = byClient.get(clientId);

                double recordsLag     = getDouble(mBeanServer, bean, "records-lag-max");
                double fetchRate      = getDouble(mBeanServer, bean, "fetch-rate");
                double bytesConsumed  = getDouble(mBeanServer, bean, "bytes-consumed-rate");
                double recordsPerReq  = getDouble(mBeanServer, bean, "records-per-request-avg");

                if (recordsLag >= 0)    stats.put("recordsLagMax",       recordsLag);
                if (fetchRate >= 0)     stats.put("fetchRate",            Math.round(fetchRate * 100.0) / 100.0);
                if (bytesConsumed >= 0) stats.put("bytesConsumedRate",    Math.round(bytesConsumed * 100.0) / 100.0);
                if (recordsPerReq >= 0) stats.put("recordsPerRequestAvg", Math.round(recordsPerReq * 100.0) / 100.0);
            }

            // consumer coordinator metrics — committed offsets, lag
            Set<ObjectName> coordinatorBeans = mBeanServer.queryNames(
                    new ObjectName("kafka.consumer:type=consumer-coordinator-metrics,*"), null
            );

            for (ObjectName bean : coordinatorBeans) {
                String clientId = bean.getKeyProperty("client-id");
                if (clientId == null) continue;

                byClient.computeIfAbsent(clientId, k -> new LinkedHashMap<>());
                Map<String, Object> stats = byClient.get(clientId);

                double commitRate = getDouble(mBeanServer, bean, "commit-rate");
                double assignedPartitions = getDouble(mBeanServer, bean, "assigned-partitions");

                if (commitRate >= 0)          stats.put("commitRate",          Math.round(commitRate * 100.0) / 100.0);
                if (assignedPartitions >= 0)  stats.put("assignedPartitions",  (int) assignedPartitions);
            }

            for (Map.Entry<String, Map<String, Object>> entry : byClient.entrySet()) {
                Map<String, Object> stats = entry.getValue();

                double lag = (double) stats.getOrDefault("recordsLagMax", 0.0);
                String status = lag > 1000 ? "DEGRADED" : "UP";

                result.add(ConnectionInfo.builder()
                        .name(entry.getKey())
                        .type("messaging")
                        .technology("Kafka Consumer")
                        .status(status)
                        .stats(stats)
                        .checkedAt(Instant.now())
                        .build());
            }

        } catch (Exception e) {
            result.add(ConnectionInfo.builder()
                    .name("Kafka Consumer")
                    .type("messaging")
                    .technology("Kafka Consumer")
                    .status("DOWN")
                    .stats(Map.of("error", String.valueOf(e.getMessage())))
                    .checkedAt(Instant.now())
                    .build());
        }

        return result;
    }

    private List<ConnectionInfo> detectProducers(MBeanServer mBeanServer) {
        List<ConnectionInfo> result = new ArrayList<>();

        try {
            Set<ObjectName> producerBeans = mBeanServer.queryNames(
                    new ObjectName("kafka.producer:type=producer-metrics,*"), null
            );

            Map<String, Map<String, Object>> byClient = new LinkedHashMap<>();

            for (ObjectName bean : producerBeans) {
                String clientId = bean.getKeyProperty("client-id");
                if (clientId == null) continue;

                byClient.computeIfAbsent(clientId, k -> new LinkedHashMap<>());
                Map<String, Object> stats = byClient.get(clientId);

                double sendRate      = getDouble(mBeanServer, bean, "record-send-rate");
                double errorRate     = getDouble(mBeanServer, bean, "record-error-rate");
                double latencyAvg    = getDouble(mBeanServer, bean, "request-latency-avg");
                double latencyMax    = getDouble(mBeanServer, bean, "request-latency-max");
                double bytesSentRate = getDouble(mBeanServer, bean, "byte-rate");

                if (sendRate >= 0)      stats.put("recordSendRate",    Math.round(sendRate * 100.0) / 100.0);
                if (errorRate >= 0)     stats.put("recordErrorRate",   Math.round(errorRate * 100.0) / 100.0);
                if (latencyAvg >= 0)    stats.put("latencyAvgMs",      Math.round(latencyAvg * 100.0) / 100.0);
                if (latencyMax >= 0)    stats.put("latencyMaxMs",      Math.round(latencyMax * 100.0) / 100.0);
                if (bytesSentRate >= 0) stats.put("bytesSentRate",     Math.round(bytesSentRate * 100.0) / 100.0);
            }

            for (Map.Entry<String, Map<String, Object>> entry : byClient.entrySet()) {
                Map<String, Object> stats = entry.getValue();

                double errorRate = (double) stats.getOrDefault("recordErrorRate", 0.0);
                String status = errorRate > 0 ? "DEGRADED" : "UP";

                result.add(ConnectionInfo.builder()
                        .name(entry.getKey())
                        .type("messaging")
                        .technology("Kafka Producer")
                        .status(status)
                        .stats(stats)
                        .checkedAt(Instant.now())
                        .build());
            }

        } catch (Exception e) {
            result.add(ConnectionInfo.builder()
                    .name("Kafka Producer")
                    .type("messaging")
                    .technology("Kafka Producer")
                    .status("DOWN")
                    .stats(Map.of("error", String.valueOf(e.getMessage())))
                    .checkedAt(Instant.now())
                    .build());
        }

        return result;
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