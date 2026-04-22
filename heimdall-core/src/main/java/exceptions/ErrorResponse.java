package exceptions;

import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private Response.Status status;
    private String message;
    private String error;
    private Instant timestamp;

    public ErrorResponse(Response.Status status, String message, String error) {
        this.status = status;
        this.message = message;
        this.error = error;
        this.timestamp = Instant.now();
    }
}