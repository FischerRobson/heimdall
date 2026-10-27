package com.fischer.heimdall.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ByteConverterTest {

    @Test
    void convertsAndRoundsToWholeUnits() {
        assertEquals(2, ByteConverter.toKilobytes(2048));
        assertEquals(2, ByteConverter.toKilobytes(2000));      // 1.95 -> 2
        assertEquals(512, ByteConverter.toMegabytes(512L * 1024 * 1024));
        assertEquals(3, ByteConverter.toGigabytes(3L * 1024 * 1024 * 1024));
        assertEquals(0, ByteConverter.toGigabytes(100));
    }
}
