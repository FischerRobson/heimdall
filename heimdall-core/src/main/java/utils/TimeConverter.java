package utils;

public class TimeConverter {

    private TimeConverter() {}

    public static String toTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours   = minutes / 60;
        long days    = hours / 24;

        return String.format("%dd %dh %dm %ds",
                days,
                hours % 24,
                minutes % 60,
                seconds % 60
        );
    }

}
