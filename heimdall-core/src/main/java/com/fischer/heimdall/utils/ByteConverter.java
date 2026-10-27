package com.fischer.heimdall.utils;

public class ByteConverter {

    private ByteConverter() { }

    public static double toKilobytes(long bytes) {
        return Math.round((double) bytes / 1024);
    }

    public static double toMegabytes(long bytes) {
        return Math.round((double) bytes / 1024 / 1024);
    }

    public static double toGigabytes(long bytes) {
        return Math.round((double) bytes / 1024 / 1024 / 1024);
    }


}
