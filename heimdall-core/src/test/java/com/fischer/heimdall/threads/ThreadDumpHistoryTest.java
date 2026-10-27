package com.fischer.heimdall.threads;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThreadDumpHistoryTest {

    @Test
    void keepsNewestDumpsUpToLimit() {
        ThreadDumpHistory history = new ThreadDumpHistory();
        history.limit = 2;

        history.add(dump("a"));
        history.add(dump("b"));
        history.add(dump("c"));

        List<String> ids = history.summaries().stream().map(ThreadDumpSummary::getId).toList();
        assertEquals(List.of("c", "b"), ids);
        assertTrue(history.get("a").isEmpty(), "oldest dump should have been evicted");
    }

    @Test
    void summaryCountsThreadsPerState() {
        ThreadDump dump = ThreadDump.builder()
                .id("x")
                .timestamp(Instant.now())
                .threadCount(3)
                .threads(List.of(entry(1, "RUNNABLE"), entry(2, "RUNNABLE"), entry(3, "BLOCKED")))
                .build();

        ThreadDumpSummary summary = ThreadDumpHistory.summarize(dump);

        assertEquals(2, summary.getStateCounts().get("RUNNABLE"));
        assertEquals(1, summary.getStateCounts().get("BLOCKED"));
    }

    private ThreadDump dump(String id) {
        return ThreadDump.builder().id(id).timestamp(Instant.now()).threads(List.of()).build();
    }

    private ThreadDumpEntry entry(long id, String state) {
        return ThreadDumpEntry.builder().id(id).name("t" + id).state(state).stackTrace(List.of()).build();
    }
}
