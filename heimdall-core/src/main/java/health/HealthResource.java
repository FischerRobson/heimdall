package health;

import exceptions.ExceptionMappers;
import history.MetricsHistory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import tracing.HeimdallMonitor;

import java.util.Map;

@RequiredArgsConstructor
@HeimdallMonitor
@Path("/health")
public class HealthResource {

    private final MetricsHistory metricsHistory;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        var map = Map.of(
                "status", "UP",
                "snapshots", metricsHistory.getAll().size(),
                "version", "1.0.0"
        );
        return Response.ok(map).build();
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
