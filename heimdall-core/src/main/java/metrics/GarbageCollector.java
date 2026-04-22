package metrics;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GarbageCollector {
    private String name;
    private long collectionCount;
    private long collectionTime;
}
