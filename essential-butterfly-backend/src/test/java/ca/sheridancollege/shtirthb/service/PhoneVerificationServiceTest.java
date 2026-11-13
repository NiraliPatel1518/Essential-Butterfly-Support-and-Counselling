
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
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.dto.PhoneNumberRequest;
import ca.sheridancollege.shtirthb.dto.PhoneVerificationStatusResponse;
import ca.sheridancollege.shtirthb.dto.VerifyPhoneRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    private static final String EMAIL = "jane@mail.com";
    private static final String PHONE = "+14165551234";

    @Mock
    ClientUserRepository users;

    @Mock
    PasswordEncoder encoder;

    private PhoneVerificationService service;
    private ClientUser user;

    @BeforeEach
    void setUp() {
        service = new PhoneVerificationService(
                users,
                encoder,
                "",
                "",
                "",
                "https://api.twilio.com"
        );

        user = new ClientUser("Jane Doe", EMAIL, "ENCODED");
    }

    private void userExists() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private PhoneNumberRequest phoneRequest(String number) {
        PhoneNumberRequest request = new PhoneNumberRequest();
        request.setPhoneNumber(number);
        return request;
    }

    private VerifyPhoneRequest codeRequest(String code) {
        VerifyPhoneRequest request = new VerifyPhoneRequest();
        request.setCode(code);
        return request;
    }

    private void pendingVerification() {
        user.setPendingPhoneNumber(PHONE);
        user.setPhoneVerificationCodeHash("HASH");
        user.setPhoneVerificationCodeExpiry(
                LocalDateTime.now().plusMinutes(10));
        user.setPhoneVerificationLastSentAt(LocalDateTime.now());
    }

    // ---------------------------------------------------------
    // Status
    // ---------------------------------------------------------

    @Test
    void getStatus_returnsTheVerifiedAndPendingNumbers() {
        user.setPhoneNumber("+14165550000");
        user.setPhoneVerified(true);
        user.setPendingPhoneNumber(PHONE);
        userExists();

        PhoneVerificationStatusResponse status =
                service.getStatus(EMAIL);

        assertTrue(status.phoneVerified());
        assertEquals("+14165550000", status.phoneNumber());
        assertEquals(PHONE, status.pendingPhoneNumber());
    }

    @Test
    void getStatus_ignoresCapitalLettersAndSpacesInTheEmail() {
        userExists();

        PhoneVerificationStatusResponse status =
                service.getStatus("  Jane@Mail.COM ");

        assertFalse(status.phoneVerified());
    }

    @Test
    void getStatus_throwsError_whenUserDoesNotExist() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(
                AppExceptions.ResourceNotFoundException.class,
                () -> service.getStatus(EMAIL)
        );
    }

    // ---------------------------------------------------------
    // Start verification
    // ---------------------------------------------------------

    @Test
    void startVerification_throwsError_whenThisNumberIsAlreadyVerified() {
        user.setPhoneNumber(PHONE);
        user.setPhoneVerified(true);
        userExists();

        assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL)
        );
    }

    @Test
    void startVerification_throwsError_whenACodeWasAlreadySentToThisNumber() {
        pendingVerification();
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL)
        );

        assertTrue(error.getMessage().contains("already been sent"));
    }

    @Test
    void startVerification_throwsError_whenFiveWrongCodesWereUsedForThisNumber() {
        user.setPendingPhoneNumber(PHONE);
        user.setPhoneVerificationAttempts(5);
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL)
        );

        assertTrue(error.getMessage().contains(
                "maximum number of incorrect attempts"));
    }

    @Test
    void startVerification_cleansTheNumberAndRestartsTheCounters_forANewNumber() {
        pendingVerification();
        user.setPhoneVerificationAttempts(4);
        user.setPhoneVerificationResendCount(2);
        userExists();

        assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> service.startVerification(
                        phoneRequest("+1 (647) 555-9876"), EMAIL)
        );

        assertEquals("+16475559876", user.getPendingPhoneNumber());
        assertEquals(0, user.getPhoneVerificationAttempts());
        assertEquals(0, user.getPhoneVerificationResendCount());
        assertNull(user.getPhoneVerificationLastSentAt());
        assertNull(user.getPhoneVerificationCodeHash());
        assertNull(user.getPhoneVerificationCodeExpiry());
    }

    @Test
    void startVerification_keepsTheOldVerifiedNumber_whileANewOneIsPending() {
        user.setPhoneNumber("+14165550000");
        user.setPhoneVerified(true);
        userExists();

        assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL)
        );

        assertEquals("+14165550000", user.getPhoneNumber());
        assertTrue(user.isPhoneVerified());
        assertEquals(PHONE, user.getPendingPhoneNumber());
    }

    @Test
    void startVerification_throwsError_whenTextMessagesAreNotConfigured() {
        userExists();

        AppExceptions.ServiceUnavailableException error = assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL)
        );

        assertTrue(error.getMessage().contains("not configured"));
        verify(users, never()).save(any());
    }

    // ---------------------------------------------------------
    // Verify
    // ---------------------------------------------------------

    @Test
    void verify_throwsError_whenNoVerificationWasStarted() {
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("123456"), EMAIL)
        );

        assertTrue(error.getMessage().contains(
                "no active phone verification"));
    }

    @Test
    void verify_throwsError_whenFiveAttemptsWereAlreadyUsed() {
        pendingVerification();
        user.setPhoneVerificationAttempts(5);
        userExists();

        assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("123456"), EMAIL)
        );

        verifyNoInteractions(encoder);
    }

    @Test
    void verify_throwsError_whenTheCodeHasExpired() {
        pendingVerification();
        user.setPhoneVerificationCodeExpiry(
                LocalDateTime.now().minusMinutes(1));
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("123456"), EMAIL)
        );

        assertTrue(error.getMessage().contains("expired"));
        verifyNoInteractions(encoder);
    }

    @Test
    void verify_countsTheWrongCodeAndSaysHowManyAttemptsAreLeft() {
        pendingVerification();
        userExists();

        when(encoder.matches("000000", "HASH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("000000"), EMAIL)
        );

        assertTrue(error.getMessage().contains("4 attempt(s) remaining"));
        assertEquals(1, user.getPhoneVerificationAttempts());
        assertFalse(user.isPhoneVerified());

        verify(users).save(user);
    }

    @Test
    void verify_blocksTheNumber_onTheFifthWrongCode() {
        pendingVerification();
        user.setPhoneVerificationAttempts(4);
        userExists();

        when(encoder.matches("000000", "HASH")).thenReturn(false);

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("000000"), EMAIL)
        );

        assertTrue(error.getMessage().contains(
                "maximum of five incorrect attempts"));

        assertEquals(5, user.getPhoneVerificationAttempts());
        verify(users).save(user);
    }

    @Test
    void verify_savesTheNumberAsVerifiedAndClearsEverything_whenCodeIsCorrect() {
        pendingVerification();
        user.setPhoneVerificationAttempts(2);
        user.setPhoneVerificationResendCount(1);
        userExists();

        when(encoder.matches("123456", "HASH")).thenReturn(true);

        service.verify(codeRequest("123456"), EMAIL);

        assertTrue(user.isPhoneVerified());
        assertEquals(PHONE, user.getPhoneNumber());
        assertNull(user.getPendingPhoneNumber());
        assertNull(user.getPhoneVerificationCodeHash());
        assertNull(user.getPhoneVerificationCodeExpiry());
        assertNull(user.getPhoneVerificationLastSentAt());
        assertEquals(0, user.getPhoneVerificationAttempts());
        assertEquals(0, user.getPhoneVerificationResendCount());

        verify(users).save(user);
    }

    @Test
    void verify_replacesTheOldVerifiedNumber_onlyAfterTheCorrectCode() {
        user.setPhoneNumber("+14165550000");
        user.setPhoneVerified(true);
        pendingVerification();
        userExists();

        when(encoder.matches("123456", "HASH")).thenReturn(true);

        service.verify(codeRequest("123456"), EMAIL);

        assertEquals(PHONE, user.getPhoneNumber());
    }

    // ---------------------------------------------------------
    // Resend
    // ---------------------------------------------------------

    @Test
    void resend_throwsError_whenNoVerificationWasStarted() {
        userExists();

        assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL)
        );
    }

    @Test
    void resend_throwsError_whenNoCodeWasSentYet() {
        user.setPendingPhoneNumber(PHONE);
        userExists();

        assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL)
        );
    }

    @Test
    void resend_throwsError_whenFiveAttemptsWereAlreadyUsed() {
        pendingVerification();
        user.setPhoneVerificationAttempts(5);
        user.setPhoneVerificationLastSentAt(
                LocalDateTime.now().minusHours(1));
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL)
        );

        assertTrue(error.getMessage().contains("incorrect attempts"));
    }

    @Test
    void resend_throwsError_afterTheThirdResend() {
        pendingVerification();
        user.setPhoneVerificationResendCount(3);
        user.setPhoneVerificationLastSentAt(
                LocalDateTime.now().minusHours(1));
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL)
        );

        assertTrue(error.getMessage().contains(
                "maximum number of resend requests"));
    }

    @Test
    void resend_throwsError_whenTheFirstWaitOf60SecondsIsNotOver() {
        pendingVerification();
        user.setPhoneVerificationLastSentAt(
                LocalDateTime.now().minusSeconds(30));
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL)
        );

        assertTrue(error.getMessage().startsWith("Please wait"));
    }

    @Test
    void resend_waitsLongerForTheSecondResend() {
        pendingVerification();
        user.setPhoneVerificationResendCount(1);
        user.setPhoneVerificationLastSentAt(
                LocalDateTime.now().minusSeconds(80));
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL)
        );

        assertTrue(error.getMessage().startsWith("Please wait"));
    }

    @Test
    void resend_doesNotCountTheResend_whenTheTextCannotBeSent() {
        pendingVerification();
        user.setPhoneVerificationLastSentAt(
                LocalDateTime.now().minusSeconds(61));
        userExists();

        assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> service.resend(EMAIL)
        );

        assertEquals(0, user.getPhoneVerificationResendCount());
        verify(users, never()).save(any());
    }
}
