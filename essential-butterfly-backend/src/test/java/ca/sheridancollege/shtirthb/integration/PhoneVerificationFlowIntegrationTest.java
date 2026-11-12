package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * Integration tests for the phone verification journey:
 *   client enters a phone number -> a code is sent by SMS -> client enters the
 *   code -> the number becomes verified (a new number replaces the old one
 *   only after its own code is correct).
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 *
 * NO REAL SMS IS EVER SENT. The Twilio settings are set to empty below, so the
 * app stops with "not configured" before it contacts Twilio. This holds even if
 * real Twilio keys exist in the computer's environment. Codes are put into the
 * database by the tests, so the "enter the code" steps can be tested fully.
 * Every test is rolled back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///phone_verification_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.twilio.account-sid=",
        "app.twilio.auth-token=",
        "app.twilio.from-number="
})
@AutoConfigureMockMvc
@Transactional
class PhoneVerificationFlowIntegrationTest {

    private static final String EMAIL = "jane@mail.com";
    private static final String NUMBER = "+14165551234";
    private static final String OTHER_NUMBER = "+16475559876";
    private static final String CODE = "123456";

    @Autowired MockMvc mvc;
    @Autowired ClientUserRepository clients;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private ClientUser jane;
    private String token;

    @BeforeEach
    void setUp() {
        jane = clients.save(new ClientUser("Jane Lee", EMAIL, "hash"));
        token = "Bearer " + jwt.generateToken(EMAIL, "CLIENT");
    }

    // ---------------------------------------------------------------- helpers

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

