package tracing;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@RequiredArgsConstructor
@HeimdallMonitor
public class HeimdallInterceptor {

    private static final int MAX_TAG_VALUE_LENGTH = 200;

    // same list of sensitive field names TraceFilter redacts in headers/cookies/body —
    // argument names get checked against it before the raw value is ever put in a tag.
    private static final Pattern SENSITIVE_ARG_NAME = Pattern.compile(
            "(?i)^(password|secret|token|apikey|api_key|authorization|accesstoken|refreshtoken|credential)$");

    private final TraceContext traceContext;

    @ConfigProperty(name = "heimdall.tracing.packages")
    Optional<String> tracingPackages;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        if (!traceContext.hasActiveTrace() || !shouldTrace(ctx)) {
            return ctx.proceed();
        }

        String className  = ctx.getTarget().getClass().getSuperclass().getSimpleName();
        String methodName = ctx.getMethod().getName();
        String layer      = detectLayer(className);
        String spanId     = UUID.randomUUID().toString();

        HeimdallSpan span = HeimdallSpan.builder()
                .id(spanId)
                .className(className)
                .methodName(methodName)
                .layer(layer)
                .startTime(System.currentTimeMillis())
                .build();

        tagArguments(span, ctx);
        traceContext.startSpan(span);

        long start = System.currentTimeMillis();
        try {
            Object result = ctx.proceed();
            if (result != null) {
                span.getTags().put("returnType", result.getClass().getSimpleName());
            }
            traceContext.endSpan(spanId, System.currentTimeMillis() - start, null);
            return result;
        } catch (Exception e) {
            traceContext.endSpan(spanId, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    // automatic tags — captures actual values for simple/printable argument types
    // (String, numbers, booleans, enums) and just the class name for anything else,
    // so we never dump arbitrary/potentially sensitive object graphs into a trace.
    // App code can add richer, intentional context via Heimdall.tag(key, value).
    private void tagArguments(HeimdallSpan span, InvocationContext ctx) {
        Object[] values = ctx.getParameters();
        if (values == null || values.length == 0) return;

        Parameter[] params = ctx.getMethod().getParameters();
        for (int i = 0; i < values.length; i++) {
            String name = i < params.length ? params[i].getName() : "arg" + i;
            boolean sensitive = SENSITIVE_ARG_NAME.matcher(name).matches();
            span.getTags().put(name, sensitive ? "***" : describeArgument(values[i]));
        }
    }

    private String describeArgument(Object value) {
        if (value == null) return "null";
        // instanceof Enum covers constant-specific class bodies too (e.g. enum X { A { ... } }),
        // where getClass() returns the anonymous per-constant subclass, not the enum type itself.
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?>) {
            String s = String.valueOf(value);
            return s.length() > MAX_TAG_VALUE_LENGTH ? s.substring(0, MAX_TAG_VALUE_LENGTH) + "..." : s;
        }
        return value.getClass().getSimpleName();
    }

    private boolean shouldTrace(InvocationContext ctx) {
        String className  = ctx.getTarget().getClass().getSuperclass().getName();
        String simpleName = ctx.getTarget().getClass().getSuperclass().getSimpleName();

        // skip true framework internals only
        if (className.startsWith("io.quarkus")
                || className.startsWith("jakarta")
                || className.startsWith("org.apache.camel")
                || className.startsWith("io.netty")
                || className.startsWith("org.jboss")
                || className.startsWith("io.smallrye")
                || className.startsWith("io.vertx")
                || className.startsWith("tracing.")) return false;

        // strategy B — configured packages
        if (tracingPackages.isPresent()) {
            String[] packages = tracingPackages.get().split(",");
            for (String pkg : packages) {
                if (className.startsWith(pkg.trim())) return true;
            }
            return false;
        }

        // strategy C — class name suffix convention
        return simpleName.endsWith("Resource")
                || simpleName.endsWith("Controller")
                || simpleName.endsWith("Service")
                || simpleName.endsWith("Repository")
                || simpleName.endsWith("Dao");
    }

    private String detectLayer(String className) {
        if (className.endsWith("Resource") || className.endsWith("Controller")) return "resource";
        if (className.endsWith("Service"))                                       return "service";
        if (className.endsWith("Repository") || className.endsWith("Dao"))       return "repository";
        return "unknown";
    }
}