package com.fischer.heimdall.beans;

import java.util.List;

/**
 * JSON shape of one bean's stats: totals over its methods plus the per-method rows.
 * {@code id} is the fully qualified class name, the same id the bean graph uses.
 */
public record BeanStatsView(
        String id,
        String name,
        long calls,
        long errors,
        double avgMs,
        double maxMs,
        MethodStats.Health health,
        List<MethodStatsView> methods
) {}
