package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

/*
 * Integration tests for the sign-up and log-in journeys.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules,
 * real JSON requests. Only the outgoing email is replaced with a fake, so
 * no email is ever sent. Codes are random and stored as hashes, so after a
 * code is "sent" the test stores a code it knows (123456 / 654321) instead.
 * Every test is rolled back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///auth_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.mail.from-address=noreply@butterfly.test"
})
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest {

    private static final String EMAIL = "jane@mail.com";
    private static final String PASSWORD = "Password123";

    @Autowired MockMvc mvc;
    @Autowired ClientUserRepository clients;
    @Autowired AdminUserRepository admins;
    @Autowired PasswordEncoder encoder;

    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void fakeEmail() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    // ---------------------------------------------------------------- helpers

    /** Checks that the error message sent back contains the given text. */
    private ResultMatcher messageContains(String text) {
        return result -> assertTrue(
                result.getResponse().getContentAsString().contains(text),
                result.getResponse().getContentAsString());
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions signup(String email, String password, String confirm) throws Exception {
        return postJson("/api/auth/signup", String.format(
                "{\"fullName\":\"Jane Doe\",\"email\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}",
                email, password, confirm));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login",
                String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password));
    }

    private ResultActions verifyEmail(String email, String code) throws Exception {
        return postJson("/api/auth/verify-email",
                String.format("{\"email\":\"%s\",\"code\":\"%s\"}", email, code));
    }

    /** Replaces the random code of a new account with one the test knows. */
    private void knownEmailCode(String email, String code) {
        ClientUser user = clients.findByEmail(email).orElseThrow();
        user.setEmailVerificationCodeHash(encoder.encode(code));
        user.setEmailVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));
        clients.save(user);
    }

    /** Creates a client who signed up, verified the email and can log in. */
    private void verifiedClient() throws Exception {
        signup(EMAIL, PASSWORD, PASSWORD).andExpect(status().isCreated());
        knownEmailCode(EMAIL, "123456");
        verifyEmail(EMAIL, "123456").andExpect(status().isOk());
    }

    private String clientToken() throws Exception {
        verifiedClient();
        return login(EMAIL, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /** Starts the admin login and returns the challenge token the browser would get. */
    private String startAdminLogin() throws Exception {
        String body = login("admin@test.com", "Admin@12345")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"challengeToken\":\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), body);
        return matcher.group(1);
    }

    private void knownAdminCode(String code) {
        AdminUser admin = admins.findByEmail("admin@test.com").orElseThrow();
        admin.setLoginOtpHash(encoder.encode(code));
        admins.save(admin);
    }

    private ResultActions verifyAdminCode(String challenge, String code) throws Exception {
        return postJson("/api/admin-auth/login-otp/verify",
                String.format("{\"challengeToken\":\"%s\",\"code\":\"%s\"}", challenge, code));
    }

    // ------------------------------------------------------------ client signup

    @Test
    void signup_createsAnUnverifiedAccountAndEmailsACode() throws Exception {
        signup(EMAIL, PASSWORD, PASSWORD).andExpect(status().isCreated());

        ClientUser user = clients.findByEmail(EMAIL).orElseThrow();
        assertFalse(user.isEmailVerified());
        assertNotNull(user.getEmailVerificationCodeHash());
        assertNotEquals(PASSWORD, user.getPassword());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void signup_withAnEmailThatIsAlreadyUsed_isRejected() throws Exception {
        signup(EMAIL, PASSWORD, PASSWORD).andExpect(status().isCreated());

        signup(EMAIL, PASSWORD, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void signup_withPasswordsThatDoNotMatch_isRejected() throws Exception {
        signup(EMAIL, PASSWORD, "Different123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords do not match."));
    }

    @Test
    void signup_withAnInvalidEmail_isRejected() throws Exception {
        signup("not-an-email", PASSWORD, PASSWORD).andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------- email verification

    @Test
    void login_beforeTheEmailIsVerified_isForbidden() throws Exception {
        signup(EMAIL, PASSWORD, PASSWORD).andExpect(status().isCreated());

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(messageContains("verify your email"));
    }

    @Test
    void verifyEmail_withAWrongCode_isRejectedAndKeepsTheAccountUnverified() throws Exception {
        signup(EMAIL, PASSWORD, PASSWORD).andExpect(status().isCreated());
        knownEmailCode(EMAIL, "123456");

        verifyEmail(EMAIL, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("4 attempt(s) remaining"));

        assertFalse(clients.findByEmail(EMAIL).orElseThrow().isEmailVerified());
    }

    @Test
    void verifyEmail_withTheCorrectCode_verifiesTheAccount() throws Exception {
        signup(EMAIL, PASSWORD, PASSWORD).andExpect(status().isCreated());
        knownEmailCode(EMAIL, "123456");

        verifyEmail(EMAIL, "123456").andExpect(status().isOk());

        assertTrue(clients.findByEmail(EMAIL).orElseThrow().isEmailVerified());
    }

    @Test
    void resendVerification_rightAfterSignup_isRejectedBecauseOfTheWait() throws Exception {
        signup(EMAIL, PASSWORD, PASSWORD).andExpect(status().isCreated());

        postJson("/api/auth/resend-verification", String.format("{\"email\":\"%s\"}", EMAIL))
                .andExpect(status().isConflict())
                .andExpect(messageContains("Please wait"));
    }

    // -------------------------------------------------------------- client login

    @Test
    void login_afterVerification_returnsATokenThatOpensPrivatePages() throws Exception {
        String token = clientToken();

        assertEquals(3, token.split("\\.").length);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void login_withAWrongPassword_isUnauthorized() throws Exception {
        verifiedClient();

        login(EMAIL, "WrongPassword1").andExpect(status().isUnauthorized());
    }

    @Test
    void login_withAnEmailThatDoesNotExist_isUnauthorized() throws Exception {
        login("nobody@mail.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void privatePage_withoutAToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void privatePage_withAFakeToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("fake.token.value")))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------- admin login

    @Test
    void adminLogin_withTheCorrectPassword_asksForAnEmailCodeAndGivesNoToken() throws Exception {
        login("admin@test.com", "Admin@12345")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true))
                .andExpect(jsonPath("$.challengeToken").isNotEmpty())
                .andExpect(jsonPath("$.maskedEmail").value("ad***@test.com"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void adminLogin_withTheCorrectEmailCode_returnsATokenForTheAdminArea() throws Exception {
        String challenge = startAdminLogin();
        knownAdminCode("654321");

        String token = verifyAdminCode(challenge, "654321")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/admin-account/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"));
    }

    @Test
    void adminLogin_withAWrongEmailCode_isRejected() throws Exception {
        String challenge = startAdminLogin();
        knownAdminCode("654321");

        verifyAdminCode(challenge, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(messageContains("4 attempt(s) remaining"));
    }

    @Test
    void adminLogin_withAMadeUpChallenge_isRejected() throws Exception {
        verifyAdminCode("999.not-a-real-challenge", "123456").andExpect(status().isBadRequest());
    }

    @Test
    void adminArea_withoutAToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/admin-account/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminArea_withAClientToken_isForbidden() throws Exception {
        String token = clientToken();

        mvc.perform(get("/api/admin-account/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }
}