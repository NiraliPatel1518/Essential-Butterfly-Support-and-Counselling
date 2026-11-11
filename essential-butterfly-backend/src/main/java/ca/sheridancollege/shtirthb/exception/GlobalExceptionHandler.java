
package ca.sheridancollege.shtirthb.exception;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import ca.sheridancollege.shtirthb.dto.ErrorResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions.AppBaseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${app.errors.include-stacktrace:false}")
    private boolean includeStacktrace;

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            String errorName,
            HttpServletRequest request,
            String stack) {

        ErrorResponse response = new ErrorResponse(
                false,
                status.value(),
                message,
                null,
                Instant.now(),
                new ErrorResponse.ErrorDetails(
                        errorName,
                        request.getRequestURI(),
                        request.getMethod(),
                        includeStacktrace ? stack : null
                )
        );

        return ResponseEntity.status(status).body(response);
    }

    private void logError(
            HttpStatus status,
            String errorName,
            String message,
            HttpServletRequest request,
            Exception exception) {

        if (status.is5xxServerError()) {

            logger.error(
                    "Unhandled application error | type={} | method={} | path={} | message={}",
                    errorName,
                    request.getMethod(),
                    request.getRequestURI(),
                    message,
                    exception
            );

        } else {

            logger.warn(
                    "Request error | type={} | status={} | method={} | path={} | message={}",
                    errorName,
                    status.value(),
                    request.getMethod(),
                    request.getRequestURI(),
                    message
            );
        }
    }

    @ExceptionHandler(AppBaseException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            AppBaseException exception,
            HttpServletRequest request) {

        HttpStatus status = ExceptionMapping.getStatus(exception);

        logError(
                status,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                request,
                exception
        );

        return buildResponse(
                status,
                exception.getMessage(),
                exception.getClass().getSimpleName(),
                request,
                exception.getStackTrace().length > 0
                        ? exception.toString()
                                + "\n"
                                + stackTraceToString(exception)
                        : null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": "
                        + (error.getDefaultMessage() == null
                                ? "Invalid value."
                                : error.getDefaultMessage()))
                .orElse("Please check the submitted information.");

        logger.warn(
                "Validation error | method={} | path={} | message={}",
                request.getMethod(),
                request.getRequestURI(),
                message
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message,
                "ValidationError",
                request,
                null
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "The request body is missing or contains invalid data.",
                "InvalidRequestBody",
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "One or more request parameters have an invalid format.",
                "InvalidParameter",
                request,
                null
        );
    }

    /*
     * Temporary compatibility handler.
     * Remove after all services stop using ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleLegacyException(
            ResponseStatusException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(
                exception.getStatusCode().value());

        String message = exception.getReason() == null
                ? "The request could not be completed."
                : exception.getReason();

        logError(
                status,
                "LegacyHttpException",
                message,
                request,
                exception
        );

        return buildResponse(
                status,
                message,
                "LegacyHttpException",
                request,
                exception.toString()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        String message = "An unexpected error occurred. Please try again later.";

        logger.error(
                "Unhandled system exception | method={} | path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                message,
                "InternalServerError",
                request,
                exception.toString() + "\n" + stackTraceToString(exception)
        );
    }

    private String stackTraceToString(Throwable throwable) {

        java.io.StringWriter writer =
                new java.io.StringWriter();

        throwable.printStackTrace(
                new java.io.PrintWriter(writer));

        return writer.toString();
    }
}
