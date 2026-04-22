package docker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

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

}
