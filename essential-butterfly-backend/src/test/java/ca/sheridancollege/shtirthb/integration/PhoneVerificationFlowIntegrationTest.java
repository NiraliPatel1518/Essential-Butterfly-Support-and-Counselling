package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.JwtService;

/**
 * Integration tests for the phone-verification flow. A local HTTP server
 * emulates the Twilio Verify API; no real SMS or Twilio credentials are used.
 * The application still exercises its real HTTP client and never stores the
 * verification code in the database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///phone_verification_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.twilio.account-sid=AC00000000000000000000000000000000",
        "app.twilio.auth-token=test-auth-token",
        "app.twilio.verify-service-sid=VA00000000000000000000000000000000"
})
@AutoConfigureMockMvc
@Transactional
class PhoneVerificationFlowIntegrationTest {

    private static final String EMAIL = "jane@mail.com";
    private static final String NUMBER = "+14165551234";
    private static final String OTHER_NUMBER = "+16475559876";
    private static final String CODE = "123456";

    private static final HttpServer TWILIO_SERVER = startTwilioServer();

    private static HttpServer startTwilioServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v2/Services/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String response;
                int statusCode = 200;

                if (path.endsWith("/Verifications")) {
                    response = "{\"status\":\"pending\"}";
                } else if (path.endsWith("/VerificationCheck")) {
                    response = body.contains("Code=123456")
                            ? "{\"status\":\"approved\"}"
                            : "{\"status\":\"pending\"}";
                } else {
                    statusCode = 404;
                    response = "{\"message\":\"not found\"}";
                }

                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(statusCode, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void twilioProperties(DynamicPropertyRegistry registry) {
        registry.add("app.twilio.verify-base-url",
                () -> "http://127.0.0.1:" + TWILIO_SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopTwilioServer() {
        TWILIO_SERVER.stop(0);
    }

    @Autowired MockMvc mvc;
    @Autowired ClientUserRepository clients;
    @Autowired JwtService jwt;

    private ClientUser jane;
    private String token;

    @BeforeEach
    void setUp() {
        jane = clients.save(new ClientUser("Jane Lee", EMAIL, "hash"));
        token = "Bearer " + jwt.generateToken(EMAIL, "CLIENT");
    }

    private ResultMatcher messageContains(String text) {
        return result -> assertTrue(
                result.getResponse().getContentAsString().contains(text),
                result.getResponse().getContentAsString());
    }

    private ResultActions phoneStatus() throws Exception {
        return mvc.perform(get("/api/phone-verification/status")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions send(String number) throws Exception {
        return mvc.perform(post("/api/phone-verification/send")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"phoneNumber\":\"%s\"}", number)));
    }

    private ResultActions verify(String code) throws Exception {
        return mvc.perform(post("/api/phone-verification/verify")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"code\":\"%s\"}", code)));
    }

    private ResultActions resend() throws Exception {
        return mvc.perform(post("/api/phone-verification/resend")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    /** Starts a real service request against the local fake Twilio endpoint. */
    private void codeWasSentTo(String number) throws Exception {
        send(number).andExpect(status().isOk());
        jane = reload();
        assertEquals(number, jane.getPendingPhoneNumber());
        assertNull(jane.getPhoneVerificationCodeHash());
    }

    private ClientUser reload() {
        return clients.findByEmail(EMAIL).orElseThrow();
    }

    /*
     * Checks: Every phone verification step needs a login.
     * How:    Status, send, verify and resend are called with no token. Each gives 401.
     */
    @Test
    void everyPhoneStep_needsALogin() throws Exception {
        mvc.perform(get("/api/phone-verification/status")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/phone-verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + NUMBER + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/phone-verification/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/phone-verification/resend")).andExpect(status().isUnauthorized());
    }

    /*
     * Checks: A new client has no verified phone.
     * How:    The status shows phoneVerified false, and no phone number or pending number.
     */
    @Test
    void aNewClient_hasNoVerifiedPhone() throws Exception {
        phoneStatus().andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVerified").value(false))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.pendingPhoneNumber").doesNotExist());
    }

    /*
     * Checks: A phone number without a country code is refused.
     * How:    "4165551234", "+0123456789" and "abc" each give 400.
     */
    @Test
    void aPhoneNumberWithoutTheCountryCode_isRejected() throws Exception {
        send("4165551234").andExpect(status().isBadRequest());
        send("+0123456789").andExpect(status().isBadRequest());
        send("abc").andExpect(status().isBadRequest());
    }

    /*
     * Checks: Sending a code uses the (fake local) Twilio Verify API and does not store the code.
     * How:    A small local HTTP server pretends to be Twilio. After send, the phone is not verified
     *   yet, the number is saved as pending, and no code hash is stored.
     */
    @Test
    void sendingACode_usesTheTwilioVerifyApiWithoutStoringTheCode() throws Exception {
        send(NUMBER).andExpect(status().isOk());
        assertFalse(reload().isPhoneVerified());
        assertEquals(NUMBER, reload().getPendingPhoneNumber());
        assertNull(reload().getPhoneVerificationCodeHash());
    }

    /*
     * Checks: An already verified number cannot be verified again.
     * How:    The client already has this number. The send gives 409 with "already verified".
     */
    @Test
    void anAlreadyVerifiedNumber_cannotBeVerifiedAgain() throws Exception {
        jane.setPhoneNumber(NUMBER);
        clients.save(jane);
        send(NUMBER).andExpect(status().isConflict()).andExpect(messageContains("already verified"));
    }

    /*
     * Checks: Asking again for the same number tells the client to use Resend.
     * How:    The second send gives 409 with "Resend code".
     */
    @Test
    void askingForACodeAgainForTheSameNumber_pointsToResend() throws Exception {
        codeWasSentTo(NUMBER);
        send(NUMBER).andExpect(status().isConflict()).andExpect(messageContains("Resend code"));
    }

    /*
     * Checks: The correct code verifies the phone.
     * How:    The fake Twilio approves 123456. After verify, the status shows phoneVerified true with
     *   the number and no pending number.
     */
    @Test
    void theCorrectCode_verifiesThePhone() throws Exception {
        codeWasSentTo(NUMBER);
        verify(CODE).andExpect(status().isOk());
        phoneStatus().andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.phoneNumber").value(NUMBER))
                .andExpect(jsonPath("$.pendingPhoneNumber").doesNotExist());
        assertNull(reload().getPhoneVerificationCodeHash());
    }

    /*
     * Checks: A code cannot be used after it worked.
     * How:    The second verify gives 400 with "no active phone verification".
     */
    @Test
    void aCode_cannotBeUsedAfterItWorked() throws Exception {
        codeWasSentTo(NUMBER);
        verify(CODE).andExpect(status().isOk());
        verify(CODE).andExpect(status().isBadRequest())
                .andExpect(messageContains("no active phone verification"));
    }

    /*
     * Checks: Verifying without asking for a code first is refused.
     * How:    The answer is 400 with "no active phone verification".
     */
    @Test
    void withoutARequestedCode_thereIsNothingToVerify() throws Exception {
        verify(CODE).andExpect(status().isBadRequest())
                .andExpect(messageContains("no active phone verification"));
    }

    /*
     * Checks: A code that is not 6 digits is refused.
     * How:    "12345" and "abcdef" give 400, and the phone is still not verified.
     */
    @Test
    void aCodeThatIsNotSixDigits_isRejected() throws Exception {
        codeWasSentTo(NUMBER);
        verify("12345").andExpect(status().isBadRequest());
        verify("abcdef").andExpect(status().isBadRequest());
        assertFalse(reload().isPhoneVerified());
    }

    /*
     * Checks: A wrong code is refused and counted.
     * How:    The answer says 4 attempts remain, and the attempts counter is 1.
     */
    @Test
    void aWrongCode_isRejectedAndCounted() throws Exception {
        codeWasSentTo(NUMBER);
        verify("000000").andExpect(status().isBadRequest())
                .andExpect(messageContains("4 attempt(s) remaining"));
        assertFalse(reload().isPhoneVerified());
        assertEquals(1, reload().getPhoneVerificationAttempts());
    }

    /*
     * Checks: After five wrong codes, even the correct code is refused.
     * How:    We send 5 wrong codes. The 5th says the maximum was reached, and the right code then
     *   also gives 400. The phone stays unverified.
     */
    @Test
    void afterFiveWrongCodes_evenTheCorrectCodeIsRefused() throws Exception {
        codeWasSentTo(NUMBER);
        for (int i = 0; i < 4; i++) {
            verify("000000").andExpect(status().isBadRequest());
        }
        verify("000000").andExpect(status().isBadRequest())
                .andExpect(messageContains("maximum of five incorrect attempts"));
        verify(CODE).andExpect(status().isBadRequest())
                .andExpect(messageContains("maximum number of incorrect attempts"));
        assertFalse(reload().isPhoneVerified());
    }

    /*
     * Checks: An expired code is refused.
     * How:    We set the expiry to one minute ago. The answer is 400 with "expired".
     */
    @Test
    void anExpiredCode_isRefused() throws Exception {
        codeWasSentTo(NUMBER);
        jane = reload();
        jane.setPhoneVerificationCodeExpiry(LocalDateTime.now().minusMinutes(1));
        clients.save(jane);
        verify(CODE).andExpect(status().isBadRequest()).andExpect(messageContains("expired"));
        assertFalse(reload().isPhoneVerified());
    }

    /*
     * Checks: A new number replaces the old one only after its code is correct.
     * How:    The client has an old number. After a code is sent to a new number and a wrong code is
     *   tried, the old number is still the verified one and the new one is pending. After the
     *   right code, the new number is verified.
     */
    @Test
    void aNewNumber_replacesTheOldOneOnlyAfterItsCodeIsCorrect() throws Exception {
        jane.setPhoneNumber(NUMBER);
        clients.save(jane);
        codeWasSentTo(OTHER_NUMBER);
        verify("000000").andExpect(status().isBadRequest());
        phoneStatus().andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.phoneNumber").value(NUMBER))
                .andExpect(jsonPath("$.pendingPhoneNumber").value(OTHER_NUMBER));
        verify(CODE).andExpect(status().isOk());
        phoneStatus().andExpect(jsonPath("$.phoneNumber").value(OTHER_NUMBER))
                .andExpect(jsonPath("$.pendingPhoneNumber").doesNotExist());
    }

    /*
     * Checks: Resend needs a code request first.
     * How:    The answer is 409 with "request a verification code first".
     */
    @Test
    void resend_needsACodeToBeRequestedFirst() throws Exception {
        resend().andExpect(status().isConflict())
                .andExpect(messageContains("request a verification code first"));
    }

    /*
     * Checks: Resend must wait for the cooldown.
     * How:    Resending right after the first send gives 409 with "Please wait".
     */
    @Test
    void resend_mustWaitForTheCooldown() throws Exception {
        codeWasSentTo(NUMBER);
        resend().andExpect(status().isConflict()).andExpect(messageContains("Please wait"));
    }

    /*
     * Checks: After the cooldown, resend sends another request.
     * How:    We set the last sent time to 61 seconds ago. Resend gives 200 and the resend count is 1.
     */
    @Test
    void afterTheCooldown_resendSendsAnotherVerificationRequest() throws Exception {
        codeWasSentTo(NUMBER);
        jane = reload();
        jane.setPhoneVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        clients.save(jane);
        resend().andExpect(status().isOk());
        assertEquals(1, reload().getPhoneVerificationResendCount());
    }

    /*
     * Checks: Resend is limited to three times.
     * How:    We set the resend count to 3. The next resend gives 409 with "maximum number of resend
     *   requests".
     */
    @Test
    void resend_isLimitedToThreeTimes() throws Exception {
        codeWasSentTo(NUMBER);
        jane = reload();
        jane.setPhoneVerificationResendCount(3);
        jane.setPhoneVerificationLastSentAt(LocalDateTime.now().minusMinutes(10));
        clients.save(jane);
        resend().andExpect(status().isConflict())
                .andExpect(messageContains("maximum number of resend requests"));
    }

    /*
     * Checks: Resend is refused after five wrong codes.
     * How:    We set the attempts to 5. Resend gives 409 with "maximum number of incorrect attempts".
     */
    @Test
    void resend_isRefusedAfterFiveWrongCodes() throws Exception {
        codeWasSentTo(NUMBER);
        jane = reload();
        jane.setPhoneVerificationAttempts(5);
        clients.save(jane);
        resend().andExpect(status().isConflict())
                .andExpect(messageContains("maximum number of incorrect attempts"));
    }
}