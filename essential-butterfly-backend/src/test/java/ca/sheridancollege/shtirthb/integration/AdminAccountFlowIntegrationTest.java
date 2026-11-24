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

    /*
     * Checks: The admin account page needs an admin login.
     * How:    No token gives 401. A client token gives 403.
     */
    @Test
    void theAdminAccountPage_needsAnAdminLogin() throws Exception {
        mvc.perform(get("/api/admin-account/profile")).andExpect(status().isUnauthorized());

        clients.save(new ClientUser("Jane Lee", "jane@mail.com", "hash"));
        String client = "Bearer " + jwt.generateToken("jane@mail.com", "CLIENT");
        profile(client).andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------- profile

    /*
     * Checks: The admin can see their own profile.
     * How:    The profile URL returns the name Administrator and the admin's email.
     */
    @Test
    void admin_seesTheirProfile() throws Exception {
        profile(adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Administrator"))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    // ---------------------------------------------------- asking to change email

    /*
     * Checks: Asking to change the email sends a code to the new address only.
     * How:    The fake mail sender is called once, and its recipient is the new email. The login email
     *   in the database does not change yet, and the new one is saved as the pending email.
     */
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

    /*
     * Checks: The new email is stored in lower case.
     * How:    We ask with New.Admin@Mail.COM. The pending email is new.admin@mail.com.
     */
    @Test
    void theNewEmailIsStoredInLowerCase() throws Exception {
        requestChange("New.Admin@Mail.COM").andExpect(status().isOk());

        assertEquals(NEW_EMAIL, admin().getPendingEmail());
    }

    /*
     * Checks: An invalid new email is refused.
     * How:    The answer is 400 and no email is sent.
     */
    @Test
    void anInvalidEmail_isRejected() throws Exception {
        requestChange("not-an-email").andExpect(status().isBadRequest());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: Changing to the current email is refused.
     * How:    The answer is 409 with "same as your current email", and no email is sent.
     */
    @Test
    void theSameEmail_isRefused() throws Exception {
        requestChange(ADMIN_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(messageContains("same as your current email"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: An email that a client already uses is refused.
     * How:    We save a client first. The answer is 409 and no email is sent.
     */
    @Test
    void anEmailUsedByAClient_isRefused() throws Exception {
        clients.save(new ClientUser("Jane Lee", "jane@mail.com", "hash"));

        requestChange("jane@mail.com")
                .andExpect(status().isConflict())
                .andExpect(messageContains("client account already uses"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: Asking twice for the same address tells the admin to use Resend.
     * How:    The second request gives 409 with "Use Resend code", and only one email was sent in
     *   total.
     */
    @Test
    void askingTwiceForTheSameAddress_pointsToResend() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());

        requestChange(NEW_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(messageContains("Use Resend code"));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    // ----------------------------------------------------------- entering the code

    /*
     * Checks: The right code changes the email and gives a new token.
     * How:    We set a code we know. After verify, the admin is found by the new email and has no
     *   pending email. The new token opens the profile with the new email.
     */
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

    /*
     * Checks: After the change, the old token no longer finds the account.
     * How:    The profile with the old token answers 404, because the old email does not exist
     *   anymore.
     */
    @Test
    void afterTheChange_theOldLoginTokenNoLongerFindsTheAccount() throws Exception {
        requestWithKnownCode();
        verifyChange(CODE).andExpect(status().isOk());

        profile(adminToken).andExpect(status().isNotFound());
    }

    /*
     * Checks: A code cannot be used twice.
     * How:    After a successful verify, a second verify gives 400 with "no active email change
     *   request".
     */
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

    /*
     * Checks: Verifying without a request is refused.
     * How:    The answer is 400 with "no active email change request".
     */
    @Test
    void withoutARequest_thereIsNothingToVerify() throws Exception {
        verifyChange(CODE)
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("no active email change request"));
    }

    /*
     * Checks: A code that is not 6 digits is refused.
     * How:    "12345" and "abcdef" both give 400, and the email is not changed.
     */
    @Test
    void aCodeThatIsNotSixDigits_isRejected() throws Exception {
        requestWithKnownCode();

        verifyChange("12345").andExpect(status().isBadRequest());
        verifyChange("abcdef").andExpect(status().isBadRequest());

        assertEquals(ADMIN_EMAIL, admin().getEmail());
    }

    /*
     * Checks: A wrong code is refused and counted.
     * How:    The answer says 4 attempts remain, and the attempts counter in the database is 1.
     */
    @Test
    void aWrongCode_isRejectedAndCounted() throws Exception {
        requestWithKnownCode();

        verifyChange("000000")
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("4 attempt(s) remaining"));

        assertEquals(ADMIN_EMAIL, admin().getEmail());
        assertEquals(1, admin().getEmailChangeOtpAttempts());
    }

    /*
     * Checks: After five wrong codes the request is cancelled.
     * How:    We send 4 wrong codes, then a 5th. The message says the email has not changed, the
     *   pending email is empty, and even the right code now gives "no active request".
     */
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

    /*
     * Checks: An expired code is refused.
     * How:    We set the expiry to one minute ago. The answer is 400 with "expired", and the email
     *   does not change.
     */
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

    /*
     * Checks: Resend needs an active request first.
     * How:    The answer is 409 with "no active email change request".
     */
    @Test
    void resend_needsARequestFirst() throws Exception {
        resend().andExpect(status().isConflict())
                .andExpect(messageContains("no active email change request"));
    }

    /*
     * Checks: Resend must wait for the cooldown.
     * How:    Resend right after the request gives 409 with "Please wait", and still only one email
     *   was sent.
     */
    @Test
    void resend_mustWaitForTheCooldown() throws Exception {
        requestChange(NEW_EMAIL).andExpect(status().isOk());

        resend().andExpect(status().isConflict())
                .andExpect(messageContains("Please wait"));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    /*
     * Checks: After the cooldown, resend emails a new code.
     * How:    We set the last sent time to 61 seconds ago. Resend gives 200, 2 emails were sent in
     *   total, and the resend count is 1.
     */
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

    /*
     * Checks: Resend is limited to three times.
     * How:    We set the resend count to 3. The next resend gives 409 with "maximum number of resend
     *   requests".
     */
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