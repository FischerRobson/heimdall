package threads;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ThreadDumpEntry {
    private long id;
    private String name;
    private String state;
    private boolean daemon;
    private int priority;
    private String lockName;
    private String lockOwnerName;
    private long lockOwnerId;
    private boolean inNative;
    private boolean suspended;
    private boolean deadlocked;
    private List<String> stackTrace;
}
