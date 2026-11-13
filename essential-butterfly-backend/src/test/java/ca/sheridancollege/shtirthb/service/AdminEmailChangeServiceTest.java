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
import ca.sheridancollege.shtirthb.dto.AdminEmailChangeRequest;
import ca.sheridancollege.shtirthb.dto.AdminEmailOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminProfileResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/*
 * Tests for the admin profile and the "change my email" flow in
 * AdminEmailSecurityService. The new address must be confirmed with a
 * 6-digit code before the login email is replaced.
 */
@ExtendWith(MockitoExtension.class)
class AdminEmailChangeServiceTest {

    private static final String CURRENT = "admin@test.com";
    private static final String NEW = "new@test.com";

    @Mock AdminUserRepository admins;
    @Mock ClientUserRepository clients;
    @Mock PasswordEncoder encoder;
    @Mock JavaMailSender mailSender;

    private AdminEmailSecurityService service;
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getMail().setFromAddress("noreply@butterfly.test");
        service = new AdminEmailSecurityService(admins, clients, encoder, mailSender, properties);
        admin = new AdminUser("Admin", CURRENT, "ENCODED");
    }

    private void adminExists() {
        when(admins.findByEmail(CURRENT)).thenReturn(Optional.of(admin));
    }

    private void newEmailIsFree() {
        when(admins.existsByEmail(NEW)).thenReturn(false);
        when(clients.existsByEmail(NEW)).thenReturn(false);
    }

    private void mailWillBeSent() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        when(encoder.encode(any())).thenReturn("HASH");
    }

    private AdminEmailChangeRequest changeRequest(String email) {
        AdminEmailChangeRequest request = new AdminEmailChangeRequest();
        request.setEmail(email);
        return request;
    }

    private AdminEmailOtpRequest codeRequest(String code) {
        AdminEmailOtpRequest request = new AdminEmailOtpRequest();
        request.setCode(code);
        return request;
    }

    /** The admin asked to change to NEW, and the code "OH" is valid for 10 minutes. */
    private void pendingChange() {
        admin.setPendingEmail(NEW);
        admin.setEmailChangeOtpHash("OH");
        admin.setEmailChangeOtpExpiry(LocalDateTime.now().plusMinutes(10));
        admin.setEmailChangeOtpLastSentAt(LocalDateTime.now());
    }

    // ---------------------------------------------------------------- profile

    @Test
    void getProfile_returnsTheNameAndEmail() {
        adminExists();

        AdminProfileResponse profile = service.getProfile(CURRENT);

        assertEquals("Admin", profile.fullName());
        assertEquals(CURRENT, profile.email());
    }

    @Test
    void getProfile_throwsError_whenAdminDoesNotExist() {
        when(admins.findByEmail(CURRENT)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getProfile(CURRENT));
    }

    // ---------------------------------------------------------- request change

    @Test
    void requestEmailChange_throwsError_whenNewEmailIsTheCurrentEmail() {
        adminExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.requestEmailChange(changeRequest(CURRENT), CURRENT));

        verifyNoInteractions(mailSender);
    }

    @Test
    void requestEmailChange_throwsError_whenAnotherAdminUsesTheEmail() {
        adminExists();
        when(admins.existsByEmail(NEW)).thenReturn(true);

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.requestEmailChange(changeRequest(NEW), CURRENT));

        verifyNoInteractions(mailSender);
    }

    @Test
    void requestEmailChange_throwsError_whenAClientUsesTheEmail() {
        adminExists();
        when(admins.existsByEmail(NEW)).thenReturn(false);
        when(clients.existsByEmail(NEW)).thenReturn(true);

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.requestEmailChange(changeRequest(NEW), CURRENT));

        verifyNoInteractions(mailSender);
    }

    @Test
    void requestEmailChange_throwsError_whenACodeWasAlreadySentToThatAddress() {
        pendingChange();
        adminExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.requestEmailChange(changeRequest(NEW), CURRENT));

        assertTrue(error.getMessage().contains("already been sent"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void requestEmailChange_emailsACodeToTheNewAddress_withoutChangingTheLoginEmail() {
        mailWillBeSent();
        adminExists();
        newEmailIsFree();

        service.requestEmailChange(changeRequest("  New@Test.com "), CURRENT);

        assertEquals(CURRENT, admin.getEmail());
        assertEquals(NEW, admin.getPendingEmail());
        assertEquals("HASH", admin.getEmailChangeOtpHash());
        assertEquals(0, admin.getEmailChangeOtpAttempts());
        assertEquals(0, admin.getEmailChangeOtpResendCount());
        assertNotNull(admin.getEmailChangeOtpLastSentAt());
        assertTrue(admin.getEmailChangeOtpExpiry().isAfter(LocalDateTime.now().plusMinutes(9)));
        verify(mailSender).send(any(MimeMessage.class));
        verify(admins).save(admin);
    }

    @Test
    void requestEmailChange_doesNotSaveAnything_whenTheEmailCannotBeSent() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        adminExists();
        newEmailIsFree();

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.requestEmailChange(changeRequest(NEW), CURRENT));

        verify(admins, never()).save(any());
    }

    // ------------------------------------------------------------ verify change

    @Test
    void verifyEmailChange_throwsError_whenNoChangeWasRequested() {
        adminExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyEmailChange(codeRequest("123456"), CURRENT));

        assertTrue(error.getMessage().contains("no active email change"));
    }

    @Test
    void verifyEmailChange_throwsError_whenTheCodeHasExpired() {
        pendingChange();
        admin.setEmailChangeOtpExpiry(LocalDateTime.now().minusMinutes(1));
        adminExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyEmailChange(codeRequest("123456"), CURRENT));

        assertTrue(error.getMessage().contains("expired"));
    }

    @Test
    void verifyEmailChange_cancelsTheChange_whenFiveAttemptsWereAlreadyUsed() {
        pendingChange();
        admin.setEmailChangeOtpAttempts(5);
        adminExists();

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verifyEmailChange(codeRequest("123456"), CURRENT));

        assertNull(admin.getPendingEmail());
        assertNull(admin.getEmailChangeOtpHash());
        assertEquals(CURRENT, admin.getEmail());
        verify(admins).save(admin);
    }

    @Test
    void verifyEmailChange_countsTheWrongCodeAndSaysHowManyAttemptsAreLeft() {
        pendingChange();
        adminExists();
        when(encoder.matches("000000", "OH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyEmailChange(codeRequest("000000"), CURRENT));

        assertTrue(error.getMessage().contains("4 attempt(s) remaining"));
        assertEquals(1, admin.getEmailChangeOtpAttempts());
        assertEquals(NEW, admin.getPendingEmail());
        verify(admins).save(admin);
    }

    @Test
    void verifyEmailChange_cancelsTheChange_onTheFifthWrongCode() {
        pendingChange();
        admin.setEmailChangeOtpAttempts(4);
        adminExists();
        when(encoder.matches("000000", "OH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyEmailChange(codeRequest("000000"), CURRENT));

        assertTrue(error.getMessage().contains("current email address has not changed"));
        assertNull(admin.getPendingEmail());
        assertEquals(CURRENT, admin.getEmail());
        verify(admins).save(admin);
    }

    @Test
    void verifyEmailChange_replacesTheLoginEmail_whenCodeIsCorrect() {
        pendingChange();
        admin.setEmailChangeOtpAttempts(2);
        adminExists();
        when(clients.existsByEmail(NEW)).thenReturn(false);
        when(encoder.matches("123456", "OH")).thenReturn(true);

        AdminUser result = service.verifyEmailChange(codeRequest("123456"), CURRENT);

        assertSame(admin, result);
        assertEquals(NEW, admin.getEmail());
        assertNull(admin.getPendingEmail());
        assertNull(admin.getEmailChangeOtpHash());
        assertNull(admin.getEmailChangeOtpExpiry());
        assertEquals(0, admin.getEmailChangeOtpAttempts());
        verify(admins).save(admin);
    }

    @Test
    void verifyEmailChange_throwsError_whenSomeoneTookTheEmailInTheMeantime() {
        pendingChange();
        adminExists();
        when(encoder.matches("123456", "OH")).thenReturn(true);
        when(clients.existsByEmail(NEW)).thenReturn(true);

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.verifyEmailChange(codeRequest("123456"), CURRENT));

        assertEquals(CURRENT, admin.getEmail());
        verify(admins, never()).save(any());
    }

    // ----------------------------------------------------------------- resend

    @Test
    void resendEmailChangeOtp_throwsError_whenNoChangeWasRequested() {
        adminExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendEmailChangeOtp(CURRENT));

        verifyNoInteractions(mailSender);
    }

    @Test
    void resendEmailChangeOtp_throwsError_whenFiveAttemptsWereAlreadyUsed() {
        pendingChange();
        admin.setEmailChangeOtpAttempts(5);
        adminExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendEmailChangeOtp(CURRENT));

        verifyNoInteractions(mailSender);
    }

    @Test
    void resendEmailChangeOtp_throwsError_afterTheThirdResend() {
        pendingChange();
        admin.setEmailChangeOtpResendCount(3);
        admin.setEmailChangeOtpLastSentAt(LocalDateTime.now().minusHours(1));
        adminExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resendEmailChangeOtp(CURRENT));

        assertTrue(error.getMessage().contains("maximum number of resend requests"));
    }

    @Test
    void resendEmailChangeOtp_throwsError_whenTheFirstWaitOf60SecondsIsNotOver() {
        pendingChange();
        admin.setEmailChangeOtpLastSentAt(LocalDateTime.now().minusSeconds(30));
        adminExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resendEmailChangeOtp(CURRENT));

        assertTrue(error.getMessage().startsWith("Please wait"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void resendEmailChangeOtp_sendsANewCodeAndCountsIt_whenTheWaitIsOver() {
        mailWillBeSent();
        pendingChange();
        admin.setEmailChangeOtpLastSentAt(LocalDateTime.now().minusSeconds(61));
        adminExists();

        service.resendEmailChangeOtp(CURRENT);

        assertEquals(1, admin.getEmailChangeOtpResendCount());
        assertEquals("HASH", admin.getEmailChangeOtpHash());
        verify(mailSender).send(any(MimeMessage.class));
        verify(admins).save(admin);
    }
}