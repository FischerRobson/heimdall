package exceptions.mappers;

import exceptions.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tracing.HeimdallMonitor;

@Provider
@HeimdallMonitor
public class NullPointerMapper implements ExceptionMapper<NullPointerException> {
    @Override
    public Response toResponse(NullPointerException e) {
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred",
                        "NullPointerException"))
                .build();
    }
}