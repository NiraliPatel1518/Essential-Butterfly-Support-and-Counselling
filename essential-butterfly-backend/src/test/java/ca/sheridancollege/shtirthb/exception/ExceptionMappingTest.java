package ca.sheridancollege.shtirthb.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ca.sheridancollege.shtirthb.exception.AppExceptions.*;

class ExceptionMappingTest {

    @Test
    void duplicateEmailMapsToConflict() {
        assertEquals(HttpStatus.CONFLICT,
                ExceptionMapping.getStatus(new DuplicateEmailException("a@b.com")));
    }

    @Test
    void resourceNotFoundMapsToNotFound() {
        assertEquals(HttpStatus.NOT_FOUND,
                ExceptionMapping.getStatus(new ResourceNotFoundException("Update")));
    }

    @Test
    void validationMapsToBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST,
                ExceptionMapping.getStatus(new ValidationException("bad")));
    }

    @Test
    void businessRuleAndConflictMapToConflict() {
        assertEquals(HttpStatus.CONFLICT,
                ExceptionMapping.getStatus(new BusinessRuleException("rule")));
        assertEquals(HttpStatus.CONFLICT,
                ExceptionMapping.getStatus(new ConflictException("conflict")));
    }

    @Test
    void invalidCredentialsMapsToUnauthorized() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                ExceptionMapping.getStatus(new InvalidCredentialsException()));
    }

    @Test
    void accountLockedMapsToLocked() {
        assertEquals(HttpStatus.LOCKED,
                ExceptionMapping.getStatus(new AccountLockedException()));
    }

    @Test
    void serviceUnavailableMapsTo503() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                ExceptionMapping.getStatus(new ServiceUnavailableException("down")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                ExceptionMapping.getStatus(
                        new ServiceUnavailableException("down", new RuntimeException("x"))));
    }

    @Test
    void unknownApplicationExceptionFallsBackToBadRequest() {
        AppBaseException unknown = new AppBaseException("unknown") { };
        assertEquals(HttpStatus.BAD_REQUEST, ExceptionMapping.getStatus(unknown));
    }

    @Test
    void messagesAreSafeAndClear() {
        assertEquals("Invalid email or password.",
                new InvalidCredentialsException().getMessage());
        assertEquals("Testimonial not found.",
                new ResourceNotFoundException("Testimonial").getMessage());
        assertEquals("Admin account is temporarily locked. Please use the password reset option.",
                new AccountLockedException().getMessage());
    }

    @Test
    void duplicateEmailMessageDoesNotRevealTheEmail() {
        String message = new DuplicateEmailException("secret@mail.com").getMessage();
        assertEquals("An account with this email already exists.", message);
        assertFalse(message.contains("secret@mail.com"));
    }

    @Test
    void serviceUnavailableKeepsItsCause() {
        RuntimeException cause = new RuntimeException("smtp down");
        assertEquals(cause, new ServiceUnavailableException("mail", cause).getCause());
    }
}