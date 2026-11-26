package ca.sheridancollege.shtirthb.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ca.sheridancollege.shtirthb.exception.AppExceptions.*;

class ExceptionMappingTest {

    /*
     * Checks: A duplicate email becomes HTTP 409 Conflict.
     * How:    ExceptionMapping.getStatus returns CONFLICT.
     */
    @Test
    void duplicateEmailMapsToConflict() {
        assertEquals(HttpStatus.CONFLICT,
                ExceptionMapping.getStatus(new DuplicateEmailException("a@b.com")));
    }

    /*
     * Checks: A missing resource becomes HTTP 404.
     * How:    ExceptionMapping.getStatus returns NOT_FOUND.
     */
    @Test
    void resourceNotFoundMapsToNotFound() {
        assertEquals(HttpStatus.NOT_FOUND,
                ExceptionMapping.getStatus(new ResourceNotFoundException("Update")));
    }

    /*
     * Checks: A validation error becomes HTTP 400.
     * How:    ExceptionMapping.getStatus returns BAD_REQUEST.
     */
    @Test
    void validationMapsToBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST,
                ExceptionMapping.getStatus(new ValidationException("bad")));
    }

    /*
     * Checks: A business rule error and a conflict error both become HTTP 409.
     * How:    We check both exception types return CONFLICT.
     */
    @Test
    void businessRuleAndConflictMapToConflict() {
        assertEquals(HttpStatus.CONFLICT,
                ExceptionMapping.getStatus(new BusinessRuleException("rule")));
        assertEquals(HttpStatus.CONFLICT,
                ExceptionMapping.getStatus(new ConflictException("conflict")));
    }

    /*
     * Checks: Wrong login details become HTTP 401.
     * How:    ExceptionMapping.getStatus returns UNAUTHORIZED.
     */
    @Test
    void invalidCredentialsMapsToUnauthorized() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                ExceptionMapping.getStatus(new InvalidCredentialsException()));
    }

    /*
     * Checks: A locked account becomes HTTP 423.
     * How:    ExceptionMapping.getStatus returns LOCKED.
     */
    @Test
    void accountLockedMapsToLocked() {
        assertEquals(HttpStatus.LOCKED,
                ExceptionMapping.getStatus(new AccountLockedException()));
    }

        /*
         * Checks: An unverified email becomes HTTP 403.
         * How:    ExceptionMapping.getStatus returns FORBIDDEN.
         */
        @Test
    void emailNotVerifiedMapsToForbidden() {
        assertEquals(HttpStatus.FORBIDDEN,
                ExceptionMapping.getStatus(new EmailNotVerifiedException()));
    }

    /*
     * Checks: An unavailable outside service becomes HTTP 503.
     * How:    Both versions of ServiceUnavailableException (with and without a cause) return
     *   SERVICE_UNAVAILABLE.
     */
    @Test
    void serviceUnavailableMapsTo503() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                ExceptionMapping.getStatus(new ServiceUnavailableException("down")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                ExceptionMapping.getStatus(
                        new ServiceUnavailableException("down", new RuntimeException("x"))));
    }

    /*
     * Checks: An unknown application exception falls back to HTTP 400.
     * How:    We use a new anonymous AppBaseException that has no special rule. The status must be
     *   BAD_REQUEST.
     */
    @Test
    void unknownApplicationExceptionFallsBackToBadRequest() {
        AppBaseException unknown = new AppBaseException("unknown") { };
        assertEquals(HttpStatus.BAD_REQUEST, ExceptionMapping.getStatus(unknown));
    }

    /*
     * Checks: The messages shown to users are safe and clear.
     * How:    We check three exact messages: invalid credentials, testimonial not found, and the
     *   locked admin account.
     */
    @Test
    void messagesAreSafeAndClear() {
        assertEquals("Invalid email or password.",
                new InvalidCredentialsException().getMessage());
        assertEquals("Testimonial not found.",
                new ResourceNotFoundException("Testimonial").getMessage());
        assertEquals("Admin account is temporarily locked. Please use the password reset option.",
                new AccountLockedException().getMessage());
    }

    /*
     * Checks: The duplicate-email message does not leak the email address.
     * How:    We pass secret@mail.com and check the message does not contain it.
     */
    @Test
    void duplicateEmailMessageDoesNotRevealTheEmail() {
        String message = new DuplicateEmailException("secret@mail.com").getMessage();
        assertEquals("An account with this email already exists.", message);
        assertFalse(message.contains("secret@mail.com"));
    }

    /*
     * Checks: ServiceUnavailableException keeps the cause for logging.
     * How:    getCause returns the same exception we passed in.
     */
    @Test
    void serviceUnavailableKeepsItsCause() {
        RuntimeException cause = new RuntimeException("smtp down");
        assertEquals(cause, new ServiceUnavailableException("mail", cause).getCause());
    }
}