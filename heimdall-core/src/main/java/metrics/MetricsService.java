package metrics;

import jakarta.enterprise.context.ApplicationScoped;
import tracing.HeimdallMonitor;
import utils.ByteConverter;
import utils.TimeConverter;

import java.lang.management.*;
import java.lang.management.ThreadInfo;
import java.util.*;

@ApplicationScoped
@HeimdallMonitor
public class MetricsService {

    public MemoryInfo getMemoryInfo() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();

        MemoryInfo memoryInfo = new MemoryInfo();

        memoryInfo.setInitialMemory(ByteConverter.toMegabytes(memoryUsage.getInit()));
        memoryInfo.setInUsage(ByteConverter.toMegabytes(memoryUsage.getUsed()));
        memoryInfo.setCommitted(ByteConverter.toMegabytes(memoryUsage.getCommitted()));
        // getMax() is -1 when no max heap is defined — fall back to committed so the
        // percentage stays meaningful instead of going negative
        long max = memoryUsage.getMax() > 0 ? memoryUsage.getMax() : memoryUsage.getCommitted();
        memoryInfo.setMaxAllowed(ByteConverter.toMegabytes(max));

        // rounded arithmetically — String.format is locale-dependent ("45,3" in pt_BR)
        // and made Double.parseDouble throw
        double percentage = max > 0 ? (double) memoryUsage.getUsed() / max * 100 : 0.0;
        memoryInfo.setPercentage(Math.round(percentage * 10.0) / 10.0);

        return memoryInfo;
    }

    private GarbageCollectorInfo getGarbageCollectorInfo() {
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        GarbageCollectorInfo garbageCollectorInfo = new GarbageCollectorInfo();
        for (GarbageCollectorMXBean gc: garbageCollectorMXBeans) {
            GarbageCollector garbageCollector = new GarbageCollector();
            garbageCollector.setName(gc.getName());
            garbageCollector.setCollectionTime(gc.getCollectionTime() == -1 ? 0 : gc.getCollectionTime());
            garbageCollector.setCollectionCount(gc.getCollectionCount() == -1 ? 0 : gc.getCollectionCount());
            garbageCollectorInfo.addGarbageCollector(garbageCollector);
        }
        return garbageCollectorInfo;
    }

    private metrics.ThreadInfo getThreadInfo() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        long[] ids = threadMXBean.getAllThreadIds();

        int blocked = 0;
        int waiting = 0;
        int timedWaiting = 0;

        for(long id: ids) {
            ThreadInfo threadInfo = threadMXBean.getThreadInfo(id);
            if (threadInfo == null) continue;
            Thread.State state = threadInfo.getThreadState();
            if (state == Thread.State.BLOCKED){
                blocked++;
            } else if (state == Thread.State.TIMED_WAITING) {
                timedWaiting++;
            } else if (state == Thread.State.WAITING) {
                waiting++;
            }
        }

        metrics.ThreadInfo metricsThreadInfo = new metrics.ThreadInfo();
        metricsThreadInfo.setThreadCount(threadMXBean.getThreadCount());
        metricsThreadInfo.setThreadBlocked(blocked);
        metricsThreadInfo.setThreadWaiting(waiting);
        metricsThreadInfo.setThreadTimedWaiting(timedWaiting);
        metricsThreadInfo.setThreadPeak(threadMXBean.getPeakThreadCount());

        return metricsThreadInfo;
    }

    private CpuInfo getCPUInfo() {
        com.sun.management.OperatingSystemMXBean osMXBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double processCpu = osMXBean.getProcessCpuLoad();
        double systemCpu = osMXBean.getCpuLoad();
        double systemCpuPercent = systemCpu < 0 ? 0.0 : Math.round(systemCpu * 1000.0) / 10.0;

        CpuInfo cpuInfo = new CpuInfo();
        cpuInfo.setSystemCpuLoad(systemCpuPercent);

        cpuInfo.setProcessCpuLoad(processCpu == -1.0 ? 0.0 : Math.round(processCpu * 1000.0) / 10.0);
        cpuInfo.setAvailableProcessors(osMXBean.getAvailableProcessors());
        cpuInfo.setTotalMemorySize(ByteConverter.toMegabytes(osMXBean.getTotalMemorySize()));
        return cpuInfo;
    }

    private JvmInfo getJvmInfo() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtimeMXBean.getUptime();

        JvmInfo jvmInfo = new JvmInfo();
        jvmInfo.setJvmName(runtimeMXBean.getVmName());
        jvmInfo.setPid(ProcessHandle.current().pid());
        jvmInfo.setUptime(TimeConverter.toTime(uptimeMs));
        jvmInfo.setJavaVersion(System.getProperty("java.version"));
        return jvmInfo;
    }

    public Metrics getMetrics() {
        return new Metrics(getMemoryInfo(), getGarbageCollectorInfo(), getThreadInfo(), getCPUInfo(), getJvmInfo());
    }

    public GcResult triggerGarbageCollection() {
        MemoryInfo before = getMemoryInfo();

        long start = System.currentTimeMillis();
        System.gc();
        long durationMs = System.currentTimeMillis() - start;

        MemoryInfo after = getMemoryInfo();
        double freedMb = before.getInUsage() - after.getInUsage();

        return new GcResult(before, after, freedMb, durationMs);
    }

}
