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
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.AdminLoginChallengeResponse;
import ca.sheridancollege.shtirthb.dto.AdminLoginOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminLoginResendRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/*
 * Tests for the admin sign-in code (two-step login) in AdminEmailSecurityService.
 * After a correct password, the admin must also enter a 6-digit email code.
 * No database and no real email: everything around the service is a mock.
 */
@ExtendWith(MockitoExtension.class)
class AdminLoginOtpServiceTest {

    private static final String TOKEN = "7.secret";

    @Mock AdminUserRepository admins;
    @Mock ClientUserRepository clients;
    @Mock PasswordEncoder encoder;
    @Mock JavaMailSender mailSender;

    private AdminEmailSecurityService service;
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        service = newService("noreply@butterfly.test");
        admin = new AdminUser("Admin", "admin@test.com", "ENCODED");
        ReflectionTestUtils.setField(admin, "id", 7L);
    }

    private AdminEmailSecurityService newService(String fromAddress) {
        AppProperties properties = new AppProperties();
        properties.getMail().setFromAddress(fromAddress);
        return new AdminEmailSecurityService(admins, clients, encoder, mailSender, properties);
    }

    private void mailWillBeSent() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        when(encoder.encode(any())).thenReturn("HASH");
    }

    /** The admin passed the password step: a challenge and a code ("OH") are active. */
    private void activeLogin() {
        admin.setLoginChallengeTokenHash("CH");
        admin.setLoginOtpHash("OH");
        admin.setLoginOtpExpiry(LocalDateTime.now().plusMinutes(10));
        admin.setLoginOtpLastSentAt(LocalDateTime.now());
    }

    /** The challenge token is valid, so the admin can be found from it. */
    private void challengeIsValid() {
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(encoder.matches(TOKEN, "CH")).thenReturn(true);
    }

    private AdminLoginOtpRequest otpRequest(String token, String code) {
        AdminLoginOtpRequest request = new AdminLoginOtpRequest();
        request.setChallengeToken(token);
        request.setCode(code);
        return request;
    }

    private AdminLoginResendRequest resendRequest(String token) {
        AdminLoginResendRequest request = new AdminLoginResendRequest();
        request.setChallengeToken(token);
        return request;
    }

    // ------------------------------------------------------------ start login

    @Test
    void startLoginVerification_throwsError_whenAdminDoesNotExist() {
        when(admins.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.startLoginVerification("admin@test.com"));
    }

    @Test
    void startLoginVerification_emailsACodeAndReturnsAChallenge() {
        mailWillBeSent();
        when(admins.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        AdminLoginChallengeResponse response = service.startLoginVerification("admin@test.com");

        assertTrue(response.requiresOtp());
        assertTrue(response.challengeToken().startsWith("7."));
        assertEquals("ad***@test.com", response.maskedEmail());
        assertEquals("HASH", admin.getLoginChallengeTokenHash());
        assertEquals("HASH", admin.getLoginOtpHash());
        assertEquals(0, admin.getLoginOtpAttempts());
        assertEquals(0, admin.getLoginOtpResendCount());
        assertNotNull(admin.getLoginOtpLastSentAt());
        assertTrue(admin.getLoginOtpExpiry().isAfter(LocalDateTime.now().plusMinutes(9)));
        verify(mailSender).send(any(MimeMessage.class));
        verify(admins).save(admin);
    }

    @Test
    void startLoginVerification_ignoresCapitalLettersAndSpacesInTheEmail() {
        mailWillBeSent();
        when(admins.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        AdminLoginChallengeResponse response = service.startLoginVerification("  ADMIN@Test.com ");

        assertTrue(response.requiresOtp());
    }

    @Test
    void startLoginVerification_hidesAVeryShortEmailCompletely() {
        mailWillBeSent();
        admin.setEmail("a@test.com");
        when(admins.findByEmail("a@test.com")).thenReturn(Optional.of(admin));

        AdminLoginChallengeResponse response = service.startLoginVerification("a@test.com");

        assertEquals("***@test.com", response.maskedEmail());
    }

    @Test
    void startLoginVerification_throwsError_whenSenderAddressIsNotConfigured() {
        service = newService("");
        when(admins.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.startLoginVerification("admin@test.com"));

        verify(admins, never()).save(any());
    }

    @Test
    void startLoginVerification_throwsError_whenEmailCannotBeSent() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        when(admins.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.startLoginVerification("admin@test.com"));

        verify(admins, never()).save(any());
    }

    // ----------------------------------------------------- challenge is checked

    @Test
    void verifyLoginOtp_throwsError_whenChallengeTokenIsMissing() {
        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(null, "123456")));
    }

    @Test
    void verifyLoginOtp_throwsError_whenChallengeTokenHasNoDot() {
        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest("nodot", "123456")));
    }

    @Test
    void verifyLoginOtp_throwsError_whenAdminIdInTheTokenIsNotANumber() {
        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest("abc.secret", "123456")));
    }

    @Test
    void verifyLoginOtp_throwsError_whenNoAdminHasThatId() {
        when(admins.findById(7L)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "123456")));
    }

    @Test
    void verifyLoginOtp_throwsError_whenTheChallengeWasAlreadyUsed() {
        // no challenge hash stored: the sign-in was already completed or cancelled
        when(admins.findById(7L)).thenReturn(Optional.of(admin));

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "123456")));
    }

    @Test
    void verifyLoginOtp_throwsError_whenTheChallengeTokenDoesNotMatch() {
        activeLogin();
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(encoder.matches(TOKEN, "CH")).thenReturn(false);

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "123456")));
    }

    // ------------------------------------------------------------ verify code

    @Test
    void verifyLoginOtp_throwsError_whenNoCodeIsActive() {
        admin.setLoginChallengeTokenHash("CH"); // no OTP hash
        challengeIsValid();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "123456")));

        assertTrue(error.getMessage().contains("log in again"));
    }

    @Test
    void verifyLoginOtp_throwsError_whenTheCodeHasExpired() {
        activeLogin();
        admin.setLoginOtpExpiry(LocalDateTime.now().minusMinutes(1));
        challengeIsValid();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "123456")));

        assertTrue(error.getMessage().contains("expired"));
    }

    @Test
    void verifyLoginOtp_endsTheSignIn_whenFiveAttemptsWereAlreadyUsed() {
        activeLogin();
        admin.setLoginOtpAttempts(5);
        challengeIsValid();

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "123456")));

        assertNull(admin.getLoginChallengeTokenHash());
        assertNull(admin.getLoginOtpHash());
        verify(admins).save(admin);
    }

    @Test
    void verifyLoginOtp_countsTheWrongCodeAndSaysHowManyAttemptsAreLeft() {
        activeLogin();
        challengeIsValid();
        when(encoder.matches("000000", "OH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "000000")));

        assertTrue(error.getMessage().contains("4 attempt(s) remaining"));
        assertEquals(1, admin.getLoginOtpAttempts());
        assertNotNull(admin.getLoginChallengeTokenHash());
        verify(admins).save(admin);
    }

    @Test
    void verifyLoginOtp_endsTheSignIn_onTheFifthWrongCode() {
        activeLogin();
        admin.setLoginOtpAttempts(4);
        challengeIsValid();
        when(encoder.matches("000000", "OH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyLoginOtp(otpRequest(TOKEN, "000000")));

        assertTrue(error.getMessage().contains("five incorrect attempts"));
        assertNull(admin.getLoginChallengeTokenHash());
        assertNull(admin.getLoginOtpHash());
        verify(admins).save(admin);
    }

    @Test
    void verifyLoginOtp_returnsTheAdminAndUsesUpTheChallenge_whenCodeIsCorrect() {
        activeLogin();
        admin.setLoginOtpAttempts(2);
        challengeIsValid();
        when(encoder.matches("123456", "OH")).thenReturn(true);

        AdminUser result = service.verifyLoginOtp(otpRequest(TOKEN, "123456"));

        assertSame(admin, result);
        assertNull(admin.getLoginChallengeTokenHash());
        assertNull(admin.getLoginOtpHash());
        assertNull(admin.getLoginOtpExpiry());
        assertNull(admin.getLoginOtpLastSentAt());
        assertEquals(0, admin.getLoginOtpAttempts());
        assertEquals(0, admin.getLoginOtpResendCount());
        verify(admins).save(admin);
    }

    // ----------------------------------------------------------------- resend

    @Test
    void resendLoginOtp_throwsError_whenTheSignInIsNoLongerActive() {
        admin.setLoginChallengeTokenHash("CH"); // no OTP hash
        challengeIsValid();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendLoginOtp(resendRequest(TOKEN)));

        verifyNoInteractions(mailSender);
    }

    @Test
    void resendLoginOtp_endsTheSignIn_whenFiveAttemptsWereAlreadyUsed() {
        activeLogin();
        admin.setLoginOtpAttempts(5);
        challengeIsValid();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendLoginOtp(resendRequest(TOKEN)));

        assertNull(admin.getLoginChallengeTokenHash());
        verify(admins).save(admin);
        verifyNoInteractions(mailSender);
    }

    @Test
    void resendLoginOtp_throwsError_afterTheThirdResend() {
        activeLogin();
        admin.setLoginOtpResendCount(3);
        admin.setLoginOtpLastSentAt(LocalDateTime.now().minusHours(1));
        challengeIsValid();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resendLoginOtp(resendRequest(TOKEN)));

        assertTrue(error.getMessage().contains("maximum number of resend requests"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void resendLoginOtp_throwsError_whenNoCodeWasSentBefore() {
        activeLogin();
        admin.setLoginOtpLastSentAt(null);
        challengeIsValid();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendLoginOtp(resendRequest(TOKEN)));

        verifyNoInteractions(mailSender);
    }

    @Test
    void resendLoginOtp_throwsError_whenTheFirstWaitOf60SecondsIsNotOver() {
        activeLogin();
        admin.setLoginOtpLastSentAt(LocalDateTime.now().minusSeconds(30));
        challengeIsValid();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resendLoginOtp(resendRequest(TOKEN)));

        assertTrue(error.getMessage().startsWith("Please wait"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void resendLoginOtp_waitsLongerForTheSecondResend() {
        // after one resend the wait is 90 seconds, so 80 seconds is still too soon
        activeLogin();
        admin.setLoginOtpResendCount(1);
        admin.setLoginOtpLastSentAt(LocalDateTime.now().minusSeconds(80));
        challengeIsValid();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendLoginOtp(resendRequest(TOKEN)));

        verifyNoInteractions(mailSender);
    }

    @Test
    void resendLoginOtp_sendsANewCodeAndCountsIt_whenTheWaitIsOver() {
        mailWillBeSent();
        activeLogin();
        admin.setLoginOtpLastSentAt(LocalDateTime.now().minusSeconds(61));
        challengeIsValid();

        service.resendLoginOtp(resendRequest(TOKEN));

        assertEquals(1, admin.getLoginOtpResendCount());
        assertEquals("HASH", admin.getLoginOtpHash());
        assertTrue(admin.getLoginOtpExpiry().isAfter(LocalDateTime.now().plusMinutes(9)));
        verify(mailSender).send(any(MimeMessage.class));
        verify(admins).save(admin);
    }

    @Test
    void resendLoginOtp_doesNotCountTheResend_whenTheEmailCannotBeSent() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        activeLogin();
        admin.setLoginOtpLastSentAt(LocalDateTime.now().minusSeconds(61));
        challengeIsValid();

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.resendLoginOtp(resendRequest(TOKEN)));

        assertEquals(0, admin.getLoginOtpResendCount());
        verify(admins, never()).save(any());
    }
}