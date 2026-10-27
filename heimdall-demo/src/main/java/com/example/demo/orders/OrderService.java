package com.example.demo.orders;

import com.fischer.heimdall.tracing.Heimdall;
import com.fischer.heimdall.tracing.HeimdallMonitor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
@HeimdallMonitor
public class OrderService {

    @Inject
    OrderRepository repository;

    public List<Order> list() {
        return repository.findAll();
    }

    public Order get(long id) {
        // business context on the current span — shows up in the trace's tags
        Heimdall.tag("orderId", id);
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    public Order create(CreateOrderRequest request) {
        if (request == null || request.customer() == null || request.customer().isBlank()) {
            throw new IllegalArgumentException("customer is required");
        }
        Order order = repository.save(request.customer(), request.total());
        Heimdall.tag("orderId", order.id());
        return order;
    }

    /** Deliberately slow, to see a long span in the waterfall. */
    public List<Order> slowReport() {
        OrderRepository.simulateLatency(400, 900);
        return repository.findAll();
    }

    /** Deliberately failing, to see an error span. */
    public void fail() {
        throw new IllegalStateException("payment provider unavailable (simulated)");
    }
}
