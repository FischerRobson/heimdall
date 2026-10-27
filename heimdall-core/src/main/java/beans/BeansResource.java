package beans;

import exceptions.ExceptionMappers;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import tracing.HeimdallMonitor;

@Path("/heimdall/api/beans")
@RequiredArgsConstructor
@HeimdallMonitor
public class BeansResource {

    private final BeansService beansService;

    @GET
    @Path("/graph")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGraph() {
        return Response.ok(beansService.getGraph()).build();
    }

    // scoped to this resource only — see MetricsResource for rationale
    @ServerExceptionMapper
    public Response mapIllegalArgument(IllegalArgumentException e) {
        return ExceptionMappers.badRequest(e);
    }

    @ServerExceptionMapper
    public Response mapNotFound(NotFoundException e) {
        return ExceptionMappers.notFound();
    }

    @ServerExceptionMapper
    public Response mapNullPointer(NullPointerException e) {
        return ExceptionMappers.nullPointer();
    }

    @ServerExceptionMapper
    public Response mapException(Exception e) {
        return ExceptionMappers.internalError(e);
    }
}
