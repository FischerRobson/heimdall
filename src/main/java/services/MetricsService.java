package services;

import entities.Metrics;
import jakarta.enterprise.context.ApplicationScoped;
import utils.ByteConverter;

import java.lang.management.*;
import java.util.*;

@ApplicationScoped
public class MetricsService {

    private Map<String, Double> getMemoryInfo() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();
        Map<String, Double> map = new HashMap<>();

        map.put("initialMemory", ByteConverter.toMegabytes(memoryUsage.getInit()));
        map.put("inUsage", ByteConverter.toMegabytes(memoryUsage.getUsed()));
        map.put("committed", ByteConverter.toMegabytes(memoryUsage.getCommitted()));
        map.put("maxAllowed", ByteConverter.toMegabytes(memoryUsage.getMax()));

        double percentage = (double) memoryUsage.getUsed() / memoryUsage.getMax() * 100;

        map.put("percentage", Double.parseDouble(String.format("%.1f", percentage)));

        return map;
    }

    private List<Map<String, Object>> getGarbageCollectorInfo() {
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        List<Map<String, Object>> list = new ArrayList<>();
        for (GarbageCollectorMXBean gc: garbageCollectorMXBeans) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", gc.getName());
            map.put("collectionCount", gc.getCollectionCount() == -1 ? 0 : gc.getCollectionCount());
            map.put("collectionTime", gc.getCollectionTime() == -1 ? 0 : gc.getCollectionTime());
            list.add(map);
        }
        return list;
    }

    private Map<String, Integer> getThreadInfo() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Map<String, Integer> map = new HashMap<>();

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

        map.put("threadCount", threadMXBean.getThreadCount());
        map.put("threadBlocked", blocked);
        map.put("threadWaiting", waiting);
        map.put("threadTimedWaiting", timedWaiting);
        map.put("threadPeak", threadMXBean.getPeakThreadCount());
        return map;
    }

    private Map<String, Double> getCPUInfo() {
        com.sun.management.OperatingSystemMXBean osMXBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        Map<String, Double> map = new HashMap<>();

        double processCpu = osMXBean.getProcessCpuLoad();
        double systemCpu = osMXBean.getCpuLoad();
        double systemCpuPercent = systemCpu < 0 ? 0.0 : Math.round(systemCpu * 1000.0) / 10.0;
        map.put("systemCpuLoad", systemCpuPercent);

        map.put("processCpuLoad", processCpu == -1.0 ? 0.0 : Math.round(processCpu * 1000.0) / 10.0);
        map.put("availableProcessors", (double) osMXBean.getAvailableProcessors());
        map.put("totalMemorySize", ByteConverter.toMegabytes(osMXBean.getTotalMemorySize()));
        return map;
    }

    public Metrics getMetrics() {
        return new Metrics(getMemoryInfo(), getGarbageCollectorInfo(), getThreadInfo(), getCPUInfo());
    }

}
