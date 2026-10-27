package com.fischer.heimdall.docker;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DockerStats {
    private double cpuPercent;
    private long memoryUsed;
    private long memoryLimit;
    private double memoryPercent;
    private long networkIn;
    private long networkOut;
    private long blockRead;
    private long blockWrite;
}