package tracing;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the trace/span-stack for the request currently being processed.
 *
 * Backed by Vert.x's duplicated-context locals (getLocal/putLocal) rather than a plain
 * ThreadLocal, so the active trace survives thread hops in reactive code — e.g. a traced
 * method returning Uni/Multi that continues via .emitOn() on a different worker or the
 * event loop. Quarkus guarantees one duplicated Vert.x context per HTTP request, and Mutiny
 * pipelines built on top of it propagate that context across continuations automatically.
 *
 * Falls back to a plain ThreadLocal when no Vert.x context is active (e.g. tests, or code
 * running on a thread Vert.x never touched).
 */
@ApplicationScoped
public class TraceContext {

    private static final String TRACE_KEY = "heimdall.trace";
    private static final String STACK_KEY = "heimdall.spanStack";

    private static final ThreadLocal<HeimdallTrace> fallbackTrace = new ThreadLocal<>();
    private static final ThreadLocal<Deque<HeimdallSpan>> fallbackStack = new ThreadLocal<>();

    @PostConstruct
    void init() {
        Heimdall.register(this);
    }

    public void startTrace(HeimdallTrace trace) {
        Context ctx = Vertx.currentContext();
        if (ctx != null) {
            ctx.putLocal(TRACE_KEY, trace);
            ctx.putLocal(STACK_KEY, new ArrayDeque<HeimdallSpan>());
        } else {
            fallbackTrace.set(trace);
            fallbackStack.set(new ArrayDeque<>());
        }
    }

    public void startSpan(HeimdallSpan span) {
        Deque<HeimdallSpan> stack = stack();
        if (stack == null) return;

        HeimdallSpan parent = stack.peek();
        if (parent != null) {
            span.setParentId(parent.getId());
        }

        stack.push(span);

        HeimdallTrace trace = trace();
        if (trace != null) trace.addSpan(span);
    }

    public void endSpan(String spanId, long duration, String error) {
        HeimdallTrace trace = trace();
        if (trace == null) return;

        trace.getSpans().stream()
                .filter(s -> s.getId().equals(spanId))
                .findFirst()
                .ifPresent(s -> {
                    s.setDuration(duration);
                    s.setError(error);
                });

        Deque<HeimdallSpan> stack = stack();
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
        }
    }

    public HeimdallTrace endTrace() {
        HeimdallTrace trace = trace();

        Context ctx = Vertx.currentContext();
        if (ctx != null) {
            ctx.removeLocal(TRACE_KEY);
            ctx.removeLocal(STACK_KEY);
        } else {
            fallbackTrace.remove();
            fallbackStack.remove();
        }

        return trace;
    }

    public HeimdallTrace current() {
        return trace();
    }

    public HeimdallSpan currentSpan() {
        Deque<HeimdallSpan> stack = stack();
        return stack == null ? null : stack.peek();
    }

    public boolean hasActiveTrace() {
        return trace() != null;
    }

    private HeimdallTrace trace() {
        Context ctx = Vertx.currentContext();
        return ctx != null ? ctx.getLocal(TRACE_KEY) : fallbackTrace.get();
    }

    private Deque<HeimdallSpan> stack() {
        Context ctx = Vertx.currentContext();
        return ctx != null ? ctx.getLocal(STACK_KEY) : fallbackStack.get();
    }
}
