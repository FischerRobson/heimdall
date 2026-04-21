package entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Metrics {

    private Map<String, Double> memoryInfo;
    private List<Map<String, Object>> garbageCollectorInfo;
    private Map<String, Integer> threadInfo;
    private Map<String, Double> cpuInfo;
}
