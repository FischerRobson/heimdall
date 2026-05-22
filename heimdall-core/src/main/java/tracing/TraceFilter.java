package tracing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Provider
@ApplicationScoped
@RequiredArgsConstructor
public class TraceFilter {

    private final TraceContext traceContext;

    private final TraceRegistry traceRegistry;

    @ServerRequestFilter
    public void requestFilter(ContainerRequestContext ctx) {
        Map<String, String> headers  = extractHeaders(ctx);
        Map<String, String> params   = extractParams(ctx);
        Map<String, String> cookies  = extractCookies(ctx);

        HeimdallTrace trace = HeimdallTrace.builder()
                .id(UUID.randomUUID().toString())
                .httpMethod(ctx.getMethod())
                .path(ctx.getUriInfo().getPath())
                .startTime(System.currentTimeMillis())
                .timestamp(Instant.now())
                .requestHeaders(headers)
                .queryParams(params)
                .cookies(cookies)
                .build();

        traceContext.startTrace(trace);
    }

    @ServerResponseFilter
    public void responseFilter(ContainerRequestContext reqCtx, ContainerResponseContext resCtx) {
        if (!traceContext.hasActiveTrace()) return;

        HeimdallTrace trace = traceContext.endTrace();
        if (trace == null) return;

        long duration = System.currentTimeMillis() - trace.getStartTime();
        trace.setDuration(duration);
        trace.setStatus(resCtx.getStatus());
        trace.setResponseHeaders(extractResponseHeaders(resCtx));

        // skip heimdall's own endpoints
        if (trace.getPath().startsWith("/heimdall") || trace.getPath().startsWith("/q/")) return;

        traceRegistry.add(trace);
    }

    private Map<String, String> extractHeaders(ContainerRequestContext ctx) {
        Map<String, String> headers = new HashMap<>();
        ctx.getHeaders().forEach((k, v) -> {
            if (!k.equalsIgnoreCase("authorization")) {
                headers.put(k.toLowerCase(), String.join(", ", v));
            } else {
                headers.put(k.toLowerCase(), maskToken(String.join(", ", v)));
            }
        });
        return headers;
    }

    private Map<String, String> extractParams(ContainerRequestContext ctx) {
        Map<String, String> params = new HashMap<>();
        ctx.getUriInfo().getQueryParameters()
                .forEach((k, v) -> params.put(k, String.join(", ", v)));
        return params;
    }

    private Map<String, String> extractCookies(ContainerRequestContext ctx) {
        Map<String, String> cookies = new HashMap<>();
        ctx.getCookies().forEach((k, v) -> cookies.put(k, maskCookie(k, v.getValue())));
        return cookies;
    }

    private Map<String, String> extractResponseHeaders(ContainerResponseContext ctx) {
        Map<String, String> headers = new HashMap<>();
        ctx.getHeaders().forEach((k, v) -> {
            String value = v.stream()
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "));
            headers.put(k.toLowerCase(), value);
        });
        return headers;
    }

    private String maskToken(String value) {
        if (value == null || value.length() < 20) return "***";
        return value.substring(0, 16) + "...";
    }

    private String maskCookie(String name, String value) {
        if (name.equalsIgnoreCase("session") || name.equalsIgnoreCase("token")) {
            return value.substring(0, Math.min(8, value.length())) + "...";
        }
        return value;
    }
}