package com.fischer.heimdall.metrics;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CpuInfo {
    private double systemCpuLoad;
    private double processCpuLoad;
    private int availableProcessors;
    private double totalMemorySize;
}
