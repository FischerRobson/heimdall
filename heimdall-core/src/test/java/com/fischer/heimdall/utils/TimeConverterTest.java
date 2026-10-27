package com.fischer.heimdall.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeConverterTest {

    @Test
    void formatsDaysHoursMinutesSeconds() {
        long ms = ((((2L * 24 + 3) * 60 + 4) * 60) + 5) * 1000 + 999;
        assertEquals("2d 3h 4m 5s", TimeConverter.toTime(ms));
    }

    @Test
    void zero() {
        assertEquals("0d 0h 0m 0s", TimeConverter.toTime(0));
    }
}
