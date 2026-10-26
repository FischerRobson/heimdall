package tracing;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class HeimdallTrace {
    private String id;
    private String httpMethod;
    private String path;
    private int status;
    private long startTime;
    private long duration;
    private Instant timestamp;
    private Map<String, String> requestHeaders;
    private Map<String, String> queryParams;
    private Map<String, String> cookies;
    private String requestBody;
    private Map<String, String> responseHeaders;
    private String responseBody;
    @Builder.Default
    private List<HeimdallSpan> spans = new ArrayList<>();

    public void addSpan(HeimdallSpan span) {
        if (spans == null) spans = new ArrayList<>();
        spans.add(span);
    }
}