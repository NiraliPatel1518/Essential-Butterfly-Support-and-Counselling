
package ca.sheridancollege.shtirthb.dto;

import java.time.Instant;

public record ErrorResponse(
        boolean success,
        int statusCode,
        String message,
        Object data,
        Instant timestamp,
        ErrorDetails error
) {

    public record ErrorDetails(
            String name,
            String path,
            String method,
            String stack
    ) {
    }
}
