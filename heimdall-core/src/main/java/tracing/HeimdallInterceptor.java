package tracing;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.UUID;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@RequiredArgsConstructor
@HeimdallMonitor
public class HeimdallInterceptor {

    private final TraceContext traceContext;

    @ConfigProperty(name = "heimdall.tracing.packages")
    Optional<String> tracingPackages;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        System.out.println("[Heimdall] intercept called on: "
                + ctx.getTarget().getClass().getSuperclass().getName()
                + "." + ctx.getMethod().getName());

        if (!traceContext.hasActiveTrace()) {
            System.out.println("[Heimdall] no active trace, skipping");
            return ctx.proceed();
        }
        if (!shouldTrace(ctx)) {
            System.out.println("[Heimdall] shouldTrace=false, skipping");
            return ctx.proceed();
        }
        if (!traceContext.hasActiveTrace()) return ctx.proceed();
        if (!shouldTrace(ctx))             return ctx.proceed();

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

        traceContext.startSpan(span);

        long start = System.currentTimeMillis();
        try {
            Object result = ctx.proceed();
            traceContext.endSpan(spanId, System.currentTimeMillis() - start, null);
            return result;
        } catch (Exception e) {
            traceContext.endSpan(spanId, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    private boolean shouldTrace(InvocationContext ctx) {
        String className  = ctx.getTarget().getClass().getSuperclass().getName();
        String simpleName = ctx.getTarget().getClass().getSuperclass().getSimpleName();

        System.out.println("[HEIMDALL]: " + className);
        System.out.println("[HEIMDALL]: " + simpleName);


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