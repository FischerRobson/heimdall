package com.fischer.heimdall.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GcResult {
    private MemoryInfo before;
    private MemoryInfo after;
    private double freedMb;
    private long durationMs;
}
