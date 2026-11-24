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

    /*
     * Checks: The profile shows the admin's name and email.
     * How:    Mockito fake repository. getProfile returns "Admin" and the admin email.
     */
    @Test
    void getProfile_returnsTheNameAndEmail() {
        adminExists();

        AdminProfileResponse profile = service.getProfile(CURRENT);

        assertEquals("Admin", profile.fullName());
        assertEquals(CURRENT, profile.email());
    }

    /*
     * Checks: An unknown admin gives not found.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void getProfile_throwsError_whenAdminDoesNotExist() {
        when(admins.findByEmail(CURRENT)).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getProfile(CURRENT));
    }

    // ---------------------------------------------------------- request change

    /*
     * Checks: Changing to the current email is refused.
     * How:    It throws BusinessRuleException and no mail is sent.
     */
    @Test
    void requestEmailChange_throwsError_whenNewEmailIsTheCurrentEmail() {
        adminExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.requestEmailChange(changeRequest(CURRENT), CURRENT));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: An email used by another admin is refused.
     * How:    existsByEmail is true. It throws ConflictException and no mail is sent.
     */
    @Test
    void requestEmailChange_throwsError_whenAnotherAdminUsesTheEmail() {
        adminExists();
        when(admins.existsByEmail(NEW)).thenReturn(true);

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.requestEmailChange(changeRequest(NEW), CURRENT));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: An email used by a client is refused.
     * How:    The client repository says it exists. It throws ConflictException and no mail is sent.
     */
    @Test
    void requestEmailChange_throwsError_whenAClientUsesTheEmail() {
        adminExists();
        when(admins.existsByEmail(NEW)).thenReturn(false);
        when(clients.existsByEmail(NEW)).thenReturn(true);

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.requestEmailChange(changeRequest(NEW), CURRENT));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: Asking again for the same address is refused.
     * How:    A change is already pending for that address. The message says "already been sent" and
     *   no mail is sent.
     */
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

    /*
     * Checks: A code is emailed to the new address, and the login email stays the same.
     * How:    We pass " New@Test.com ". The login email is unchanged, the pending email is
     *   new@test.com, the hash is stored, counters are 0, the expiry is about 10 minutes away,
     *   one mail is sent and the admin is saved.
     */
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

    /*
     * Checks: Nothing is saved when the email cannot be sent.
     * How:    The fake sender throws. It throws ServiceUnavailableException and save() is never
     *   called.
     */
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

    /*
     * Checks: Verifying without a request is refused.
     * How:    The message contains "no active email change".
     */
    @Test
    void verifyEmailChange_throwsError_whenNoChangeWasRequested() {
        adminExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verifyEmailChange(codeRequest("123456"), CURRENT));

        assertTrue(error.getMessage().contains("no active email change"));
    }

    /*
     * Checks: An expired code is refused.
     * How:    The expiry is one minute ago. The message says expired.
     */
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

    /*
     * Checks: After five used attempts the change is cancelled.
     * How:    The pending email and hash become null, the login email is unchanged, and the admin is
     *   saved.
     */
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

    /*
     * Checks: A wrong code is counted and the message says how many tries are left.
     * How:    The message says "4 attempt(s) remaining", the attempts are 1, the pending email stays,
     *   and the admin is saved.
     */
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

    /*
     * Checks: The fifth wrong code cancels the change.
     * How:    With 4 attempts already, a wrong code gives the message "current email address has not
     *   changed". The pending email is null and the login email is unchanged.
     */
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

    /*
     * Checks: The right code replaces the login email.
     * How:    The encoder says it matches. The email becomes the new one, and the pending email, hash,
     *   expiry and attempts are cleared and saved.
     */
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

    /*
     * Checks: It is refused if someone took the email in the meantime.
     * How:    The code is right, but a client now uses the email. It throws ConflictException, the
     *   email is unchanged, and nothing is saved.
     */
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

    /*
     * Checks: Resend without a request is refused.
     * How:    It throws BusinessRuleException and no mail is sent.
     */
    @Test
    void resendEmailChangeOtp_throwsError_whenNoChangeWasRequested() {
        adminExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendEmailChangeOtp(CURRENT));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: Resend after five wrong attempts is refused.
     * How:    It throws BusinessRuleException and no mail is sent.
     */
    @Test
    void resendEmailChangeOtp_throwsError_whenFiveAttemptsWereAlreadyUsed() {
        pendingChange();
        admin.setEmailChangeOtpAttempts(5);
        adminExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resendEmailChangeOtp(CURRENT));

        verifyNoInteractions(mailSender);
    }

    /*
     * Checks: A fourth resend is refused.
     * How:    The resend count is 3. The message says "maximum number of resend requests".
     */
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

    /*
     * Checks: Resend within 60 seconds is refused.
     * How:    The message starts with "Please wait" and no mail is sent.
     */
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

    /*
     * Checks: After the wait, resend sends a new code and counts it.
     * How:    The last send was 61 seconds ago. The resend count is 1, the hash is updated, one mail
     *   is sent and the admin is saved.
     */
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