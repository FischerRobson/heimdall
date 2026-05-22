package exceptions.mappers;

import exceptions.ErrorResponse;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tracing.HeimdallMonitor;

@Provider
@HeimdallMonitor
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException e) {
        return Response
                .status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(Response.Status.NOT_FOUND,
                        "Resource not found",
                        "NotFoundException"))
                .build();
    }

}