package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.JwtService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/*
 * Integration tests for the admin account page:
 *   the admin sees their profile -> asks to change the login email -> a code is
 *   emailed to the NEW address -> the email changes only after the code is
 *   correct, and a fresh login token is returned.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 * The mail sender is replaced by a mock, so no real email is ever sent.
 * Codes are put into the database by the tests. Every test is rolled back.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///admin_account_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.mail.from-address=noreply@butterfly.test",
        "app.admin.email=admin@test.com",
        "app.admin.password=Admin@12345"
})
@AutoConfigureMockMvc
@Transactional
class AdminAccountFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String NEW_EMAIL = "new.admin@mail.com";
    private static final String CODE = "123456";

    @Autowired MockMvc mvc;
    @Autowired AdminUserRepository admins;
    @Autowired ClientUserRepository clients;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    @MockitoBean JavaMailSender mailSender;

    private String adminToken;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage((Session) null));
        adminToken = "Bearer " + jwt.generateToken(ADMIN_EMAIL, "ADMIN");
    }

    // ---------------------------------------------------------------- helpers

    private ResultMatcher messageContains(String text) {
        return result -> assertTrue(
                result.getResponse().getContentAsString().contains(text),
                result.getResponse().getContentAsString());
    }

    private ResultActions profile(String token) throws Exception {
        return mvc.perform(get("/api/admin-account/profile")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions requestChange(String email) throws Exception {
        return mvc.perform(post("/api/admin-account/email-change/request")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\":\"%s\"}", email)));
    }

    private ResultActions verifyChange(String code) throws Exception {
        return mvc.perform(post("/api/admin-account/email-change/verify")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"code\":\"%s\"}", code)));
    }

    private ResultActions resend() throws Exception {
        return mvc.perform(post("/api/admin-account/email-change/resend")
                .header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private AdminUser admin() {
        return admins.findByEmail(ADMIN_EMAIL).orElseThrow();
    }

    /** Replaces the random emailed code with one the test knows. */
    private void knownCode() {
        AdminUser admin = admin();
        admin.setEmailChangeOtpHash(encoder.encode(CODE));
        admin.setEmailChangeOtpExpiry(LocalDateTime.now().plusMinutes(10));
        admins.save(admin);
    }

    private void requestWithKnownCode() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());
        knownCode();
    }

    // -------------------------------------------------------------- protection

    @Test
    void theAdminAccountPage_needsAnAdminLogin() throws Exception {
        mvc.perform(get("/api/admin-account/profile")).andExpect(status().isUnauthorized());

        clients.save(new ClientUser("Jane Lee", "jane@mail.com", "hash"));
        String client = "Bearer " + jwt.generateToken("jane@mail.com", "CLIENT");
        profile(client).andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------- profile

    @Test
    void admin_seesTheirProfile() throws Exception {
        profile(adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Administrator"))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    // ---------------------------------------------------- asking to change email

    @Test
    void asking_emailsACodeToTheNewAddressOnly() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(sent.capture());
        assertEquals(NEW_EMAIL, sent.getValue().getAllRecipients()[0].toString());

        // The login email does not change yet.
        assertEquals(ADMIN_EMAIL, admin().getEmail());
        assertEquals(NEW_EMAIL, admin().getPendingEmail());
    }

    @Test
    void theNewEmailIsStoredInLowerCase() throws Exception {
        requestChange("New.Admin@Mail.COM").andExpect(status().isOk());

        assertEquals(NEW_EMAIL, admin().getPendingEmail());
    }

    @Test
    void anInvalidEmail_isRejected() throws Exception {
        requestChange("not-an-email").andExpect(status().isBadRequest());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void theSameEmail_isRefused() throws Exception {
        requestChange(ADMIN_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(messageContains("same as your current email"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void anEmailUsedByAClient_isRefused() throws Exception {
        clients.save(new ClientUser("Jane Lee", "jane@mail.com", "hash"));

        requestChange("jane@mail.com")
                .andExpect(status().isConflict())
                .andExpect(messageContains("client account already uses"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void askingTwiceForTheSameAddress_pointsToResend() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());

        requestChange(NEW_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(messageContains("Use Resend code"));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    // ----------------------------------------------------------- entering the code

    @Test
    void theCorrectCode_changesTheEmailAndReturnsAFreshToken() throws Exception {
        requestWithKnownCode();

        String newToken = verifyChange(CODE)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The admin is now found by the new email.
        AdminUser changed = admins.findByEmail(NEW_EMAIL).orElseThrow();
        assertEquals(NEW_EMAIL, changed.getEmail());
        assertNull(changed.getPendingEmail());

        // The new token works and shows the new email.
        profile("Bearer " + newToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL));
    }

    @Test
    void afterTheChange_theOldLoginTokenNoLongerFindsTheAccount() throws Exception {
        requestWithKnownCode();
        verifyChange(CODE).andExpect(status().isOk());

        profile(adminToken).andExpect(status().isNotFound());
    }

    @Test
    void aCode_cannotBeUsedTwice() throws Exception {
        requestWithKnownCode();
        String newToken = verifyChange(CODE).andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/admin-account/email-change/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("no active email change request"));
    }

    @Test
    void withoutARequest_thereIsNothingToVerify() throws Exception {
        verifyChange(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("no active email change request"));
    }

    @Test
    void aCodeThatIsNotSixDigits_isRejected() throws Exception {
        requestWithKnownCode();

        verifyChange("12345").andExpect(status().isBadRequest());
        verifyChange("abcdef").andExpect(status().isBadRequest());

        assertEquals(ADMIN_EMAIL, admin().getEmail());
    }

    @Test
    void aWrongCode_isRejectedAndCounted() throws Exception {
        requestWithKnownCode();

        verifyChange("000000")
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("4 attempt(s) remaining"));

        assertEquals(ADMIN_EMAIL, admin().getEmail());
        assertEquals(1, admin().getEmailChangeOtpAttempts());
    }

    @Test
    void afterFiveWrongCodes_theRequestIsCancelledAndTheEmailStaysTheSame() throws Exception {
        requestWithKnownCode();

        for (int i = 0; i < 4; i++) {
            verifyChange("000000").andExpect(status().isBadRequest());
        }
        verifyChange("000000")
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("current email address has not changed"));

        assertEquals(ADMIN_EMAIL, admin().getEmail());
        assertNull(admin().getPendingEmail());

        // Even the right code no longer works: the admin must start again.
        verifyChange(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("no active email change request"));
    }

    @Test
    void anExpiredCode_isRefused() throws Exception {
        requestWithKnownCode();
        AdminUser admin = admin();
        admin.setEmailChangeOtpExpiry(LocalDateTime.now().minusMinutes(1));
        admins.save(admin);

        verifyChange(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("expired"));

        assertEquals(ADMIN_EMAIL, admin().getEmail());
    }

    // ------------------------------------------------------------------ resend

    @Test
    void resend_needsARequestFirst() throws Exception {
        resend().andExpect(status().isConflict())
                .andExpect(messageContains("no active email change request"));
    }

    @Test
    void resend_mustWaitForTheCooldown() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());

        resend().andExpect(status().isConflict())
                .andExpect(messageContains("Please wait"));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void afterTheCooldown_resendEmailsANewCode() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());
        AdminUser admin = admin();
        admin.setEmailChangeOtpLastSentAt(LocalDateTime.now().minusSeconds(61));
        admins.save(admin);

        resend().andExpect(status().isOk());

        verify(mailSender, times(2)).send(any(MimeMessage.class));
        assertEquals(1, admin().getEmailChangeOtpResendCount());
    }

    @Test
    void resend_isLimitedToThreeTimes() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());
        AdminUser admin = admin();
        admin.setEmailChangeOtpResendCount(3);
        admin.setEmailChangeOtpLastSentAt(LocalDateTime.now().minusMinutes(10));
        admins.save(admin);

        resend().andExpect(status().isConflict())
                .andExpect(messageContains("maximum number of resend requests"));
    }
}