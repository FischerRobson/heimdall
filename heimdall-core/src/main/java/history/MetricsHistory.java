package history;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@ApplicationScoped
public class MetricsHistory {

    @ConfigProperty(name = "heimdall.history.limit", defaultValue = "60")
    private int limit;

    private final List<MetricsSnapshot> metricsSnapshotLinkedList;

    public MetricsHistory() {
        this.metricsSnapshotLinkedList = Collections.synchronizedList(new LinkedList<>());
    }

    public void add(MetricsSnapshot metricsSnapshot) {
        if (metricsSnapshotLinkedList.size() >= limit) {
            metricsSnapshotLinkedList.removeFirst();
        }
        metricsSnapshotLinkedList.add(metricsSnapshot);
    }

    public List<MetricsSnapshot> getAll() {
        // Collections.synchronizedList only makes individual calls atomic — iterating it
        // (which is exactly what happens when this gets serialized) still needs external
        // synchronization to be safe against a concurrent add()/removeFirst() from the
        // scheduler thread. Return a snapshot copy instead of the live list.
        synchronized (metricsSnapshotLinkedList) {
            return new ArrayList<>(metricsSnapshotLinkedList);
        }
    }

}
