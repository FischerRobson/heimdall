package com.fischer.heimdall.metrics;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ThreadInfo {
    private int threadCount;
    private int threadBlocked;
    private int threadWaiting;
    private int threadTimedWaiting;
    private int threadPeak;
}
