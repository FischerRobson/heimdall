package metrics;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JvmInfo {
    private String uptime;
    private long pid;
    private String jvmName;
    private String javaVersion;
}
