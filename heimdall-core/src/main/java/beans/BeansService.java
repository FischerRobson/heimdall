package beans;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import tracing.HeimdallMonitor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

@ApplicationScoped
@HeimdallMonitor
@RequiredArgsConstructor
public class BeansService {

    private final BeanManager beanManager;

    public BeanGraph getGraph() {
        Set<Bean<?>> allBeans = beanManager.getBeans(Object.class, new AnnotationLiteral<Any>() {});

        Map<String, BeanNode> nodeMap = new LinkedHashMap<>();
        Set<BeanEdge> edges = new HashSet<>();

        for (Bean<?> bean : allBeans) {
            if (isNoise(bean)) continue;

            String fullClass = bean.getBeanClass().getName();

            if (!nodeMap.containsKey(fullClass)) {
                nodeMap.put(fullClass, new BeanNode(
                        bean.getBeanClass().getSimpleName(),
                        fullClass,
                        bean.getScope().getSimpleName(),
                        bean.getTypes().stream()
                                .map(t -> t.getTypeName())
                                .filter(t -> !t.equals("java.lang.Object"))
                                .toList()
                ));
            }

            for (Class<?> injectedType : resolveInjectedTypes(bean)) {
                allBeans.stream()
                        .filter(b -> !isNoise(b))
                        .filter(b -> b.getTypes().stream()
                                .anyMatch(t -> t.getTypeName().equals(injectedType.getName())))
                        .findFirst()
                        .ifPresent(target -> edges.add(new BeanEdge(
                                bean.getBeanClass().getSimpleName(),
                                target.getBeanClass().getSimpleName()
                        )));
            }
        }

        return new BeanGraph(new ArrayList<>(nodeMap.values()), edges);
    }

    private Set<Class<?>> resolveInjectedTypes(Bean<?> bean) {
        Set<Class<?>> injected = new HashSet<>();

        // strategy 1 — CDI injection points
        for (InjectionPoint ip : bean.getInjectionPoints()) {
            if (ip.getType() instanceof Class<?> c) {
                injected.add(c);
            }
        }

        if (!injected.isEmpty()) return injected;

        Class<?> beanClass = bean.getBeanClass();

        // strategy 2 — @Inject fields
        for (Field field : beanClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                injected.add(field.getType());
            }
        }

        if (!injected.isEmpty()) return injected;

        // strategy 3 — Lombok @RequiredArgsConstructor final fields
        List<Field> finalFields = Arrays.stream(beanClass.getDeclaredFields())
                .filter(f -> java.lang.reflect.Modifier.isFinal(f.getModifiers()))
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .toList();

        if (!finalFields.isEmpty()) {
            Class<?>[] finalTypes = finalFields.stream()
                    .map(Field::getType)
                    .toArray(Class[]::new);

            boolean hasMatchingConstructor = Arrays.stream(beanClass.getDeclaredConstructors())
                    .anyMatch(c -> Arrays.equals(c.getParameterTypes(), finalTypes));

            if (hasMatchingConstructor) {
                finalFields.forEach(f -> injected.add(f.getType()));
            }
        }

        // strategy 4 — @Inject constructor
        for (Constructor<?> constructor : beanClass.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                for (Class<?> param : constructor.getParameterTypes()) {
                    injected.add(param);
                }
            }
        }

        return injected;
    }

    private boolean isNoise(Bean<?> bean) {
        String name = bean.getBeanClass().getName();
        return name.startsWith("io.quarkus")
                || name.startsWith("jakarta")
                || name.startsWith("org.jboss")
                || name.startsWith("io.smallrye")
                || name.startsWith("io.vertx")
                || name.startsWith("com.sun")
                || name.startsWith("org.apache.camel")
                || name.startsWith("io.netty")
                || name.startsWith("java.");
    }
}