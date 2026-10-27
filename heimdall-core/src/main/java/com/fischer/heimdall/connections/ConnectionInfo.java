package com.fischer.heimdall.connections;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class ConnectionInfo {
    private String name;
    private String type;
    private String technology;
    private String status;
    private String host;
    private Map<String, Object> stats;
    private Instant checkedAt;
}