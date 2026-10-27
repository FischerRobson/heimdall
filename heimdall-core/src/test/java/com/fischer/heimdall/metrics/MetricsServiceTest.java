package com.fischer.heimdall.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MetricsServiceTest {

    private final MetricsService service = new MetricsService();
    private Locale originalLocale;

    @BeforeEach
    void saveLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void memoryInfoWorksWithCommaDecimalLocale() {
        // regression: String.format("%.1f") produced "45,3" under pt_BR and
        // Double.parseDouble threw NumberFormatException
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));

        MemoryInfo info = assertDoesNotThrow(service::getMemoryInfo);

        assertTrue(info.getPercentage() >= 0 && info.getPercentage() <= 100,
                "percentage out of range: " + info.getPercentage());
        assertTrue(info.getMaxAllowed() > 0);
    }

    @Test
    void fullSnapshotHasAllSections() {
        Metrics metrics = service.getMetrics();

        assertNotNull(metrics.getMemoryInfo());
        assertNotNull(metrics.getCpuInfo());
        assertNotNull(metrics.getJvmInfo());
        assertFalse(metrics.getGarbageCollectorInfo().getGarbageCollectorList().isEmpty());
        assertTrue(metrics.getThreadInfo().getThreadCount() > 0);
        assertTrue(metrics.getJvmInfo().getPid() > 0);
    }

    @Test
    void gcResultIsConsistent() {
        GcResult result = service.triggerGarbageCollection();

        assertNotNull(result.getBefore());
        assertNotNull(result.getAfter());
        assertTrue(result.getDurationMs() >= 0);
    }
}
