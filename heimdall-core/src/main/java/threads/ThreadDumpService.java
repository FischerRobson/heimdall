package threads;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tracing.HeimdallMonitor;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@HeimdallMonitor
public class ThreadDumpService {

    @ConfigProperty(name = "heimdall.threaddump.max-depth", defaultValue = "100")
    int maxStackDepth;

    public ThreadDump dump() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
        Set<Long> deadlockedIds = findDeadlockedIds(threadMXBean);

        List<ThreadDumpEntry> entries = new ArrayList<>();
        for (ThreadInfo info : threadInfos) {
            if (info == null) continue;
            entries.add(toEntry(info, deadlockedIds));
        }

        return ThreadDump.builder()
                .timestamp(Instant.now())
                .threadCount(entries.size())
                .deadlockedThreadCount(deadlockedIds.size())
                .threads(entries)
                .build();
    }

    private Set<Long> findDeadlockedIds(ThreadMXBean threadMXBean) {
        long[] ids = threadMXBean.findDeadlockedThreads();
        if (ids == null) return Set.of();

        Set<Long> deadlocked = new HashSet<>();
        for (long id : ids) deadlocked.add(id);
        return deadlocked;
    }

    private ThreadDumpEntry toEntry(ThreadInfo info, Set<Long> deadlockedIds) {
        List<String> stackTrace = Arrays.stream(info.getStackTrace())
                .limit(maxStackDepth)
                .map(StackTraceElement::toString)
                .toList();

        return ThreadDumpEntry.builder()
                .id(info.getThreadId())
                .name(info.getThreadName())
                .state(info.getThreadState().name())
                .daemon(info.isDaemon())
                .priority(info.getPriority())
                .lockName(info.getLockName())
                .lockOwnerName(info.getLockOwnerName())
                .lockOwnerId(info.getLockOwnerId())
                .inNative(info.isInNative())
                .suspended(info.isSuspended())
                .deadlocked(deadlockedIds.contains(info.getThreadId()))
                .stackTrace(stackTrace)
                .build();
    }
}
