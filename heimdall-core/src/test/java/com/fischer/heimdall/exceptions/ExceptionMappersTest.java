package com.fischer.heimdall.exceptions;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionMappersTest {

    @Test
    void mapsToStatusAndErrorBody() {
        assertStatus(404, ExceptionMappers.notFound());
        assertStatus(400, ExceptionMappers.badRequest(new IllegalArgumentException("bad id")));
        assertStatus(500, ExceptionMappers.nullPointer());
        assertStatus(500, ExceptionMappers.internalError(new IllegalStateException("broken")));
    }

    @Test
    void bodyCarriesMessageAndErrorType() {
        ErrorResponse body = (ErrorResponse) ExceptionMappers.internalError(new IllegalStateException("broken")).getEntity();
        assertEquals("broken", body.getMessage());
        assertEquals("IllegalStateException", body.getError());

        ErrorResponse npe = (ErrorResponse) ExceptionMappers.nullPointer().getEntity();
        // the raw NPE message is never exposed
        assertEquals("An unexpected error occurred", npe.getMessage());
    }

    private static void assertStatus(int expected, Response response) {
        assertEquals(expected, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
    }
}
