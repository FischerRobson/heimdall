package metrics;

import lombok.Getter;
import lombok.NoArgsConstructor;
import tracing.HeimdallMonitor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@HeimdallMonitor
public class GarbageCollectorInfo {

    private List<GarbageCollector> garbageCollectorList;

    public void addGarbageCollector(GarbageCollector garbageCollector) {
        if (this.garbageCollectorList == null) {
            this.garbageCollectorList = new ArrayList<>();
        }

        garbageCollectorList.add(garbageCollector);
    }

}
