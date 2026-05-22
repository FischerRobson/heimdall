package tracing;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayDeque;
import java.util.Deque;

@ApplicationScoped
public class TraceContext {

    private static final ThreadLocal<HeimdallTrace> currentTrace = new ThreadLocal<>();
    private static final ThreadLocal<Deque<HeimdallSpan>> spanStack = new ThreadLocal<>();

    public void startTrace(HeimdallTrace trace) {
        currentTrace.set(trace);
        spanStack.set(new ArrayDeque<>());
    }

    public void startSpan(HeimdallSpan span) {
        Deque<HeimdallSpan> stack = spanStack.get();
        if (stack == null) return;

        HeimdallSpan parent = stack.peek();
        if (parent != null) {
            span.setParentId(parent.getId());
        }

        stack.push(span);
        currentTrace.get().addSpan(span);
    }

    public void endSpan(String spanId, long duration, String error) {
        HeimdallTrace trace = currentTrace.get();
        if (trace == null) return;

        trace.getSpans().stream()
                .filter(s -> s.getId().equals(spanId))
                .findFirst()
                .ifPresent(s -> {
                    s.setDuration(duration);
                    s.setError(error);
                });

        Deque<HeimdallSpan> stack = spanStack.get();
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
        }
    }

    public HeimdallTrace endTrace() {
        HeimdallTrace trace = currentTrace.get();
        currentTrace.remove();
        spanStack.remove();
        return trace;
    }

    public HeimdallTrace current() {
        return currentTrace.get();
    }

    public boolean hasActiveTrace() {
        return currentTrace.get() != null;
    }
}