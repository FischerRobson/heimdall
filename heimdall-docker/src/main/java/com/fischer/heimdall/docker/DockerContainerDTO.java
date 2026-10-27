package com.fischer.heimdall.docker;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DockerContainerDTO {
    private String shortId;
    private String name;
    private String image;
    private String status;
    private String state;
    private String role;
    private String cluster;
    private DockerStats stats;
}