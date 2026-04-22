package beans;


import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class BeanStats {
    private AtomicLong callCount;
    private AtomicLong errorCount;
    private AtomicLong totalTimeMs;
    private AtomicLong minTimeMs;
    private AtomicLong maxTimeMs;
    private volatile Instant lastCalledAt;
}
