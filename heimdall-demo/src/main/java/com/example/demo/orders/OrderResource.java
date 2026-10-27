package com.example.demo.orders;

import com.fischer.heimdall.tracing.HeimdallMonitor;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Demo endpoints. Call them (curl, Swagger UI at /q/swagger-ui) and watch the
 * Traces page of the dashboard at /heimdall/.
 *
 * Classes need @HeimdallMonitor for their methods to become spans; the request
 * itself is traced either way.
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@HeimdallMonitor
public class OrderResource {

    @Inject
    OrderService orderService;

    @GET
    public List<Order> list() {
        return orderService.list();
    }

    @GET
    @Path("/{id}")
    public Order get(@PathParam("id") long id) {
        return orderService.get(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateOrderRequest request) {
        try {
            return Response.status(Response.Status.CREATED).entity(orderService.create(request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/report")
    public List<Order> slowReport() {
        return orderService.slowReport();
    }

    @GET
    @Path("/fail")
    public void fail() {
        orderService.fail();
    }
}
