package ca.sheridancollege.shtirthb.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import ca.sheridancollege.shtirthb.dto.ErrorResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/test");
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        return new MethodParameter(
                getClass().getDeclaredMethod("sample", String.class), 0);
    }

    @Test
    void applicationExceptionGivesTheMappedStatusAndAClearBody() {
        ResponseEntity<ErrorResponse> response = handler.handleApplicationException(
                new ResourceNotFoundException("Testimonial"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.success());
        assertEquals(404, body.statusCode());
        assertEquals("Testimonial not found.", body.message());
        assertNotNull(body.timestamp());
        assertEquals("ResourceNotFoundException", body.error().name());
        assertEquals("/api/test", body.error().path());
        assertEquals("POST", body.error().method());
    }

    @Test
    void eachApplicationExceptionUsesItsOwnStatus() {
        assertEquals(HttpStatus.UNAUTHORIZED, handler
                .handleApplicationException(new InvalidCredentialsException(), request)
                .getStatusCode());
        assertEquals(HttpStatus.LOCKED, handler
                .handleApplicationException(new AccountLockedException(), request)
                .getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler
                .handleApplicationException(new DuplicateEmailException("a@b.com"), request)
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler
                .handleApplicationException(new ValidationException("bad"), request)
                .getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, handler
                .handleApplicationException(new ServiceUnavailableException("down"), request)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, handler
                .handleApplicationException(new EmailNotVerifiedException(), request)
                .getStatusCode());
    }

    @Test
    void stackTraceIsHiddenByDefault() {
        ErrorResponse body = handler.handleApplicationException(
                new ValidationException("bad"), request).getBody();

        assertNull(body.error().stack());
    }

    @Test
    void stackTraceIsIncludedOnlyWhenSwitchedOn() {
        ReflectionTestUtils.setField(handler, "includeStacktrace", true);

        ErrorResponse body = handler.handleApplicationException(
                new ValidationException("bad"), request).getBody();

        assertNotNull(body.error().stack());
        assertTrue(body.error().stack().contains("ValidationException"));
    }

    @Test
    void invalidFieldGivesFieldNameAndMessage() throws Exception {
        BeanPropertyBindingResult errors =
                new BeanPropertyBindingResult(new Object(), "form");
        errors.addError(new FieldError("form", "email", "must not be blank"));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(
                new MethodArgumentNotValidException(parameter(), errors), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("email: must not be blank", response.getBody().message());
        assertEquals("ValidationError", response.getBody().error().name());
    }

    @Test
    void validationWithoutFieldErrorsGivesAGenericMessage() throws Exception {
        BeanPropertyBindingResult errors =
                new BeanPropertyBindingResult(new Object(), "form");

        ResponseEntity<ErrorResponse> response = handler.handleValidation(
                new MethodArgumentNotValidException(parameter(), errors), request);

        assertEquals("Please check the submitted information.",
                response.getBody().message());
    }

    @Test
    void unreadableBodyGivesBadRequest() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "bad json", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response =
                handler.handleUnreadableRequest(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("The request body is missing or contains invalid data.",
                response.getBody().message());
        assertEquals("InvalidRequestBody", response.getBody().error().name());
    }

    @Test
    void wrongParameterTypeGivesBadRequest() throws Exception {
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException(
                        "abc", Integer.class, "id", parameter(), null);

        ResponseEntity<ErrorResponse> response =
                handler.handleTypeMismatch(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("InvalidParameter", response.getBody().error().name());
    }

    @Test
    void legacyResponseStatusExceptionKeepsItsStatusAndReason() {
        ResponseEntity<ErrorResponse> response = handler.handleLegacyException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Not allowed", response.getBody().message());
        assertEquals("LegacyHttpException", response.getBody().error().name());
    }

    @Test
    void legacyExceptionWithoutReasonGetsADefaultMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleLegacyException(
                new ResponseStatusException(HttpStatus.NOT_FOUND), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("The request could not be completed.",
                response.getBody().message());
    }

    @Test
    void unexpectedErrorGives500WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("database password is hunter2"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred. Please try again later.",
                response.getBody().message());
        assertEquals("InternalServerError", response.getBody().error().name());
        assertFalse(response.getBody().message().contains("hunter2"));
        assertNull(response.getBody().error().stack());
    }
}