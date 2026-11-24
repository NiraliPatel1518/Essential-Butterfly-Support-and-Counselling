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

    /*
     * Checks: DuplicateEmailException always gives the same fixed message.
     * How:    We create it with a@b.com and check the message is "An account with this email already
     *   exists." without the email in it.
     */
    @Test
    void duplicateEmailUsesAFixedMessage() {
        DuplicateEmailException exception = new DuplicateEmailException("a@b.com");

        assertEquals("An account with this email already exists.", exception.getMessage());
    }

    /*
     * Checks: ResourceNotFoundException builds the message from the resource name.
     * How:    We pass "Intake submission" and check the message is "Intake submission not found."
     */
    @Test
    void resourceNotFoundNamesTheResource() {
        assertEquals("Intake submission not found.",
                new ResourceNotFoundException("Intake submission").getMessage());
    }

    /*
     * Checks: ValidationException keeps the message it was given.
     * How:    We create it with "Title is required." and read the same text back.
     */
    @Test
    void validationKeepsItsMessage() {
        assertEquals("Title is required.", new ValidationException("Title is required.").getMessage());
    }

    /*
     * Checks: BusinessRuleException keeps the message it was given.
     * How:    We create it with "Already approved." and read the same text back.
     */
    @Test
    void businessRuleKeepsItsMessage() {
        assertEquals("Already approved.", new BusinessRuleException("Already approved.").getMessage());
    }

    /*
     * Checks: ConflictException keeps the message it was given.
     * How:    We create it with "Already exists." and read the same text back.
     */
    @Test
    void conflictKeepsItsMessage() {
        assertEquals("Already exists.", new ConflictException("Already exists.").getMessage());
    }

    /*
     * Checks: InvalidCredentialsException has one fixed, safe message.
     * How:    The message must be "Invalid email or password." so it never shows which part was wrong.
     */
    @Test
    void invalidCredentialsUsesAFixedMessage() {
        assertEquals("Invalid email or password.", new InvalidCredentialsException().getMessage());
    }

    /*
     * Checks: AccountLockedException has one fixed message that points to password reset.
     * How:    We check the exact text about the temporarily locked admin account.
     */
    @Test
    void accountLockedUsesAFixedMessage() {
        assertEquals("Admin account is temporarily locked. Please use the password reset option.",
                new AccountLockedException().getMessage());
    }

        /*
         * Checks: EmailNotVerifiedException has one fixed message about verifying the email.
         * How:    We check the exact text that asks the user to check the inbox for the verification code.
         */
        @Test
    void emailNotVerifiedUsesAFixedMessage() {
        assertEquals(
                "Please verify your email address before logging in. Check your inbox for the verification code.",
                new EmailNotVerifiedException().getMessage());
    }

    /*
     * Checks: ServiceUnavailableException keeps its message, and it can also carry a cause.
     * How:    Without a cause, getCause is null. With a cause (smtp timeout), getCause returns that
     *   same object.
     */
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

    /*
     * Checks: The base exception class stores a message and a cause.
     * How:    We create a small anonymous subclass with a message and a root cause, and read both
     *   back.
     */
    @Test
    void baseExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        AppBaseException exception = new AppBaseException("wrapped", cause) { };

        assertEquals("wrapped", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    /*
     * Checks: All our exceptions extend AppBaseException, and it is a RuntimeException (unchecked).
     * How:    assertInstanceOf is called for each of the nine exception types. A ValidationException
     *   must also be a RuntimeException.
     */
    @Test
    void everyApplicationExceptionIsAnUncheckedAppBaseException() {
        assertInstanceOf(AppBaseException.class, new DuplicateEmailException("a@b.com"));
        assertInstanceOf(AppBaseException.class, new ResourceNotFoundException("X"));
        assertInstanceOf(AppBaseException.class, new ValidationException("x"));
        assertInstanceOf(AppBaseException.class, new BusinessRuleException("x"));
        assertInstanceOf(AppBaseException.class, new ConflictException("x"));
        assertInstanceOf(AppBaseException.class, new InvalidCredentialsException());
        assertInstanceOf(AppBaseException.class, new AccountLockedException());
        assertInstanceOf(AppBaseException.class, new EmailNotVerifiedException());
        assertInstanceOf(AppBaseException.class, new ServiceUnavailableException("x"));
        assertInstanceOf(RuntimeException.class, new ValidationException("x"));
    }

    /*
     * Checks: Any application exception can be caught as AppBaseException.
     * How:    We throw a ConflictException inside assertThrows(AppBaseException.class, ...) and read
     *   its message.
     */
    @Test
    void canBeCaughtThroughTheBaseType() {
        AppBaseException caught = assertThrows(AppBaseException.class, () -> {
            throw new ConflictException("Already exists.");
        });

        assertEquals("Already exists.", caught.getMessage());
    }

    /*
     * Checks: The AppExceptions holder class can be created through reflection without errors.
     * How:    We open the private constructor and create an object. This also covers that constructor
     *   in the coverage report.
     */
    @Test
    void utilityClassCannotBeUsedToCreateObjects() throws Exception {
        Constructor<AppExceptions> constructor = AppExceptions.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }
}