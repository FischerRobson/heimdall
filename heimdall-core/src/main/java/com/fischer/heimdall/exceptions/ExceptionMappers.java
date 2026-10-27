package com.fischer.heimdall.exceptions;

import jakarta.ws.rs.core.Response;

public final class ExceptionMappers {

    private ExceptionMappers() {}

    public static Response notFound() {
        return Response
                .status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(Response.Status.NOT_FOUND,
                        "Resource not found",
                        "NotFoundException"))
                .build();
    }

    public static Response badRequest(IllegalArgumentException e) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(Response.Status.BAD_REQUEST, e.getMessage(), "Bad Request"))
                .build();
    }

    public static Response nullPointer() {
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred",
                        "NullPointerException"))
                .build();
    }

    public static Response internalError(Exception e) {
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(
                        Response.Status.INTERNAL_SERVER_ERROR,
                        e.getMessage(),
                        e.getClass().getSimpleName()
                ))
                .build();
    }
}
