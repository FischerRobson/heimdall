package com.example.demo.orders;

import com.fischer.heimdall.tracing.HeimdallMonitor;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory "database" with a little artificial latency, so spans have something to show. */
@ApplicationScoped
@HeimdallMonitor
public class OrderRepository {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public OrderRepository() {
        save("ana", 120.50);
        save("bruno", 89.90);
        save("carla", 310.00);
    }

    public List<Order> findAll() {
        simulateLatency(5, 20);
        return orders.values().stream()
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    public Optional<Order> findById(long id) {
        simulateLatency(2, 10);
        return Optional.ofNullable(orders.get(id));
    }

    public Order save(String customer, double total) {
        long id = ids.incrementAndGet();
        Order order = new Order(id, customer, total, Instant.now());
        orders.put(id, order);
        return order;
    }

    static void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
