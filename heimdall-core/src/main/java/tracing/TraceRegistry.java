package tracing;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@ApplicationScoped
public class TraceRegistry {

    @ConfigProperty(name = "heimdall.traces.limit", defaultValue = "100")
    int limit;

    private final LinkedList<HeimdallTrace> traces = new LinkedList<>();

    public synchronized void add(HeimdallTrace trace) {
        if (trace == null) return;
        if (traces.size() >= limit) {
            traces.removeLast();
        }
        traces.addFirst(trace);
    }

    public synchronized List<HeimdallTrace> getAll() {
        return new ArrayList<>(traces);
    }

    public synchronized List<HeimdallTrace> getByMethod(String method) {
        return traces.stream()
                .filter(t -> method.equalsIgnoreCase(t.getHttpMethod()))
                .toList();
    }

    public synchronized List<HeimdallTrace> getErrors() {
        return traces.stream()
                .filter(t -> t.getStatus() >= 400)
                .toList();
    }

    public synchronized void clear() {
        traces.clear();
    }
}