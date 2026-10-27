package tracing;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sample.SampleService;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class HeimdallInterceptorTest {

    @Inject
    TraceContext traceContext;

    @Inject
    SampleService sampleService;

    @AfterEach
    void cleanup() {
        traceContext.endTrace();
    }

    @Test
    void recordsSpanWithTagsAndMasksSensitiveArguments() {
        traceContext.startTrace(HeimdallTrace.builder().id("t").build());

        sampleService.greet("ana", "hunter2");

        HeimdallTrace trace = traceContext.endTrace();
        assertEquals(1, trace.getSpans().size());

        HeimdallSpan span = trace.getSpans().get(0);
        assertEquals("SampleService", span.getClassName());
        assertEquals("greet", span.getMethodName());
        assertEquals("service", span.getLayer());
        assertEquals("ana", span.getTags().get("name"));
        assertEquals("***", span.getTags().get("password"));
        assertEquals("String", span.getTags().get("returnType"));
        assertNull(span.getError());
    }

    @Test
    void doesNothingWithoutActiveTrace() {
        assertEquals("hello bob", sampleService.greet("bob", "x"));
        assertFalse(traceContext.hasActiveTrace());
    }

    @Test
    void exceptionMarksSpanAndPopsIt() {
        traceContext.startTrace(HeimdallTrace.builder().id("t").build());

        assertThrows(IllegalStateException.class, sampleService::fail);
        assertNull(traceContext.currentSpan(), "failed span must be popped from the stack");

        HeimdallSpan span = traceContext.endTrace().getSpans().get(0);
        assertEquals("boom", span.getError());
    }

    @Test
    void errorWithoutMessageStillPopsSpanAndIsMarked() {
        traceContext.startTrace(HeimdallTrace.builder().id("t").build());

        assertThrows(AssertionError.class, sampleService::failWithError);
        // the next span must be a root, not a child of the failed one
        sampleService.greet("x", "y");

        HeimdallTrace trace = traceContext.endTrace();
        assertEquals(2, trace.getSpans().size());
        assertEquals("AssertionError", trace.getSpans().get(0).getError());
        assertNull(trace.getSpans().get(1).getParentId());
    }
}
