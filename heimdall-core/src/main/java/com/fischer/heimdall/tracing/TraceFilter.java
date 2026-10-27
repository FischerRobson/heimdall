package com.fischer.heimdall.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;
import org.jboss.resteasy.reactive.server.SimpleResourceInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Provider
@ApplicationScoped
@RequiredArgsConstructor
public class TraceFilter {

    private static final int MAX_BODY_LENGTH = 4000;

    // hard cap on how many bytes/elements we ever buffer for a trace preview, independent
    // of the real payload size — this filter runs on every request the host app serves,
    // not just Heimdall's own, so a large upload/report response must never be fully
    // buffered/re-serialized here just to keep a 4000-char preview.
    private static final int MAX_CAPTURE_BYTES = 16_384;
    private static final int MAX_CAPTURE_ELEMENTS = 500;

    // same idea as maskToken()/maskCookie() below, applied to JSON body text — redact by
    // key name rather than fully parsing the body, so it works regardless of shape.
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(\"(?:password|secret|token|apikey|api_key|authorization|accessToken|refreshToken)\"\\s*:\\s*)\"[^\"]*\"");

    private final TraceContext traceContext;

    private final TraceRegistry traceRegistry;

    private final ObjectMapper objectMapper;

    private final CurrentVertxRequest currentVertxRequest;

    // Heimdall's own REST resources (core and optional modules like heimdall-docker) all
    // live under this package. Matched by resource class rather than by path so it keeps
    // working whatever prefix the routes end up under — UriInfo.getPath() is relative to
    // the REST root path, and a host app could have its own routes starting with "/heimdall".
    private static final String HEIMDALL_PACKAGE = "com.fischer.heimdall.";

    @ServerRequestFilter
    public void requestFilter(ContainerRequestContext ctx, SimpleResourceInfo resourceInfo) {
        // don't trace the dashboard's own polling — it would flood the registry
        if (isHeimdallResource(resourceInfo)) return;

        Map<String, String> headers  = extractHeaders(ctx);
        Map<String, String> params   = extractParams(ctx);
        Map<String, String> cookies  = extractCookies(ctx);
        String requestBody           = extractRequestBody(ctx);

        HeimdallTrace trace = HeimdallTrace.builder()
                .id(UUID.randomUUID().toString())
                .httpMethod(ctx.getMethod())
                .path(ctx.getUriInfo().getPath())
                .startTime(System.currentTimeMillis())
                .timestamp(Instant.now())
                .requestHeaders(headers)
                .queryParams(params)
                .cookies(cookies)
                .requestBody(requestBody)
                .build();

        traceContext.startTrace(trace);

        // Register the trace when the HTTP response actually ends, not in the JAX-RS
        // response filter: an unmapped exception is answered by Quarkus' error handler,
        // which never runs response filters — those traces (the 500s, the ones that
        // matter most) used to be silently dropped.
        RoutingContext routingContext = currentVertxRequest.getCurrent();
        if (routingContext != null) {
            routingContext.addEndHandler(ignored -> complete(trace, routingContext));
        }
    }

    /**
     * Enriches the trace with what only JAX-RS knows (the response entity). Not called
     * when the request fails with an unmapped exception — {@link #complete} covers that.
     */
    @ServerResponseFilter
    public void responseFilter(ContainerRequestContext reqCtx, ContainerResponseContext resCtx) {
        if (!traceContext.hasActiveTrace()) return;

        HeimdallTrace trace = traceContext.endTrace();
        if (trace == null) return;

        trace.setStatus(resCtx.getStatus());
        trace.setResponseHeaders(extractResponseHeaders(resCtx));
        trace.setResponseBody(extractResponseBody(resCtx));
    }

    private void complete(HeimdallTrace trace, RoutingContext routingContext) {
        trace.setDuration(System.currentTimeMillis() - trace.getStartTime());

        // the response filter didn't run (or the error handler rewrote the response):
        // take status and headers from what was really sent
        HttpServerResponse response = routingContext.response();
        trace.setStatus(response.getStatusCode());
        if (trace.getResponseHeaders() == null) {
            Map<String, String> headers = new HashMap<>();
            response.headers().forEach(h -> headers.merge(h.getKey().toLowerCase(), h.getValue(), (a, b) -> a + ", " + b));
            trace.setResponseHeaders(headers);
        }

        traceRegistry.add(trace);
    }

    private boolean isHeimdallResource(SimpleResourceInfo resourceInfo) {
        if (resourceInfo == null || resourceInfo.getResourceClass() == null) return false;
        return resourceInfo.getResourceClass().getName().startsWith(HEIMDALL_PACKAGE);
    }

    private String extractRequestBody(ContainerRequestContext ctx) {
        if (!ctx.hasEntity() || !isTextual(ctx.getMediaType())) return null;

        try {
            InputStream in = ctx.getEntityStream();
            // only ever buffer the first MAX_CAPTURE_BYTES ourselves — stitch it back
            // together with whatever's left of the original stream so the real resource
            // method still sees the complete body, no matter how large it actually is.
            byte[] head = in.readNBytes(MAX_CAPTURE_BYTES);
            ctx.setEntityStream(new SequenceInputStream(new ByteArrayInputStream(head), in));

            if (head.length == 0) return null;
            return redactBody(truncate(new String(head, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return null;
        }
    }

    private String extractResponseBody(ContainerResponseContext ctx) {
        Object entity = ctx.getEntity();
        if (entity == null || entity instanceof InputStream || entity instanceof byte[]) return null;

        MediaType mediaType = ctx.getMediaType();
        if (!isTextual(mediaType) || isTooLargeToCapture(entity)) return null;

        try {
            String body = mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                    ? objectMapper.writeValueAsString(entity)
                    : String.valueOf(entity);
            return redactBody(truncate(body));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTooLargeToCapture(Object entity) {
        if (entity instanceof Collection<?> c) return c.size() > MAX_CAPTURE_ELEMENTS;
        if (entity instanceof Map<?, ?> m) return m.size() > MAX_CAPTURE_ELEMENTS;
        if (entity.getClass().isArray()) return Array.getLength(entity) > MAX_CAPTURE_ELEMENTS;
        return false;
    }

    private boolean isTextual(MediaType mediaType) {
        if (mediaType == null) return false;
        return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                || mediaType.isCompatible(MediaType.TEXT_PLAIN_TYPE)
                || mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE)
                || mediaType.isCompatible(MediaType.TEXT_XML_TYPE)
                || mediaType.isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    }

    private String truncate(String body) {
        if (body == null || body.length() <= MAX_BODY_LENGTH) return body;
        return body.substring(0, MAX_BODY_LENGTH) + "... (truncated)";
    }

    private String redactBody(String body) {
        if (body == null) return null;
        return SENSITIVE_FIELD.matcher(body).replaceAll("$1\"***\"");
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