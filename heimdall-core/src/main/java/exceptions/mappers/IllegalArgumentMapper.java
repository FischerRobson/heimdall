package exceptions.mappers;

import exceptions.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tracing.HeimdallMonitor;

@Provider
@HeimdallMonitor
public class IllegalArgumentMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(IllegalArgumentException e) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(Response.Status.BAD_REQUEST, e.getMessage(), "Bad Request"))
                .build();
    }
}
