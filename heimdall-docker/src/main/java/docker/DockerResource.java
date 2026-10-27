package docker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.time.Instant;
import java.util.Map;

@Path("/docker")
@ApplicationScoped
@RequiredArgsConstructor
public class DockerResource {

    private final DockerService dockerService;

    @GET
    @Path("/containers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocker() {
        return Response.ok(dockerService.getRunningContainers()).build();
    }

    @GET
    @Path("/host")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHost() {
        DockerHostInfo hostInfo = dockerService.getHostInfo();
        if (hostInfo == null) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of(
                            "status", Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                            "message", "Docker daemon not reachable",
                            "timestamp", Instant.now().toString()
                    ))
                    .build();
        }
        return Response.ok(hostInfo).build();
    }

    // scoped to this resource only — a global @Provider ExceptionMapper would also
    // intercept exceptions thrown by the host application's own endpoints. This module
    // has no dependency on heimdall-core, so it can't reuse its ErrorResponse type.
    @ServerExceptionMapper
    public Response mapException(Exception e) {
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                        "status", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "message", String.valueOf(e.getMessage()),
                        "error", e.getClass().getSimpleName(),
                        "timestamp", Instant.now().toString()
                ))
                .build();
    }

}
