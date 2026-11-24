package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ca.sheridancollege.shtirthb.dto.PhoneNumberRequest;
import ca.sheridancollege.shtirthb.dto.PhoneVerificationStatusResponse;
import ca.sheridancollege.shtirthb.dto.VerifyPhoneRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/*
 * Unit tests for PhoneVerificationService.
 *
 * The service sends and checks codes through Twilio Verify. These tests never
 * call the real Twilio. Instead, a small fake Twilio server runs on this
 * computer (127.0.0.1) and the service is pointed at it. This lets us test
 * every rule (attempts, resends, waiting time, expiry, errors) and also check
 * exactly what the service sends to Twilio, without sending any real SMS.
 */
@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    private static final String EMAIL = "jane@mail.com";
    private static final String PHONE = "+14165551234";
    private static final String OLD_PHONE = "+14165550000";

    private static final String SEND_PATH = "/v2/Services/VA123/Verifications";
    private static final String CHECK_PATH = "/v2/Services/VA123/VerificationCheck";

    @Mock
    private ClientUserRepository users;

    private FakeTwilio twilio;
    private PhoneVerificationService service;
    private ClientUser user;

    @BeforeEach
    void setUp() throws IOException {
        twilio = new FakeTwilio();
        service = serviceFor("AC123", "secret-token", "VA123", twilio.baseUrl());
        user = new ClientUser("Jane Doe", EMAIL, "ENCODED");
    }

    @AfterEach
    void tearDown() {
        twilio.stop();
    }

    // ------------------------------------------------------------- helpers

    private PhoneVerificationService serviceFor(
            String sid, String token, String verifySid, String baseUrl) {
        return new PhoneVerificationService(users, sid, token, verifySid, baseUrl);
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

    /** The user asked for a code for PHONE and it is valid for 10 minutes. */
    private void pendingVerification() {
        user.setPendingPhoneNumber(PHONE);
        user.setPhoneVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));
        user.setPhoneVerificationLastSentAt(LocalDateTime.now());
    }

    /** Twilio answers "approved" for the check request. */
    private void twilioApproves() {
        twilio.checkStatus = 200;
        twilio.checkBody = "{\"status\":\"approved\"}";
    }

    // ------------------------------------------------------------- status

    /*
     * Checks: The status shows the verified and the pending number.
     * How:    Unit test with a small fake Twilio web server (no real SMS). The user has an old
     *   verified number and a pending one. The status shows phoneVerified true and both numbers.
     */
    @Test
    void getStatus_returnsTheVerifiedAndPendingNumbers() {
        user.setPhoneNumber(OLD_PHONE);
        user.setPendingPhoneNumber(PHONE);
        userExists();

        PhoneVerificationStatusResponse status = service.getStatus(EMAIL);

        assertTrue(status.phoneVerified());
        assertEquals(OLD_PHONE, status.phoneNumber());
        assertEquals(PHONE, status.pendingPhoneNumber());
    }

    /*
     * Checks: Capital letters and spaces in the email are ignored.
     * How:    A messy email finds the user. For a user with no phone, phoneVerified is false and the
     *   number is null.
     */
    @Test
    void getStatus_ignoresCapitalLettersAndSpacesInTheEmail() {
        userExists();

        PhoneVerificationStatusResponse status =
                service.getStatus("  JANE@Mail.COM ");

        assertFalse(status.phoneVerified());
        assertNull(status.phoneNumber());
    }

    /*
     * Checks: The status for an unknown user gives not found.
     * How:    It throws ResourceNotFoundException.
     */
    @Test
    void getStatus_throwsError_whenUserDoesNotExist() {
        when(users.findByEmail("missing@mail.com")).thenReturn(Optional.empty());

        assertThrows(AppExceptions.ResourceNotFoundException.class,
                () -> service.getStatus("missing@mail.com"));
    }

    // ----------------------------------------------------- startVerification

    /*
     * Checks: Starting verification for an already verified number is refused.
     * How:    It throws BusinessRuleException, the fake Twilio gets 0 requests and nothing is saved.
     */
    @Test
    void startVerification_throwsError_whenThisNumberIsAlreadyVerified() {
        user.setPhoneNumber(PHONE);
        userExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        assertEquals(0, twilio.requests.size());
        verify(users, never()).save(any());
    }

    /*
     * Checks: Starting again for the same pending number is refused.
     * How:    The message says "already been sent" and Twilio gets no request.
     */
    @Test
    void startVerification_throwsError_whenACodeWasAlreadySentToThisNumber() {
        pendingVerification();
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        assertTrue(error.getMessage().contains("already been sent"));
        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: A number with five wrong attempts cannot start again.
     * How:    The message says "maximum number of incorrect attempts" and Twilio gets no request.
     */
    @Test
    void startVerification_throwsError_whenFiveWrongCodesWereUsedForThisNumber() {
        user.setPendingPhoneNumber(PHONE);
        user.setPhoneVerificationAttempts(5);
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        assertTrue(error.getMessage().contains("maximum number of incorrect attempts"));
        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: A number that belongs to another account is refused.
     * How:    It throws ConflictException, Twilio gets no request and nothing is saved.
     */
    @Test
    void startVerification_throwsError_whenTheNumberBelongsToAnotherAccount() {
        userExists();
        when(users.existsByPhoneNumberAndIdNot(eq(PHONE), any())).thenReturn(true);

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        assertEquals(0, twilio.requests.size());
        verify(users, never()).save(any());
    }

    /*
     * Checks: The number is cleaned and Twilio is asked to send the code.
     * How:    The user gets the pending number, a last-sent time, an expiry, counters 0, and no code
     *   hash (Twilio keeps the code). The fake Twilio got one request with the right path and
     *   the body "To=%2B14165551234&Channel=sms".
     */
    @Test
    void startVerification_cleansTheNumberAndAsksTwilioToSendTheCode() {
        userExists();

        service.startVerification(phoneRequest("+1 (416) 555-1234"), EMAIL);

        // The user is saved with the cleaned number and a fresh timer.
        assertEquals(PHONE, user.getPendingPhoneNumber());
        assertNotNull(user.getPhoneVerificationLastSentAt());
        assertTrue(user.getPhoneVerificationCodeExpiry()
                .isAfter(LocalDateTime.now().plusMinutes(9)));
        assertEquals(0, user.getPhoneVerificationAttempts());
        assertEquals(0, user.getPhoneVerificationResendCount());
        assertNull(user.getPhoneVerificationCodeHash());
        verify(users).save(user);

        // Exactly one request went to Twilio, asking for an SMS.
        assertEquals(1, twilio.requests.size());
        FakeTwilio.Request sent = twilio.requests.get(0);
        assertEquals(SEND_PATH, sent.path);
        assertEquals("To=%2B14165551234&Channel=sms", sent.body);
    }

    /*
     * Checks: The Twilio login is sent as a form request.
     * How:    The fake Twilio checks the Authorization header and the content type
     *   application/x-www-form-urlencoded.
     */
    @Test
    void startVerification_sendsTheTwilioLoginAsAForm() {
        userExists();

        service.startVerification(phoneRequest(PHONE), EMAIL);

        FakeTwilio.Request sent = twilio.requests.get(0);
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                "AC123:secret-token".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, sent.authorization);
        assertEquals("application/x-www-form-urlencoded", sent.contentType);
    }

    /*
     * Checks: A new number restarts the counters.
     * How:    The user had 3 attempts and 2 resends for another number. After starting, the pending
     *   number is new and both counters are 0.
     */
    @Test
    void startVerification_restartsTheCounters_forANewNumber() {
        user.setPendingPhoneNumber("+14165559999");
        user.setPhoneVerificationAttempts(3);
        user.setPhoneVerificationResendCount(2);
        user.setPhoneVerificationLastSentAt(LocalDateTime.now().minusMinutes(5));
        userExists();

        service.startVerification(phoneRequest(PHONE), EMAIL);

        assertEquals(PHONE, user.getPendingPhoneNumber());
        assertEquals(0, user.getPhoneVerificationAttempts());
        assertEquals(0, user.getPhoneVerificationResendCount());
    }

    /*
     * Checks: The old verified number is kept while a new one is pending.
     * How:    The old number is unchanged and the new number is only pending.
     */
    @Test
    void startVerification_keepsTheOldVerifiedNumber_whileANewOneIsPending() {
        user.setPhoneNumber(OLD_PHONE);
        userExists();

        service.startVerification(phoneRequest(PHONE), EMAIL);

        assertEquals(OLD_PHONE, user.getPhoneNumber());
        assertEquals(PHONE, user.getPendingPhoneNumber());
    }

    /*
     * Checks: If Twilio rejects the number, nothing is saved.
     * How:    It throws ServiceUnavailableException ("could not send the verification text"). The
     *   last-sent time is null and save() is never called.
     */
    @Test
    void startVerification_throwsError_andSavesNothing_whenTwilioRejectsTheNumber() {
        twilio.sendStatus = 400;
        twilio.sendBody = "{\"message\":\"Invalid parameter\"}";
        userExists();

        AppExceptions.ServiceUnavailableException error = assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        assertTrue(error.getMessage().contains("could not send the verification text"));
        assertNull(user.getPhoneVerificationLastSentAt());
        verify(users, never()).save(any());
    }

    /*
     * Checks: If Twilio is not configured, a clear error is returned.
     * How:    The message says "not configured", no request is sent and nothing is saved.
     */
    @Test
    void startVerification_throwsError_whenTwilioIsNotConfigured() {
        PhoneVerificationService unconfigured =
                serviceFor("", "", "", twilio.baseUrl());
        userExists();

        AppExceptions.ServiceUnavailableException error = assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> unconfigured.startVerification(phoneRequest(PHONE), EMAIL));

        assertTrue(error.getMessage().contains("not configured"));
        assertEquals(0, twilio.requests.size());
        assertNull(user.getPhoneVerificationCodeHash());
        verify(users, never()).save(any());
    }

    /*
     * Checks: If Twilio cannot be reached, a clear error is returned.
     * How:    The message says "could not reach" and nothing is saved.
     */
    @Test
    void startVerification_throwsError_whenTwilioCannotBeReached() {
        twilio.stop();
        userExists();

        AppExceptions.ServiceUnavailableException error = assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> service.startVerification(phoneRequest(PHONE), EMAIL));

        assertTrue(error.getMessage().contains("could not reach"));
        verify(users, never()).save(any());
    }

    // -------------------------------------------------------------- verify

    /*
     * Checks: Verifying without starting is refused.
     * How:    The message contains "no active phone verification" and Twilio gets no request.
     */
    @Test
    void verify_throwsError_whenNoVerificationWasStarted() {
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("123456"), EMAIL));

        assertTrue(error.getMessage().contains("no active phone verification"));
        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: Verifying after five used attempts is refused.
     * How:    The message says "maximum number of incorrect attempts" and Twilio gets no request.
     */
    @Test
    void verify_throwsError_whenFiveAttemptsWereAlreadyUsed() {
        pendingVerification();
        user.setPhoneVerificationAttempts(5);
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("123456"), EMAIL));

        assertTrue(error.getMessage().contains("maximum number of incorrect attempts"));
        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: An expired code is refused.
     * How:    The expiry is one minute ago. The message says expired and Twilio gets no request.
     */
    @Test
    void verify_throwsError_whenTheCodeHasExpired() {
        pendingVerification();
        user.setPhoneVerificationCodeExpiry(LocalDateTime.now().minusMinutes(1));
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("123456"), EMAIL));

        assertTrue(error.getMessage().contains("expired"));
        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: A wrong code is counted and the message says how many tries are left.
     * How:    The fake Twilio answers pending. The message says "4 attempt(s) remaining", attempts are
     *   1, the phone number is still empty and the user is saved.
     */
    @Test
    void verify_countsTheWrongCodeAndSaysHowManyAttemptsAreLeft() {
        pendingVerification();
        twilio.checkBody = "{\"status\":\"pending\"}";
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("000000"), EMAIL));

        assertTrue(error.getMessage().contains("4 attempt(s) remaining"));
        assertEquals(1, user.getPhoneVerificationAttempts());
        assertNull(user.getPhoneNumber());
        verify(users).save(user);
    }

    /*
     * Checks: The fifth wrong code stops the verification.
     * How:    With 4 attempts, a wrong code gives "maximum of five incorrect attempts". The attempts
     *   are 5 and the phone number is still empty.
     */
    @Test
    void verify_stopsOnTheFifthWrongCode() {
        pendingVerification();
        user.setPhoneVerificationAttempts(4);
        twilio.checkBody = "{\"status\":\"pending\"}";
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("000000"), EMAIL));

        assertTrue(error.getMessage().contains("maximum of five incorrect attempts"));
        assertEquals(5, user.getPhoneVerificationAttempts());
        assertNull(user.getPhoneNumber());
    }

    /*
     * Checks: When Twilio approves the code, the number is saved and the temporary data is cleared.
     * How:    The user gets the verified number, no pending number, counters 0, and no last-sent time
     *   or expiry. Twilio got a check request with the body "To=%2B14165551234&Code=123456".
     */
    @Test
    void verify_savesTheNumberAndClearsEverything_whenTwilioApprovesTheCode() {
        pendingVerification();
        user.setPhoneVerificationAttempts(2);
        user.setPhoneVerificationResendCount(1);
        twilioApproves();
        userExists();

        service.verify(codeRequest("123456"), EMAIL);

        assertEquals(PHONE, user.getPhoneNumber());
        assertTrue(user.isPhoneVerified());
        assertNull(user.getPendingPhoneNumber());
        assertEquals(0, user.getPhoneVerificationAttempts());
        assertEquals(0, user.getPhoneVerificationResendCount());
        assertNull(user.getPhoneVerificationLastSentAt());
        assertNull(user.getPhoneVerificationCodeExpiry());
        verify(users).save(user);

        FakeTwilio.Request checked = twilio.requests.get(0);
        assertEquals(CHECK_PATH, checked.path);
        assertEquals("To=%2B14165551234&Code=123456", checked.body);
    }

    /*
     * Checks: The old verified number is replaced only after the right code.
     * How:    After an approved check, the number is the new one.
     */
    @Test
    void verify_replacesTheOldVerifiedNumber_onlyAfterTheCorrectCode() {
        user.setPhoneNumber(OLD_PHONE);
        pendingVerification();
        twilioApproves();
        userExists();

        service.verify(codeRequest("123456"), EMAIL);

        assertEquals(PHONE, user.getPhoneNumber());
    }

    /*
     * Checks: The old verified number is kept when the code is wrong.
     * How:    After a wrong code, the old number is unchanged and the new one is still pending.
     */
    @Test
    void verify_keepsTheOldVerifiedNumber_whenTheCodeIsWrong() {
        user.setPhoneNumber(OLD_PHONE);
        pendingVerification();
        twilio.checkBody = "{\"status\":\"pending\"}";
        userExists();

        assertThrows(AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("000000"), EMAIL));

        assertEquals(OLD_PHONE, user.getPhoneNumber());
        assertEquals(PHONE, user.getPendingPhoneNumber());
    }

    /*
     * Checks: If Twilio has no open verification, it says expired and does not count an attempt.
     * How:    The message says expired and the attempts stay 0.
     */
    @Test
    void verify_throwsExpired_andDoesNotCountAnAttempt_whenTwilioHasNoOpenVerification() {
        pendingVerification();
        twilio.checkStatus = 404;
        twilio.checkBody = "{\"message\":\"not found\"}";
        userExists();

        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> service.verify(codeRequest("123456"), EMAIL));

        assertTrue(error.getMessage().contains("expired"));
        assertEquals(0, user.getPhoneVerificationAttempts());
    }

    /*
     * Checks: If Twilio fails, a service error is returned and no attempt is counted.
     * How:    The message says "could not check the verification code", attempts stay 0 and the phone
     *   number is empty.
     */
    @Test
    void verify_throwsError_andDoesNotCountAnAttempt_whenTwilioFails() {
        pendingVerification();
        twilio.checkStatus = 500;
        twilio.checkBody = "{}";
        userExists();

        AppExceptions.ServiceUnavailableException error = assertThrows(
                AppExceptions.ServiceUnavailableException.class,
                () -> service.verify(codeRequest("123456"), EMAIL));

        assertTrue(error.getMessage().contains("could not check the verification code"));
        assertEquals(0, user.getPhoneVerificationAttempts());
        assertNull(user.getPhoneNumber());
    }

    /*
     * Checks: It is refused if another account took the number meanwhile.
     * How:    The code is approved, but the number now belongs to another user. It throws
     *   ConflictException and nothing is saved.
     */
    @Test
    void verify_throwsError_whenAnotherAccountTookTheNumberInTheMeantime() {
        pendingVerification();
        twilioApproves();
        userExists();
        when(users.existsByPhoneNumberAndIdNot(eq(PHONE), any())).thenReturn(true);

        assertThrows(AppExceptions.ConflictException.class,
                () -> service.verify(codeRequest("123456"), EMAIL));

        assertNull(user.getPhoneNumber());
        verify(users, never()).save(any());
    }

    // -------------------------------------------------------------- resend

    /*
     * Checks: Resend without starting is refused.
     * How:    It throws BusinessRuleException and Twilio gets no request.
     */
    @Test
    void resend_throwsError_whenNoVerificationWasStarted() {
        userExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL));

        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: Resend before any code was sent is refused.
     * How:    A pending number exists but no last-sent time. It throws BusinessRuleException.
     */
    @Test
    void resend_throwsError_whenNoCodeWasSentYet() {
        user.setPendingPhoneNumber(PHONE);
        userExists();

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL));

        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: Resend after five wrong attempts is refused.
     * How:    The message says "maximum number of incorrect attempts".
     */
    @Test
    void resend_throwsError_whenFiveAttemptsWereAlreadyUsed() {
        pendingVerification();
        user.setPhoneVerificationAttempts(5);
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL));

        assertTrue(error.getMessage().contains("maximum number of incorrect attempts"));
    }

    /*
     * Checks: A fourth resend is refused.
     * How:    The message says "maximum number of resend requests" and Twilio gets no request.
     */
    @Test
    void resend_throwsError_afterTheThirdResend() {
        pendingVerification();
        user.setPhoneVerificationResendCount(3);
        user.setPhoneVerificationLastSentAt(LocalDateTime.now().minusMinutes(10));
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL));

        assertTrue(error.getMessage().contains("maximum number of resend requests"));
        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: Resend within 60 seconds is refused.
     * How:    The message starts with "Please wait" and Twilio gets no request.
     */
    @Test
    void resend_throwsError_whenTheFirstWaitOf60SecondsIsNotOver() {
        pendingVerification();
        user.setPhoneVerificationLastSentAt(LocalDateTime.now().minusSeconds(30));
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL));

        assertTrue(error.getMessage().startsWith("Please wait"));
        assertEquals(0, twilio.requests.size());
    }

    /*
     * Checks: The second resend needs a longer wait.
     * How:    With 1 resend done and the last send 70 seconds ago, the message starts with "Please
     *   wait".
     */
    @Test
    void resend_waitsLongerForTheSecondResend() {
        pendingVerification();
        user.setPhoneVerificationResendCount(1);
        // 70 seconds is enough for the first resend (60 s) but not the second (90 s).
        user.setPhoneVerificationLastSentAt(LocalDateTime.now().minusSeconds(70));
        userExists();

        AppExceptions.BusinessRuleException error = assertThrows(
                AppExceptions.BusinessRuleException.class,
                () -> service.resend(EMAIL));

        assertTrue(error.getMessage().startsWith("Please wait"));
    }

    /*
     * Checks: After the wait, resend asks Twilio for a new code and counts it.
     * How:    The resend count is 1. The fake Twilio got one send request with the right body, and the
     *   user was saved.
     */
    @Test
    void resend_sendsANewCodeAndCountsIt_whenTheWaitIsOver() {
        pendingVerification();
        user.setPhoneVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        userExists();

        service.resend(EMAIL);

        assertEquals(1, user.getPhoneVerificationResendCount());
        assertEquals(1, twilio.requests.size());
        assertEquals(SEND_PATH, twilio.requests.get(0).path);
        assertEquals("To=%2B14165551234&Channel=sms", twilio.requests.get(0).body);
        verify(users, atLeastOnce()).save(user);
    }

    /*
     * Checks: A failed resend is not counted.
     * How:    Twilio fails. It throws ServiceUnavailableException and the resend count stays 0.
     */
    @Test
    void resend_doesNotCountTheResend_whenTheTextCannotBeSent() {
        pendingVerification();
        user.setPhoneVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        twilio.sendStatus = 500;
        twilio.sendBody = "{}";
        userExists();

        assertThrows(AppExceptions.ServiceUnavailableException.class,
                () -> service.resend(EMAIL));

        assertEquals(0, user.getPhoneVerificationResendCount());
    }

    // --------------------------------------------------------- fake Twilio

    /*
     * A tiny web server that pretends to be Twilio Verify.
     * It answers the "send" and the "check" requests with the status and body
     * set by the test, and remembers every request it receives.
     */
    private static class FakeTwilio {

        static class Request {
            String path;
            String body;
            String authorization;
            String contentType;
        }

        final List<Request> requests = new ArrayList<>();

        volatile int sendStatus = 201;
        volatile String sendBody = "{\"status\":\"pending\"}";
        volatile int checkStatus = 200;
        volatile String checkBody = "{\"status\":\"pending\"}";

        private final HttpServer server;

        FakeTwilio() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private boolean stopped = false;

        void stop() {
            if (!stopped) {
                stopped = true;
                server.stop(0);
            }
        }

        private void handle(HttpExchange exchange) throws IOException {
            Request request = new Request();
            request.path = exchange.getRequestURI().getPath();
            request.body = new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            request.contentType = exchange.getRequestHeaders().getFirst("Content-Type");

            synchronized (requests) {
                requests.add(request);
            }

            boolean isCheck = request.path.endsWith("/VerificationCheck");
            int status = isCheck ? checkStatus : sendStatus;
            byte[] answer = (isCheck ? checkBody : sendBody)
                    .getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        }
    }
}