package ca.sheridancollege.shtirthb.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

import ca.sheridancollege.shtirthb.exception.AppExceptions.*;

class AppExceptionsTest {

    @Test
    void duplicateEmailUsesAFixedMessage() {
        DuplicateEmailException exception = new DuplicateEmailException("a@b.com");

        assertEquals("An account with this email already exists.", exception.getMessage());
    }

    @Test
    void resourceNotFoundNamesTheResource() {
        assertEquals("Intake submission not found.",
                new ResourceNotFoundException("Intake submission").getMessage());
    }

    @Test
    void validationKeepsItsMessage() {
        assertEquals("Title is required.", new ValidationException("Title is required.").getMessage());
    }

    @Test
    void businessRuleKeepsItsMessage() {
        assertEquals("Already approved.", new BusinessRuleException("Already approved.").getMessage());
    }

    @Test
    void conflictKeepsItsMessage() {
        assertEquals("Already exists.", new ConflictException("Already exists.").getMessage());
    }

    @Test
    void invalidCredentialsUsesAFixedMessage() {
        assertEquals("Invalid email or password.", new InvalidCredentialsException().getMessage());
    }

    @Test
    void accountLockedUsesAFixedMessage() {
        assertEquals("Admin account is temporarily locked. Please use the password reset option.",
                new AccountLockedException().getMessage());
    }

    @Test
    void serviceUnavailableKeepsMessageAndOptionalCause() {
        ServiceUnavailableException plain = new ServiceUnavailableException("Mail is down.");
        assertEquals("Mail is down.", plain.getMessage());
        assertNull(plain.getCause());

        RuntimeException cause = new RuntimeException("smtp timeout");
        ServiceUnavailableException withCause = new ServiceUnavailableException("Mail is down.", cause);
        assertEquals("Mail is down.", withCause.getMessage());
        assertEquals(cause, withCause.getCause());
    }

    @Test
    void baseExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        AppBaseException exception = new AppBaseException("wrapped", cause) { };

        assertEquals("wrapped", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void everyApplicationExceptionIsAnUncheckedAppBaseException() {
        assertInstanceOf(AppBaseException.class, new DuplicateEmailException("a@b.com"));
        assertInstanceOf(AppBaseException.class, new ResourceNotFoundException("X"));
        assertInstanceOf(AppBaseException.class, new ValidationException("x"));
        assertInstanceOf(AppBaseException.class, new BusinessRuleException("x"));
        assertInstanceOf(AppBaseException.class, new ConflictException("x"));
        assertInstanceOf(AppBaseException.class, new InvalidCredentialsException());
        assertInstanceOf(AppBaseException.class, new AccountLockedException());
        assertInstanceOf(AppBaseException.class, new ServiceUnavailableException("x"));
        assertInstanceOf(RuntimeException.class, new ValidationException("x"));
    }

    @Test
    void canBeCaughtThroughTheBaseType() {
        AppBaseException caught = assertThrows(AppBaseException.class, () -> {
            throw new ConflictException("Already exists.");
        });

        assertEquals("Already exists.", caught.getMessage());
    }

    @Test
    void utilityClassCannotBeUsedToCreateObjects() throws Exception {
        Constructor<AppExceptions> constructor = AppExceptions.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }
}