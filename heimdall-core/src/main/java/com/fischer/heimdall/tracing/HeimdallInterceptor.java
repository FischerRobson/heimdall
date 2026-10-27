package com.fischer.heimdall.tracing;

import com.fischer.heimdall.beans.MethodStatsRegistry;
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
    private final MethodStatsRegistry statsRegistry;

    @ConfigProperty(name = "heimdall.tracing.packages")
    Optional<String> tracingPackages;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        // the target is the generated subclass; its superclass is the bean class itself
        Class<?> beanClass = ctx.getTarget().getClass().getSuperclass();
        if (IS_INTERNAL.get(beanClass)) {
            return ctx.proceed();
        }

        // stats are collected on every call — scheduler and messaging calls have no
        // HTTP trace but still count. A span is only built when a trace is active.
        HeimdallSpan span = traceContext.hasActiveTrace() && shouldTrace(beanClass)
                ? startSpan(beanClass, ctx)
                : null;

        long start = System.nanoTime();
        Throwable failure = null;
        try {
            Object result = ctx.proceed();
            if (span != null && result != null) {
                span.getTags().put("returnType", result.getClass().getSimpleName());
            }
            return result;
        } catch (Throwable t) {
            // Throwable, not Exception — an Error (StackOverflowError, AssertionError...)
            // must also be recorded
            failure = t;
            throw t;
        } finally {
            long elapsed = System.nanoTime() - start;
            statsRegistry.record(beanClass.getName(), ctx.getMethod().getName(), elapsed, failure);
            if (span != null) {
                String error = failure == null ? null
                        : failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
                // always pop the span, otherwise every later span in this request
                // would be attached to this one as its parent
                traceContext.endSpan(span.getId(), elapsed / 1_000_000, error);
            }
        }
    }

    private HeimdallSpan startSpan(Class<?> beanClass, InvocationContext ctx) {
        String className = beanClass.getSimpleName();
        HeimdallSpan span = HeimdallSpan.builder()
                .id(UUID.randomUUID().toString())
                .className(className)
                .methodName(ctx.getMethod().getName())
                .layer(detectLayer(className))
                .startTime(System.currentTimeMillis())
                .build();

        tagArguments(span, ctx);
        traceContext.startSpan(span);
        return span;
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

    // framework internals and Heimdall itself — neither traced nor counted.
    // Cached per class: this runs on every intercepted call.
    private static final ClassValue<Boolean> IS_INTERNAL = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName();
            return name.startsWith("io.quarkus")
                    || name.startsWith("jakarta")
                    || name.startsWith("org.apache.camel")
                    || name.startsWith("io.netty")
                    || name.startsWith("org.jboss")
                    || name.startsWith("io.smallrye")
                    || name.startsWith("io.vertx")
                    || name.startsWith("com.fischer.heimdall.");
        }
    };

    private boolean shouldTrace(Class<?> beanClass) {
        String className  = beanClass.getName();
        String simpleName = beanClass.getSimpleName();

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