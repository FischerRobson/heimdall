package com.fischer.heimdall.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit test — no Vert.x context is active on the JUnit thread, so this exercises
 * TraceContext's ThreadLocal fallback.
 */
class TraceContextTest {

    private final TraceContext context = new TraceContext();

    @AfterEach
    void cleanup() {
        context.endTrace();
    }

    @Test
    void nestedSpansGetParentIds() {
        context.startTrace(HeimdallTrace.builder().id("t1").build());

        HeimdallSpan outer = span("outer");
        HeimdallSpan inner = span("inner");
        context.startSpan(outer);
        context.startSpan(inner);

        assertSame(inner, context.currentSpan());
        assertEquals("outer", inner.getParentId());
        assertNull(outer.getParentId());

        context.endSpan("inner", 5, null);
        assertSame(outer, context.currentSpan());
        context.endSpan("outer", 10, "err");

        HeimdallTrace trace = context.endTrace();
        assertEquals(2, trace.getSpans().size());
        assertEquals(5, inner.getDuration());
        assertEquals("err", outer.getError());
        assertFalse(context.hasActiveTrace());
    }

    @Test
    void spansAreIgnoredWithoutActiveTrace() {
        context.startSpan(span("orphan"));
        assertNull(context.currentSpan());
        assertNull(context.endTrace());
    }

    @Test
    void heimdallTagWritesToCurrentSpan() {
        context.init(); // registers this context with the static Heimdall API
        context.startTrace(HeimdallTrace.builder().id("t2").build());
        HeimdallSpan s = span("s");
        context.startSpan(s);

        Heimdall.tag("orderId", 42);
        Heimdall.tag("", "ignored");
        Heimdall.tag("nullValue", null);

        assertEquals("42", s.getTags().get("orderId"));
        assertEquals("null", s.getTags().get("nullValue"));
        assertFalse(s.getTags().containsKey(""));
    }

    private HeimdallSpan span(String id) {
        return HeimdallSpan.builder().id(id).className("C").methodName("m").build();
    }
}
