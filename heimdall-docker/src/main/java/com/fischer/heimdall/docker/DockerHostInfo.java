package com.fischer.heimdall.docker;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DockerHostInfo {
    private String dockerVersion;
    private String operatingSystem;
    private int totalContainers;
    private int runningContainers;
    private int stoppedContainers;
    private int totalImages;
    private long totalMemory;
    private int cpus;
}