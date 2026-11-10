
package ca.sheridancollege.shtirthb.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

import ca.sheridancollege.shtirthb.exception.AppExceptions.*;

public final class ExceptionMapping {

    private ExceptionMapping() {
    }

    private static final Map<
            Class<? extends AppBaseException>,
            HttpStatus
    > MAPPINGS = Map.of(
            DuplicateEmailException.class, HttpStatus.CONFLICT,
            ResourceNotFoundException.class, HttpStatus.NOT_FOUND,
            ValidationException.class, HttpStatus.BAD_REQUEST,
            BusinessRuleException.class, HttpStatus.CONFLICT,
            ConflictException.class, HttpStatus.CONFLICT,
            InvalidCredentialsException.class, HttpStatus.UNAUTHORIZED,
            AccountLockedException.class, HttpStatus.LOCKED,
            ServiceUnavailableException.class,
            HttpStatus.SERVICE_UNAVAILABLE
    );

    public static HttpStatus getStatus(
            AppBaseException exception) {

        Class<?> exceptionClass = exception.getClass();

        for (Map.Entry<
                Class<? extends AppBaseException>,
                HttpStatus
        > entry : MAPPINGS.entrySet()) {

            if (entry.getKey().isAssignableFrom(exceptionClass)) {
                return entry.getValue();
            }
        }

        return HttpStatus.BAD_REQUEST;
    }
}
