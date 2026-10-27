package com.fischer.heimdall.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Metrics {

    private MemoryInfo memoryInfo;
    private GarbageCollectorInfo garbageCollectorInfo;
    private ThreadInfo threadInfo;
    private CpuInfo cpuInfo;
    private JvmInfo jvmInfo;
}
