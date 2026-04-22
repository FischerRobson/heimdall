package metrics;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemoryInfo {
    private double initialMemory;
    private double inUsage;
    private double committed;
    private double maxAllowed;
    private double percentage;
}
