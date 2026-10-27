package com.fischer.heimdall.threads;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/** Lightweight view of a stored dump, without stack traces — for the history list. */
@Getter
@Builder
public class ThreadDumpSummary {
    private String id;
    private Instant timestamp;
    private int threadCount;
    private int deadlockedThreadCount;
    private Map<String, Integer> stateCounts;
}
