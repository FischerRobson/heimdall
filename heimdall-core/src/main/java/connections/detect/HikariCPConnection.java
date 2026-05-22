package connections.detect;

import connections.ConnectionInfo;
import jakarta.enterprise.context.ApplicationScoped;
import tracing.HeimdallMonitor;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@HeimdallMonitor
public class HikariCPConnection {

    public void detect(List<ConnectionInfo> connectionInfoList) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

        try {
            ObjectName hikariPattern = new ObjectName("com.zaxxer.hikari:type=Pool (*),*");
            Set<ObjectName> hikariPools = mBeanServer.queryNames(hikariPattern, null);

            for (ObjectName poolName : hikariPools) {
                int active  = (int) getAttribute(mBeanServer, poolName, "ActiveConnections");
                int idle    = (int) getAttribute(mBeanServer, poolName, "IdleConnections");
                int total   = (int) getAttribute(mBeanServer, poolName, "TotalConnections");
                int pending = (int) getAttribute(mBeanServer, poolName, "ThreadsAwaitingConnection");
                int max     = (int) getAttribute(mBeanServer, poolName, "MaxConnections");

                double usage = max > 0
                        ? Math.round((double) active / max * 1000.0) / 10.0
                        : 0.0;

                String status = pending > 0 ? "DEGRADED" : "UP";

                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("activeConnections", active);
                stats.put("idleConnections",   idle);
                stats.put("totalConnections",  total);
                stats.put("pendingThreads",    pending);
                stats.put("maxConnections",    max);
                stats.put("usagePercent",      usage);

                String name = poolName.getKeyProperty("name")
                        .replaceAll("[()]", "");

                connectionInfoList.add(ConnectionInfo.builder()
                        .name(name)
                        .type("database")
                        .technology("HikariCP")
                        .status(status)
                        .stats(stats)
                        .checkedAt(Instant.now())
                        .build());
            }

        } catch (MalformedObjectNameException e) {
            connectionInfoList.add(ConnectionInfo.builder()
                    .name("HikariCP")
                    .type("database")
                    .technology("HikariCP")
                    .status("DOWN")
                    .stats(Map.of("error", e.getMessage()))
                    .checkedAt(Instant.now())
                    .build());
        }
    }

    private Object getAttribute(MBeanServer server, ObjectName name, String attribute) {
        try {
            return server.getAttribute(name, attribute);
        } catch (Exception e) {
            return 0;
        }
    }

}
