package com.fischer.heimdall.beans;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Live counters for one monitored method. Updated on every call from many threads,
 * so everything is atomic or volatile — no locks on the hot path.
 */
public class MethodStats {

    private static final int MAX_ERROR_LENGTH = 300;

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();
    private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNanos = new AtomicLong();
    private volatile long lastCalledAt;   // epoch millis, 0 = never
    private volatile String lastError;

    public void record(long durationNanos, Throwable failure) {
        calls.incrementAndGet();
        totalNanos.addAndGet(durationNanos);
        minNanos.accumulateAndGet(durationNanos, Math::min);
        maxNanos.accumulateAndGet(durationNanos, Math::max);
        lastCalledAt = System.currentTimeMillis();
        if (failure != null) {
            errors.incrementAndGet();
            lastError = describe(failure);
        }
    }

    /**
     * Point-in-time copy. The counters are read one by one, so under load the values
     * can be off by a call or two relative to each other — fine for a dashboard.
     */
    public MethodStatsView snapshot(String method, long slowThresholdMs) {
        long n = calls.get();
        long err = errors.get();
        double avgMs = n == 0 ? 0 : toMs(totalNanos.get()) / n;
        long min = minNanos.get();
        return new MethodStatsView(
                method,
                n,
                err,
                round(avgMs),
                n == 0 || min == Long.MAX_VALUE ? 0 : round(toMs(min)),
                round(toMs(maxNanos.get())),
                lastCalledAt,
                lastError,
                Health.of(err, avgMs, slowThresholdMs)
        );
    }

    private static String describe(Throwable t) {
        String msg = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
        return msg.length() > MAX_ERROR_LENGTH ? msg.substring(0, MAX_ERROR_LENGTH) + "..." : msg;
    }

    private static double toMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double round(double ms) {
        return Math.round(ms * 100) / 100.0;
    }

    /** Ordered from best to worst, so the worst of several can be picked with max(). */
    public enum Health {
        ok, slow, error;

        static Health of(long errors, double avgMs, long slowThresholdMs) {
            if (errors > 0) return error;
            if (avgMs > slowThresholdMs) return slow;
            return ok;
        }
    }
}
