package com.fischer.heimdall.connections.detect;

import com.fischer.heimdall.connections.ConnectionInfo;
import jakarta.enterprise.context.ApplicationScoped;
import com.fischer.heimdall.tracing.HeimdallMonitor;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@HeimdallMonitor
public class HikariCPConnection {

    // HikariCP (register-mbeans=true) publishes, per pool:
    //   com.zaxxer.hikari:type=Pool (<poolName>)        live counts
    //   com.zaxxer.hikari:type=PoolConfig (<poolName>)  settings, incl. MaximumPoolSize
    // The pool name is only inside the "type" value — there is no "name" key.
    private static final Pattern POOL_TYPE = Pattern.compile("^Pool \\((.*)\\)$");

    public void detect(List<ConnectionInfo> connectionInfoList) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

        Set<ObjectName> hikariPools;
        try {
            hikariPools = mBeanServer.queryNames(new ObjectName("com.zaxxer.hikari:type=Pool (*),*"), null);
        } catch (MalformedObjectNameException e) {
            return; // constant pattern, can't happen
        }

        for (ObjectName poolBean : hikariPools) {
            Matcher m = POOL_TYPE.matcher(String.valueOf(poolBean.getKeyProperty("type")));
            String name = m.matches() ? m.group(1) : poolBean.getCanonicalName();

            int active  = getInt(mBeanServer, poolBean, "ActiveConnections");
            int idle    = getInt(mBeanServer, poolBean, "IdleConnections");
            int total   = getInt(mBeanServer, poolBean, "TotalConnections");
            int pending = getInt(mBeanServer, poolBean, "ThreadsAwaitingConnection");
            int max     = getInt(mBeanServer, configBean(name), "MaximumPoolSize");

            double usage = max > 0
                    ? Math.round((double) active / max * 1000.0) / 10.0
                    : 0.0;

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("activeConnections", active);
            stats.put("idleConnections",   idle);
            stats.put("totalConnections",  total);
            stats.put("pendingThreads",    pending);
            stats.put("maxConnections",    max);
            stats.put("usagePercent",      usage);

            connectionInfoList.add(ConnectionInfo.builder()
                    .name(name)
                    .type("database")
                    .technology("HikariCP")
                    .status(pending > 0 ? "DEGRADED" : "UP")
                    .stats(stats)
                    .checkedAt(Instant.now())
                    .build());
        }
    }

    private ObjectName configBean(String poolName) {
        try {
            return new ObjectName("com.zaxxer.hikari:type=PoolConfig (" + poolName + ")");
        } catch (MalformedObjectNameException e) {
            return null;
        }
    }

    /** 0 when the bean or attribute is missing or not a number. */
    private int getInt(MBeanServer server, ObjectName name, String attribute) {
        if (name == null) return 0;
        try {
            return server.getAttribute(name, attribute) instanceof Number n ? n.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
