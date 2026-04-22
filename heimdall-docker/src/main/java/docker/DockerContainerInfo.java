package docker;

import lombok.Builder;
import lombok.Getter;
import java.util.Map;

@Getter
@Builder
public class DockerContainerInfo {
    private String id;
    private String shortId;
    private String name;
    private String image;
    private String status;
    private String state;
    private long createdAt;
    private String[] ports;
    private Map<String, String> labels;
    private DockerStats stats;
}