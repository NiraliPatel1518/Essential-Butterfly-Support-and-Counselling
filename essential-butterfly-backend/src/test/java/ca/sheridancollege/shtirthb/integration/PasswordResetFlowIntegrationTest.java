package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.PasswordResetTokenRepository;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/*
 * Integration tests for the forgotten password journey:
 *   user asks for a reset -> a one-time token is created -> user sets a new
 *   password with the token -> the old password stops working.
 *
 * Real application, real PostgreSQL (Testcontainers), real security rules.
 * The reset link is read from the email. The mail sender is a mock, so no
 * real email is sent.
 * Every test is rolled back at the end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///password_reset_flow_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.admin.email=admin@test.com",
        "app.admin.password=Admin@12345",
        "app.mail.from-address=noreply@butterfly.test"
})
@AutoConfigureMockMvc
@Transactional
class PasswordResetFlowIntegrationTest {

    private static final String EMAIL = "jane@mail.com";
    private static final String OLD_PASSWORD = "OldPassword1";
    private static final String NEW_PASSWORD = "NewPassword1";
    private static final String GENERIC_ANSWER =
            "If an account exists with this email, a password reset link has been generated.";

    @Autowired MockMvc mvc;
    @Autowired ClientUserRepository clients;
    @Autowired AdminUserRepository admins;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;

    // Admin login sends an email code, so the mail sender is replaced by a mock.
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage((Session) null));
        clients.save(new ClientUser("Jane Lee", EMAIL, encoder.encode(OLD_PASSWORD)));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/password/forgot").param("email", email));
    }

    private ResultActions reset(String token, String password, String confirm) throws Exception {
        return mvc.perform(post("/api/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                        "{\"token\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}",
                        token, password, confirm)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)));
    }

    private PasswordResetToken onlyToken() {
        List<PasswordResetToken> all = tokens.findAll();
        assertEquals(1, all.size());
        return all.get(0);
    }

    /** Reads the reset link from the email that was sent; the database stores only its SHA-256 hash. */
    private String requestedClientToken() throws Exception {
        return requestedToken(EMAIL);
    }

    private String requestedToken(String email) throws Exception {
        forgot(email).andExpect(status().isOk());
        Matcher matcher = Pattern.compile("/reset-password\\?token=([a-f0-9-]+)")
                .matcher(textOf(resetMail()));
        if (!matcher.find()) throw new AssertionError("The reset link was not in the email");
        return matcher.group(1);
    }

    /** The most recent email that contains a reset link. */
    private MimeMessage resetMail() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        List<MimeMessage> all = captor.getAllValues();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (textOf(all.get(i)).contains("/reset-password?token=")) return all.get(i);
        }
        throw new AssertionError("No reset email was sent");
    }

    // Collects the text of every part of an email.
    private static String textOf(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            StringBuilder all = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                all.append(textOf(multipart.getBodyPart(i))).append('\n');
            }
            return all.toString();
        }
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof java.io.InputStream stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part.getDataHandler().writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean clientPasswordIs(String password) {
        return encoder.matches(password, clients.findByEmail(EMAIL).orElseThrow().getPassword());
    }

    // -------------------------------------------------------- asking for a reset

    /*
     * Checks: Asking for a reset creates a one-time token that lasts 30 minutes.
     * How:    The answer is the same generic text. In the database there is one token with type
     *   CLIENT, the right owner, a 64-character hash, used false, and an expiry about 30 minutes
     *   away.
     */
    @Test
    void asking_createsAOneTimeTokenThatLastsThirtyMinutes() throws Exception {
        forgot(EMAIL)
                .andExpect(status().isOk())
                .andExpect(content().string(GENERIC_ANSWER));

        PasswordResetToken token = onlyToken();
        Long clientId = clients.findByEmail(EMAIL).orElseThrow().getId();
        assertEquals("CLIENT", token.getUserType());
        assertEquals(clientId, token.getClientUser().getId());
        assertEquals(64, token.getTokenHash().length());
        assertFalse(token.isUsed());
        assertTrue(token.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));
        assertTrue(token.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(31)));
    }

    /*
     * Checks: An unknown email gets the same answer and no token.
     * How:    The answer is identical to the known-email answer (so nobody can guess which emails
     *   exist), and the token table is empty.
     */
    @Test
    void anUnknownEmail_getsTheSameAnswerAndNoToken() throws Exception {
        // The answer must not reveal whether an account exists.
        forgot("nobody@mail.com")
                .andExpect(status().isOk())
                .andExpect(content().string(GENERIC_ANSWER));

        assertEquals(0, tokens.count());
    }

    /*
     * Checks: An invalid email is refused.
     * How:    "not-an-email" gives 400.
     */
    @Test
    void anInvalidEmail_isRejected() throws Exception {
        forgot("not-an-email").andExpect(status().isBadRequest());

        assertEquals(0, tokens.count());
    }

    /*
     * Checks: Spaces and capital letters in the email do not matter.
     * How:    We send " JANE@Mail.COM ". It works and exactly 1 token is created.
     */
    @Test
    void theEmailIsTrimmedAndCaseDoesNotMatter() throws Exception {
        forgot("  JANE@Mail.COM ").andExpect(status().isOk());

        assertEquals(1, tokens.count());
    }

    /*
     * Checks: An admin email gets an admin token.
     * How:    The saved token has the type ADMIN and is linked to the admin account.
     */
    @Test
    void anAdminEmail_getsAnAdminToken() throws Exception {
        AdminUser admin = admins.findByEmail("admin@test.com").orElseThrow();

        forgot("admin@test.com").andExpect(status().isOk());

        PasswordResetToken token = onlyToken();
        assertEquals("ADMIN", token.getUserType());
        assertEquals(admin.getId(), token.getAdminUser().getId());
    }

    /*
     * Checks: The reset link is emailed to the account owner.
     * How:    We capture the fake email. It has one recipient (the owner), the right sender and
     *   subject, and the text says the link expires in 30 minutes.
     */
    @Test
    void asking_emailsTheResetLinkToTheAccountOwner() throws Exception {
        forgot(EMAIL).andExpect(status().isOk());

        MimeMessage mail = resetMail();
        assertEquals(1, mail.getRecipients(Message.RecipientType.TO).length);
        assertEquals(EMAIL, mail.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("noreply@butterfly.test", mail.getFrom()[0].toString());
        assertEquals("Reset your Essential Butterfly password", mail.getSubject());
        assertTrue(textOf(mail).contains("expires in 30 minutes"));
    }

    /*
     * Checks: The link in the email matches the token in the database.
     * How:    We take the raw token from the email, hash it, and compare with the stored hash (the
     *   database never keeps the raw token).
     */
    @Test
    void theEmailedLinkMatchesTheTokenInTheDatabase() throws Exception {
        String rawToken = requestedClientToken();

        assertEquals(hash(rawToken), onlyToken().getTokenHash());
    }

    /*
     * Checks: An admin also gets the reset link by email.
     * How:    The raw token from the admin email, once hashed, equals the stored hash.
     */
    @Test
    void anAdminEmail_getsTheResetLinkToo() throws Exception {
        String rawToken = requestedToken("admin@test.com");

        assertEquals(hash(rawToken), onlyToken().getTokenHash());
    }

    /*
     * Checks: An unknown email gets no mail.
     * How:    The answer is 200 and the fake mail sender is never called.
     */
    @Test
    void anUnknownEmail_getsNoMail() throws Exception {
        forgot("nobody@mail.com").andExpect(status().isOk());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: The reset link is not printed to the console.
     * How:    We catch System.out during the request. The output must not contain
     *   /reset-password?token=.
     */
    @Test
    void theLinkIsNotPrintedToTheConsole() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            forgot(EMAIL).andExpect(status().isOk());
        } finally {
            System.setOut(original);
        }

        assertFalse(output.toString(StandardCharsets.UTF_8).contains("/reset-password?token="));
    }

    /*
     * Checks: If the email fails to send, the answer is the same and the token is kept.
     * How:    The fake sender throws MailSendException. The answer is still the generic one and 1
     *   token is saved.
     */
    @Test
    void aFailedEmail_stillGivesTheSameAnswerAndKeepsTheToken() throws Exception {
        doThrow(new MailSendException("mail server is down")).when(mailSender).send(any(MimeMessage.class));

        forgot(EMAIL)
                .andExpect(status().isOk())
                .andExpect(content().string(GENERIC_ANSWER));

        assertEquals(1, tokens.count());
    }

    // --------------------------------------------------------- setting the new one

    /*
     * Checks: With the token, the client can set a new password and log in with it.
     * How:    The reset answers "Password reset successfully." and then a login with the new password
     *   gives 200.
     */
    @Test
    void withTheToken_theClientCanSetANewPasswordAndLogInWithIt() throws Exception {
        String token = requestedClientToken();

        reset(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successfully."));

        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    /*
     * Checks: After the reset, the old password stops working.
     * How:    A login with the old password gives 401.
     */
    @Test
    void afterTheReset_theOldPasswordNoLongerWorks() throws Exception {
        String token = requestedClientToken();
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        login(EMAIL, OLD_PASSWORD).andExpect(status().isUnauthorized());
    }

    /*
     * Checks: The new password is stored hashed.
     * How:    The saved password is not the plain text, and the BCrypt encoder says it matches.
     */
    @Test
    void theNewPassword_isStoredHashed() throws Exception {
        String token = requestedClientToken();
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        String stored = clients.findByEmail(EMAIL).orElseThrow().getPassword();
        assertFalse(NEW_PASSWORD.equals(stored));
        assertTrue(encoder.matches(NEW_PASSWORD, stored));
    }

    /*
     * Checks: A token can only be used once.
     * How:    After the first reset the token is marked used. The second reset gives 400 with
     *   "Invalid, expired, or already used reset token." and the password stays the same.
     */
    @Test
    void aToken_canOnlyBeUsedOnce() throws Exception {
        String token = requestedClientToken();
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
        assertTrue(onlyToken().isUsed());

        reset(token, "AnotherPass1", "AnotherPass1")
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid, expired, or already used reset token."));

        assertTrue(clientPasswordIs(NEW_PASSWORD));
    }

    /*
     * Checks: An unknown token is refused.
     * How:    A made-up token gives 400 with the same message, and the old password still works.
     */
    @Test
    void anUnknownToken_isRejected() throws Exception {
        reset("not-a-real-token", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid, expired, or already used reset token."));

        assertTrue(clientPasswordIs(OLD_PASSWORD));
    }

    /*
     * Checks: An expired token is refused.
     * How:    We save a token that expired one minute ago. The reset gives 400 and the password is
     *   unchanged.
     */
    @Test
    void anExpiredToken_isRejected() throws Exception {
        ClientUser client = clients.findByEmail(EMAIL).orElseThrow();

        LocalDateTime now = LocalDateTime.now();

        PasswordResetToken expiredToken = PasswordResetToken.forClient(
            hash("expired-token"),
            client,
            now.minusMinutes(1)
    );

        // The expiry must be after creation, even though both are in the past.
        ReflectionTestUtils.setField(
            expiredToken,
            "createdAt",
            now.minusHours(1)
    );

        tokens.saveAndFlush(expiredToken);

        reset("expired-token", NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(
                    "Invalid, expired, or already used reset token."));

    assertTrue(clientPasswordIs(OLD_PASSWORD));
}

    /*
     * Checks: Passwords that do not match change nothing and the token stays usable.
     * How:    The answer is 400 with "Passwords do not match." The old password still works and the
     *   token is not used. A correct second try works.
     */
    @Test
    void passwordsThatDoNotMatch_changeNothingAndKeepTheTokenUsable() throws Exception {
        String token = requestedClientToken();

        reset(token, NEW_PASSWORD, "SomethingElse1")
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Passwords do not match."));

        assertTrue(clientPasswordIs(OLD_PASSWORD));
        assertFalse(onlyToken().isUsed());

        // The user can still finish the reset with the same token.
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
    }

    /*
     * Checks: A short password is refused and the token stays usable.
     * How:    "short1" gives 400, the password is unchanged, and the token is not used.
     */
    @Test
    void aShortPassword_isRejectedAndKeepsTheTokenUsable() throws Exception {
        String token = requestedClientToken();

        reset(token, "short1", "short1").andExpect(status().isBadRequest());

        assertTrue(clientPasswordIs(OLD_PASSWORD));
        assertFalse(onlyToken().isUsed());
    }

    /*
     * Checks: Missing fields are refused.
     * How:    An empty JSON body gives 400.
     */
    @Test
    void missingFields_areRejected() throws Exception {
        mvc.perform(post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------- admins

    /*
     * Checks: An admin reset clears the login lock, and the new password works.
     * How:    The admin has 5 failed attempts and is locked. After the reset the attempts are 0, the
     *   lock is empty, and the new password works.
     */
    @Test
    void anAdminReset_clearsTheLoginLockAndTheNewPasswordWorks() throws Exception {
        AdminUser admin = admins.findByEmail("admin@test.com").orElseThrow();
        admin.setFailedLoginAttempts(5);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        admins.save(admin);

        String token = requestedToken("admin@test.com");
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        AdminUser after = admins.findByEmail("admin@test.com").orElseThrow();
        assertEquals(0, after.getFailedLoginAttempts());
        assertNull(after.getLockedUntil());

        // The admin login is a two-step login: the password is accepted
        // and an email code is requested.
        login("admin@test.com", NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true));
    }
}