    /** Pretends a code was sent to the given number a moment ago. */
    private void codeWasSentTo(String number) {
        jane.setPendingPhoneNumber(number);
        jane.setPhoneVerificationCodeHash(encoder.encode(CODE));
        jane.setPhoneVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));
        jane.setPhoneVerificationLastSentAt(LocalDateTime.now());
        jane.setPhoneVerificationAttempts(0);
        jane.setPhoneVerificationResendCount(0);
        clients.save(jane);
    }

    private ClientUser reload() {
        return clients.findByEmail(EMAIL).orElseThrow();
    }

    // -------------------------------------------------------------- protection

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

    // ------------------------------------------------------------------ status

    @Test
    void aNewClient_hasNoVerifiedPhone() throws Exception {
        phoneStatus().andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVerified").value(false))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.pendingPhoneNumber").doesNotExist());
    }

    // ----------------------------------------------------------- sending a code

    @Test
    void aPhoneNumberWithoutTheCountryCode_isRejected() throws Exception {
        send("4165551234").andExpect(status().isBadRequest());
        send("+0123456789").andExpect(status().isBadRequest());
        send("abc").andExpect(status().isBadRequest());
    }

    @Test
    void sendingACode_failsSafelyWhenSmsIsNotConfigured() throws Exception {
        // This is also the proof that the tests never reach Twilio.
        send(NUMBER)
                .andExpect(status().isServiceUnavailable())
                .andExpect(messageContains("Phone verification is not configured"));

        assertFalse(reload().isPhoneVerified());
    }

    @Test
    void anAlreadyVerifiedNumber_cannotBeVerifiedAgain() throws Exception {
        jane.setPhoneNumber(NUMBER);
        jane.setPhoneVerified(true);
        clients.save(jane);

        send(NUMBER)
                .andExpect(status().isConflict())
                .andExpect(messageContains("already verified"));
    }

    @Test
    void askingForACodeAgainForTheSameNumber_pointsToResend() throws Exception {
        codeWasSentTo(NUMBER);

        send(NUMBER)
                .andExpect(status().isConflict())
                .andExpect(messageContains("Resend code"));
    }

    // --------------------------------------------------------- entering the code

    @Test
    void theCorrectCode_verifiesThePhone() throws Exception {
        codeWasSentTo(NUMBER);

        verify(CODE).andExpect(status().isOk());

        phoneStatus().andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.phoneNumber").value(NUMBER))
                .andExpect(jsonPath("$.pendingPhoneNumber").doesNotExist());
        assertNull(reload().getPhoneVerificationCodeHash());
    }

    @Test
    void aCode_cannotBeUsedAfterItWorked() throws Exception {
        codeWasSentTo(NUMBER);
        verify(CODE).andExpect(status().isOk());

        verify(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("no active phone verification"));
    }

    @Test
    void withoutARequestedCode_thereIsNothingToVerify() throws Exception {
        verify(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("no active phone verification"));
    }

    @Test
    void aCodeThatIsNotSixDigits_isRejected() throws Exception {
        codeWasSentTo(NUMBER);

        verify("12345").andExpect(status().isBadRequest());
        verify("abcdef").andExpect(status().isBadRequest());

        assertFalse(reload().isPhoneVerified());
    }

    @Test
    void aWrongCode_isRejectedAndCounted() throws Exception {
        codeWasSentTo(NUMBER);

        verify("000000")
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("4 attempt(s) remaining"));

        assertFalse(reload().isPhoneVerified());
        assertEquals(1, reload().getPhoneVerificationAttempts());
    }

    @Test
    void afterFiveWrongCodes_evenTheCorrectCodeIsRefused() throws Exception {
        codeWasSentTo(NUMBER);

        for (int i = 0; i < 4; i++) {
            verify("000000").andExpect(status().isBadRequest());
        }
        verify("000000")
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("maximum of five incorrect attempts"));

        verify(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("maximum number of incorrect attempts"));

        assertFalse(reload().isPhoneVerified());
    }

    @Test
    void anExpiredCode_isRefused() throws Exception {
        codeWasSentTo(NUMBER);
        jane.setPhoneVerificationCodeExpiry(LocalDateTime.now().minusMinutes(1));
        clients.save(jane);

        verify(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("expired"));

        assertFalse(reload().isPhoneVerified());
    }

    // ------------------------------------------------------ changing the number

    @Test
    void aNewNumber_replacesTheOldOneOnlyAfterItsCodeIsCorrect() throws Exception {
        jane.setPhoneNumber(NUMBER);
        jane.setPhoneVerified(true);
        clients.save(jane);
        codeWasSentTo(OTHER_NUMBER);

        // A wrong code changes nothing: the old number stays verified.
        verify("000000").andExpect(status().isBadRequest());
        phoneStatus().andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.phoneNumber").value(NUMBER))
                .andExpect(jsonPath("$.pendingPhoneNumber").value(OTHER_NUMBER));

        // The right code switches to the new number.
        verify(CODE).andExpect(status().isOk());
        phoneStatus().andExpect(jsonPath("$.phoneNumber").value(OTHER_NUMBER))
                .andExpect(jsonPath("$.pendingPhoneNumber").doesNotExist());
    }

    // ------------------------------------------------------------------ resend

    @Test
    void resend_needsACodeToBeRequestedFirst() throws Exception {
        resend().andExpect(status().isConflict())
                .andExpect(messageContains("request a verification code first"));
    }

    @Test
    void resend_mustWaitForTheCooldown() throws Exception {
        codeWasSentTo(NUMBER);

        resend().andExpect(status().isConflict())
                .andExpect(messageContains("Please wait"));
    }

    @Test
    void afterTheCooldown_resendTriesToSendAgain() throws Exception {
        codeWasSentTo(NUMBER);
        jane.setPhoneVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        clients.save(jane);

        // The cooldown is over, so the app goes on to send the SMS.
        // SMS is not configured in tests, so it stops there.
        resend().andExpect(status().isServiceUnavailable())
                .andExpect(messageContains("Phone verification is not configured"));
    }

    @Test
    void resend_isLimitedToThreeTimes() throws Exception {
        codeWasSentTo(NUMBER);
        jane.setPhoneVerificationResendCount(3);
        jane.setPhoneVerificationLastSentAt(LocalDateTime.now().minusMinutes(10));
        clients.save(jane);

        resend().andExpect(status().isConflict())
                .andExpect(messageContains("maximum number of resend requests"));
    }

    @Test
    void resend_isRefusedAfterFiveWrongCodes() throws Exception {
        codeWasSentTo(NUMBER);
        jane.setPhoneVerificationAttempts(5);
        clients.save(jane);

        resend().andExpect(status().isConflict())
                .andExpect(messageContains("maximum number of incorrect attempts"));
    }
}