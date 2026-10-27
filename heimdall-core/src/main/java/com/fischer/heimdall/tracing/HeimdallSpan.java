package com.fischer.heimdall.tracing;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
public class HeimdallSpan {
    private String id;
    private String parentId;
    private String className;
    private String methodName;
    private String layer;
    private long startTime;
    private long duration;
    private String error;
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();
}