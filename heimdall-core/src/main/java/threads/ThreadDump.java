package threads;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
public class ThreadDump {
    private String id;
    private Instant timestamp;
    private int threadCount;
    private int deadlockedThreadCount;
    private List<ThreadDumpEntry> threads;
}
