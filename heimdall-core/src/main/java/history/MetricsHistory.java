package history;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
        return metricsSnapshotLinkedList;
    }

}
