package com.fischer.heimdall.tracing;

/**
 * Static entry point for application code to attach custom attributes to the span
 * currently being traced, e.g. {@code Heimdall.tag("orderId", id)}.
 *
 * No-op if called outside of a traced method (no active span) — safe to call unconditionally.
 */
public final class Heimdall {

    private static volatile TraceContext traceContext;

    private Heimdall() {}

    static void register(TraceContext context) {
        traceContext = context;
    }

    public static void tag(String key, Object value) {
        if (traceContext == null || key == null || key.isBlank()) return;

        HeimdallSpan span = traceContext.currentSpan();
        if (span == null) return;

        span.getTags().put(key, value == null ? "null" : String.valueOf(value));
    }
}
