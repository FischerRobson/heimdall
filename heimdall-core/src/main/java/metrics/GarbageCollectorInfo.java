package metrics;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class GarbageCollectorInfo {

    private List<GarbageCollector> garbageCollectorList;

    public void addGarbageCollector(GarbageCollector garbageCollector) {
        if (this.garbageCollectorList == null) {
            this.garbageCollectorList = new ArrayList<>();
        }

        garbageCollectorList.add(garbageCollector);
    }

}
