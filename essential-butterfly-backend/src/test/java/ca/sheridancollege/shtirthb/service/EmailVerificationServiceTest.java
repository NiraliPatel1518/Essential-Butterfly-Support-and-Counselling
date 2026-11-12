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

    @Test
    void sendVerificationCode_storesTheCodeAsAHashNeverAsPlainText() {
        mailWillBeSent();
        ClientUser user = unverifiedUser();

        service.sendVerificationCode(user);

        // the value given to the encoder is a 6-digit code, and only its hash is stored
        verify(encoder).encode(argThat((CharSequence code) -> code.toString().matches("[0-9]{6}")));
        assertEquals("NEW_HASH", user.getEmailVerificationCodeHash());
    }

    @Test
    void sendVerificationCode_keepsTheIncorrectAttemptCount() {
        mailWillBeSent();
        ClientUser user = unverifiedUser();
        user.setEmailVerificationAttempts(2);

        service.sendVerificationCode(user);

        assertEquals(2, user.getEmailVerificationAttempts());
    }

    @Test
    void sendVerificationCode_throwsError_whenSenderAddressIsNotConfigured() {
        service = newService(" ");

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.sendVerificationCode(unverifiedUser()));

        verifyNoInteractions(mailSender);
        verify(users, never()).save(any());
    }

    @Test
    void sendVerificationCode_throwsError_whenEmailCannotBeSent() {
        mailWillBeSent();
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.sendVerificationCode(unverifiedUser()));
    }

    // ----------------------------------------------------------------- verify

    @Test
    void verify_throwsError_whenNoAccountExistsForTheEmail() {
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "123456")));
    }

    @Test
    void verify_doesNothing_whenEmailIsAlreadyVerified() {
        ClientUser user = new ClientUser("Jane Doe", "jane@mail.com", "ENCODED"); // verified by default
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        service.verify(verifyRequest("jane@mail.com", "123456"));

        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

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

    @Test
    void verify_ignoresCapitalLettersAndSpacesInTheEmail() {
        ClientUser user = userWithValidCode();
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("123456", "HASH")).thenReturn(true);

        service.verify(verifyRequest("  Jane@Mail.COM ", "123456"));

        assertTrue(user.isEmailVerified());
    }

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

    @Test
    void verify_throwsError_whenNoCodeWasEverSent() {
        ClientUser user = unverifiedUser(); // no hash, no expiry
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(verifyRequest("jane@mail.com", "123456")));

        assertTrue(error.getMessage().contains("expired"));
    }

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

    @Test
    void resend_throwsError_whenNoUnverifiedAccountExists() {
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.resend(resendRequest("jane@mail.com")));
    }

    @Test
    void resend_throwsError_whenEmailIsAlreadyVerified() {
        ClientUser user = new ClientUser("Jane Doe", "jane@mail.com", "ENCODED");
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        verifyNoInteractions(mailSender);
    }

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

    @Test
    void resend_throwsError_whenNoCodeWasSentBefore() {
        ClientUser user = unverifiedUser(); // lastSentAt is null
        when(users.findByEmail("jane@mail.com")).thenReturn(Optional.of(user));

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resend(resendRequest("jane@mail.com")));

        verifyNoInteractions(mailSender);
    }

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