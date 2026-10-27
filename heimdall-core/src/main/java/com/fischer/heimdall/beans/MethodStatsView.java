package com.fischer.heimdall.beans;

/** JSON shape of one method's stats. Times are in milliseconds. */
public record MethodStatsView(
        String method,
        long calls,
        long errors,
        double avgMs,
        double minMs,
        double maxMs,
        long lastCalledAt,
        String lastError,
        MethodStats.Health health
) {}
