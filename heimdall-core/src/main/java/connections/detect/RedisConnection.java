package connections.detect;

import connections.ConnectionInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class RedisConnection {

    @Inject
    BeanManager beanManager;

    @ConfigProperty(name = "quarkus.redis.hosts")
    Optional<String> redisHosts;

    @ConfigProperty(name = "quarkus.redis.port", defaultValue = "6379")
    int redisPort;

    public void detect(List<ConnectionInfo> connectionInfoList) {

        // strategy 1 — JMX MBeans (Lettuce client registers these)
        List<ConnectionInfo> fromJmx = detectViaJmx();
        if (!fromJmx.isEmpty()) {
            connectionInfoList.addAll(fromJmx);
            return;
        }

        // strategy 2 — CDI bean detection
        boolean hasRedisBeans = detectViaCdi();
        if (hasRedisBeans) {
            connectionInfoList.add(buildFromConfig("UP"));
            return;
        }

        // strategy 3 — config only
        if (redisHosts.isPresent()) {
            connectionInfoList.add(buildFromConfig("UNKNOWN"));
        }

        // if none of the above — Redis is not configured, return nothing
    }

    private List<ConnectionInfo> detectViaJmx() {
        List<ConnectionInfo> result = new ArrayList<>();
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

        try {
            // Lettuce connection pool MBeans
            Set<ObjectName> lettucePoolBeans = mBeanServer.queryNames(
                    new ObjectName("io.lettuce:*"), null
            );

            // Redisson MBeans
            Set<ObjectName> redissonBeans = mBeanServer.queryNames(
                    new ObjectName("org.redisson:*"), null
            );

            Set<ObjectName> allBeans = new HashSet<>();
            allBeans.addAll(lettucePoolBeans);
            allBeans.addAll(redissonBeans);

            if (allBeans.isEmpty()) return result;

            for (ObjectName bean : allBeans) {
                Map<String, Object> stats = new LinkedHashMap<>();

                double usedConnections  = getDouble(mBeanServer, bean, "UsedConnections");
                double freeConnections  = getDouble(mBeanServer, bean, "FreeConnections");
                double maxConnections   = getDouble(mBeanServer, bean, "MaxConnections");
                double activeCount      = getDouble(mBeanServer, bean, "ActiveCount");
                double idleCount        = getDouble(mBeanServer, bean, "IdleCount");

                if (usedConnections >= 0)  stats.put("usedConnections",  (int) usedConnections);
                if (freeConnections >= 0)  stats.put("freeConnections",  (int) freeConnections);
                if (maxConnections >= 0)   stats.put("maxConnections",   (int) maxConnections);
                if (activeCount >= 0)      stats.put("activeCount",      (int) activeCount);
                if (idleCount >= 0)        stats.put("idleCount",        (int) idleCount);

                if (redisHosts.isPresent()) {
                    stats.put("host", redisHosts.get());
                }

                result.add(ConnectionInfo.builder()
                        .name(bean.getKeyProperty("name") != null
                                ? bean.getKeyProperty("name")
                                : "Redis")
                        .type("cache")
                        .technology(bean.getDomain().contains("redisson") ? "Redisson" : "Lettuce")
                        .status("UP")
                        .host(redisHosts.orElse("unknown"))
                        .stats(stats)
                        .checkedAt(Instant.now())
                        .build());
            }

        } catch (Exception ignored) {}

        return result;
    }

    private boolean detectViaCdi() {
        try {
            // check for Quarkus Redis datasource bean
            var redisTypes = List.of(
                    "io.quarkus.redis.datasource.ReactiveRedisDataSource",
                    "io.quarkus.redis.datasource.RedisDataSource",
                    "io.vertx.redis.client.Redis"
            );

            for (String typeName : redisTypes) {
                try {
                    Class<?> clazz = Class.forName(typeName);
                    if (!beanManager.getBeans(clazz).isEmpty()) {
                        return true;
                    }
                } catch (ClassNotFoundException ignored) {}
            }

        } catch (Exception ignored) {}

        return false;
    }

    private ConnectionInfo buildFromConfig(String status) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("host", redisHosts.orElse("unknown"));
        stats.put("port", redisPort);

        return ConnectionInfo.builder()
                .name("Redis")
                .type("cache")
                .technology("Redis")
                .status(status)
                .host(redisHosts.orElse("unknown"))
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