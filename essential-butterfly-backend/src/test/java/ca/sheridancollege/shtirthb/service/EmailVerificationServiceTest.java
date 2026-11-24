package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.ResendVerificationRequest;
import ca.sheridancollege.shtirthb.dto.VerifyEmailRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/*
 * Tests for EmailVerificationService (6-digit code sent after signup).
 * No database and no real email: the repository, the encoder and the
 * mail sender are all mocks.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock ClientUserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JavaMailSender mailSender;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = newService("noreply@butterfly.test");
    }

    private EmailVerificationService newService(String fromAddress) {
        AppProperties properties = new AppProperties();
        properties.getMail().setFromAddress(fromAddress);
        return new EmailVerificationService(users, encoder, mailSender, properties);
    }

    private ClientUser unverifiedUser() {
        ClientUser user = new ClientUser("Jane Doe", "jane@mail.com", "ENCODED");
        user.setEmailVerified(false);
        return user;
    }

    /** A user whose code "HASH" is valid for the next 10 minutes. */
    private ClientUser userWithValidCode() {
        ClientUser user = unverifiedUser();
        user.setEmailVerificationCodeHash("HASH");
        user.setEmailVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));
        user.setEmailVerificationLastSentAt(LocalDateTime.now());
        return user;
    }

    private VerifyEmailRequest verifyRequest(String email, String code) {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    private ResendVerificationRequest resendRequest(String email) {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail(email);
        return request;
    }

    private void mailWillBeSent() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        when(encoder.encode(any())).thenReturn("NEW_HASH");
    }

    // ---------------------------------------------------------------- sending

    /*
     * Checks: The service stores a hashed code with an expiry and emails the user.
     * How:    Mockito fakes. After sendVerificationCode the user has the (fake) hash, a last-sent time
     *   and an expiry more than 9 minutes away. The user is saved and one email is sent.
     */
    @Test
    void sendVerificationCode_storesHashedCodeAndEmailsTheUser() {
        mailWillBeSent();
        ClientUser user = unverifiedUser();

        service.sendVerificationCode(user);

        assertEquals("NEW_HASH", user.getEmailVerificationCodeHash());
        assertNotNull(user.getEmailVerificationLastSentAt());
        assertTrue(user.getEmailVerificationCodeExpiry().isAfter(LocalDateTime.now().plusMinutes(9)));
        verify(users).save(user);
        verify(mailSender).send(any(MimeMessage.class));
    }

    /*
     * Checks: The code is stored as a hash, never as plain text.
     * How:    We check that the encoder received a 6-digit code, and the user holds only the encoder's
     *   output.
     */
    @Test
    void sendVerificationCode_storesTheCodeAsAHashNeverAsPlainText() {
        mailWillBeSent();
        ClientUser user = unverifiedUser();

        service.sendVerificationCode(user);

        // the value given to the encoder is a 6-digit code, and only its hash is stored
        verify(encoder).encode(argThat((CharSequence code) -> code.toString().matches("[0-9]{6}")));
        assertEquals("NEW_HASH", user.getEmailVerificationCodeHash());
    }

    /*
     * Checks: Sending a new code does not reset the wrong-attempt count.
     * How:    The user has 2 attempts before and still has 2 after.
     */
    @Test
    void sendVerificationCode_keepsTheIncorrectAttemptCount() {
        mailWillBeSent();
        ClientUser user = unverifiedUser();
        user.setEmailVerificationAttempts(2);

        service.sendVerificationCode(user);

        assertEquals(2, user.getEmailVerificationAttempts());
    }

    /*
     * Checks: A blank sender address gives a service error.
     * How:    It throws ServiceUnavailableException. No mail is sent and the user is not saved.
     */
    @Test
    void sendVerificationCode_throwsError_whenSenderAddressIsNotConfigured() {
        service = newService(" ");

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.sendVerificationCode(unverifiedUser()));

        verifyNoInteractions(mailSender);
        verify(users, never()).save(any());
    }

    /*
     * Checks: A mail failure gives a service error.
     * How:    The fake sender throws MailSendException. The service throws
     *   ServiceUnavailableException.
     */
    @Test
    void sendVerificationCode_throwsError_whenEmailCannotBeSent() {
        mailWillBeSent();
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.sendVerificationCode(unverifiedUser()));
    }

    // ----------------------------------------------------------------- verify

    /*
     * Checks: Verifying an unknown email is refused.
     * How:    findByEmail returns empty. It throws ValidationException.
     */
    @Test
    void verify_throwsError_whenNoAccountExistsForTheEmail() {
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "123456")));
    }

    /*
     * Checks: An already verified account is left alone.
     * How:    Nothing is saved and the encoder is never used.
     */
    @Test
    void verify_doesNothing_whenEmailIsAlreadyVerified() {
        ClientUser user = new ClientUser("Jane Doe", "jane@mail.com", "ENCODED"); // verified by default
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        service.verify(verifyRequest("jane@mail.com", "123456"));

        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

    /*
     * Checks: The right code verifies the email and clears the code data.
     * How:    The encoder says the code matches. The user is verified, the hash, expiry and last-sent
     *   time are null, the attempts and resend counts are 0, and the user is saved.
     */
    @Test
    void verify_marksTheEmailVerifiedAndClearsTheCode_whenCodeIsCorrect() {
        ClientUser user = userWithValidCode();
        user.setEmailVerificationAttempts(2);
        user.setEmailVerificationResendCount(1);
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("123456", "HASH")).thenReturn(true);

        service.verify(verifyRequest("jane@mail.com", "123456"));

        assertTrue(user.isEmailVerified());
        assertNull(user.getEmailVerificationCodeHash());
        assertNull(user.getEmailVerificationCodeExpiry());
        assertNull(user.getEmailVerificationLastSentAt());
        assertEquals(0, user.getEmailVerificationAttempts());
        assertEquals(0, user.getEmailVerificationResendCount());
        verify(users).save(user);
    }

    /*
     * Checks: Capital letters and spaces in the email are ignored.
     * How:    " Jane@Mail.COM " finds the same user and verifies it.
     */
    @Test
    void verify_ignoresCapitalLettersAndSpacesInTheEmail() {
        ClientUser user = userWithValidCode();
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("123456", "HASH")).thenReturn(true);

        service.verify(verifyRequest("  Jane@Mail.COM ", "123456"));

        assertTrue(user.isEmailVerified());
    }

    /*
     * Checks: A wrong code is counted and the message says how many tries are left.
     * How:    The message contains "4 attempt(s) remaining", the attempts are 1, the user is still
     *   unverified and is saved.
     */
    @Test
    void verify_countsTheWrongCodeAndSaysHowManyAttemptsAreLeft() {
        ClientUser user = userWithValidCode();
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("000000", "HASH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "000000")));

        assertTrue(error.getMessage().contains("4 attempt(s) remaining"));
        assertEquals(1, user.getEmailVerificationAttempts());
        assertFalse(user.isEmailVerified());
        verify(users).save(user);
    }

    /*
     * Checks: The fifth wrong code deletes the registration.
     * How:    With 4 attempts already, one more wrong code throws ValidationException ("five incorrect
     *   attempts"), and the user is deleted, not saved.
     */
    @Test
    void verify_deletesTheRegistration_onTheFifthWrongCode() {
        ClientUser user = userWithValidCode();
        user.setEmailVerificationAttempts(4);
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("000000", "HASH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "000000")));

        assertTrue(error.getMessage().contains("five incorrect attempts"));
        verify(users).delete(user);
        verify(users, never()).save(any());
    }

    /*
     * Checks: A user with five used attempts is deleted without checking the code.
     * How:    The user is deleted and the encoder is never used.
     */
    @Test
    void verify_deletesTheRegistration_whenFiveAttemptsWereAlreadyUsed() {
        ClientUser user = userWithValidCode();
        user.setEmailVerificationAttempts(5);
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "123456")));

        verify(users).delete(user);
        verifyNoInteractions(encoder);
    }

    /*
     * Checks: Verifying when no code was ever sent is refused.
     * How:    The user has no hash and no expiry. The message mentions that the code expired.
     */
    @Test
    void verify_throwsError_whenNoCodeWasEverSent() {
        ClientUser user = unverifiedUser(); // no hash, no expiry
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "123456")));

        assertTrue(error.getMessage().contains("expired"));
    }

    /*
     * Checks: An expired code is refused.
     * How:    The expiry is one minute ago. The message says expired, the encoder is never used, and
     *   the user stays unverified.
     */
    @Test
    void verify_throwsError_whenTheCodeHasExpired() {
        ClientUser user = userWithValidCode();
        user.setEmailVerificationCodeExpiry(LocalDateTime.now().minusMinutes(1));
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "123456")));

        assertTrue(error.getMessage().contains("expired"));
        verifyNoInteractions(encoder);
        assertFalse(user.isEmailVerified());
    }

    // ----------------------------------------------------------------- resend

    /*
     * Checks: Resend for an unknown email gives not found.
     * How:    findByEmail returns empty. It throws ResourceNotFoundException.
     */
    @Test
    void resend_throwsError_whenNoUnverifiedAccountExists() {
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.resend(resendRequest("jane@mail.com")));
    }

    /*
     * Checks: Resend for an already verified email is refused.
     * How:    It throws BusinessRuleException and no mail is sent.
     */
    @Test
    void resend_throwsError_whenEmailIsAlreadyVerified() {
        ClientUser user = new ClientUser("Jane Doe", "jane@mail.com", "ENCODED");
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: A fourth resend is refused.
     * How:    The resend count is 3. The message says "maximum number of resend requests" and no mail
     *   is sent.
     */
    @Test
    void resend_throwsError_afterTheThirdResend() {
        ClientUser user = userWithValidCode();
        user.setEmailVerificationResendCount(3);
        user.setEmailVerificationLastSentAt(LocalDateTime.now().minusHours(1));
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        assertTrue(error.getMessage().contains("maximum number of resend requests"));
        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: Resend is refused when no code was ever sent.
     * How:    lastSentAt is null. It throws BusinessRuleException and no mail is sent.
     */
    @Test
    void resend_throwsError_whenNoCodeWasSentBefore() {
        ClientUser user = unverifiedUser(); // lastSentAt is null
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: Resend within 60 seconds is refused.
     * How:    The last send was 30 seconds ago. The message starts with "Please wait", no mail is
     *   sent, and the resend count stays 0.
     */
    @Test
    void resend_throwsError_whenTheFirstWaitOf60SecondsIsNotOver() {
        ClientUser user = userWithValidCode();
        user.setEmailVerificationLastSentAt(LocalDateTime.now().minusSeconds(30));
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        assertTrue(error.getMessage().startsWith("Please wait"));
        verifyNoInteractions(mailSender);
        assertEquals(0, user.getEmailVerificationResendCount());
    }

    /*
     * Checks: After the wait, resend sends a new code and counts it.
     * How:    The last send was 61 seconds ago. The resend count is 1, the hash is updated, and one
     *   mail is sent.
     */
    @Test
    void resend_sendsANewCodeAndCountsIt_whenTheWaitIsOver() {
        mailWillBeSent();
        ClientUser user = userWithValidCode();
        user.setEmailVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        service.resend(resendRequest("jane@mail.com"));

        assertEquals(1, user.getEmailVerificationResendCount());
        assertEquals("NEW_HASH", user.getEmailVerificationCodeHash());
        verify(mailSender).send(any(MimeMessage.class));
    }

    /*
     * Checks: The second resend needs a longer wait.
     * How:    With 1 resend done and the last send 80 seconds ago, resend is refused.
     */
    @Test
    void resend_waitsLongerForTheSecondResend() {
        // after one resend the wait is 90 seconds, so 80 seconds is still too soon
        ClientUser user = userWithValidCode();
        user.setEmailVerificationResendCount(1);
        user.setEmailVerificationLastSentAt(LocalDateTime.now().minusSeconds(80));
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: A failed resend email is not counted.
     * How:    The fake sender throws. The service throws ServiceUnavailableException and the resend
     *   count stays 0.
     */
    @Test
    void resend_doesNotCountTheResend_whenTheEmailCannotBeSent() {
        mailWillBeSent();
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        ClientUser user = userWithValidCode();
        user.setEmailVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        assertEquals(0, user.getEmailVerificationResendCount());
    }
}