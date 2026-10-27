package com.fischer.heimdall.threads;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Keeps the last N captured thread dumps in memory, so they can be compared
 * (e.g. before/after a slowdown) and re-opened after leaving the page.
 */
@ApplicationScoped
public class ThreadDumpHistory {

    @ConfigProperty(name = "heimdall.threaddump.history-limit", defaultValue = "10")
    int limit;

    // newest first
    private final Deque<ThreadDump> dumps = new ArrayDeque<>();

    public synchronized void add(ThreadDump dump) {
        while (dumps.size() >= Math.max(limit, 1)) {
            dumps.removeLast();
        }
        dumps.addFirst(dump);
    }

    public synchronized List<ThreadDumpSummary> summaries() {
        return dumps.stream().map(ThreadDumpHistory::summarize).toList();
    }

    public synchronized Optional<ThreadDump> get(String id) {
        return dumps.stream().filter(d -> d.getId().equals(id)).findFirst();
    }

    public synchronized void clear() {
        dumps.clear();
    }

    static ThreadDumpSummary summarize(ThreadDump dump) {
        Map<String, Integer> stateCounts = new TreeMap<>();
        for (ThreadDumpEntry entry : dump.getThreads()) {
            stateCounts.merge(entry.getState(), 1, Integer::sum);
        }

        return ThreadDumpSummary.builder()
                .id(dump.getId())
                .timestamp(dump.getTimestamp())
                .threadCount(dump.getThreadCount())
                .deadlockedThreadCount(dump.getDeadlockedThreadCount())
                .stateCounts(stateCounts)
                .build();
    }
}
