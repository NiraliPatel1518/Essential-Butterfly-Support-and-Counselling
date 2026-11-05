package ca.sheridancollege.shtirthb.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ca.sheridancollege.shtirthb.service.AuthService;
import ca.sheridancollege.shtirthb.service.ContactMessageService;
import ca.sheridancollege.shtirthb.service.JwtService;
import ca.sheridancollege.shtirthb.service.PasswordResetService;

/**
 * UC-01 / UC-02 / UC-03: Email format validation on the API
 * An email must look like name@example.com (it needs a dot and an ending like .com).
 * Uses a standalone MockMvc (no database, no Spring context) so it runs fast.
 *
 * This file has 7 tests:
 *   Test 1 - Signup with an email that has no ending (jane@mail) -> 400
 *   Test 2 - Login with an email that has no ending -> 400
 *   Test 3 - Contact message with an email that has no ending -> 400
 *   Test 4 - Contact message with a correct email -> 200
 *   Test 5 - Forgot password with a wrong email -> 400 with a clear message
 *   Test 6 - Forgot password cleans the email (spaces, capital letters) before using it
 *   Test 7 - Forgot password for admin also cleans the email
 */
class EmailFormatValidationTest {

    // Fake services, so only the controllers are tested
    private AuthService authService;
    private JwtService jwtService;
    private ContactMessageService contactService;
    private PasswordResetService passwordResetService;

    // MockMvc sends fake HTTP requests to the controllers
    private MockMvc mvc;

    // Runs before every test: creates fresh fakes and fresh controllers
    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        contactService = mock(ContactMessageService.class);
        passwordResetService = mock(PasswordResetService.class);

        mvc = MockMvcBuilders
                .standaloneSetup(
                        new AuthController(authService, jwtService),
                        new ContactMessageController(contactService),
                        new PasswordResetController(passwordResetService))
                .build();
    }

    /*
     * TEST 1: Signup with an incomplete email
     * Checks that:
     *   - "jane@mail" (no .com) is rejected with 400
     *   - The account is not created
     */
    @Test
    void signup_rejectsEmailWithoutEnding() throws Exception {
        // Act: sign up with "jane@mail"
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane Doe","email":"jane@mail",
                                 "password":"Password123","confirmPassword":"Password123"}
                                """))
                // Assert: 400
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    /*
     * TEST 2: Login with an incomplete email
     * Checks that:
     *   - "jane@mail" is rejected with 400
     *   - No login check and no token
     */
    @Test
    void login_rejectsEmailWithoutEnding() throws Exception {
        // Act: log in with "jane@mail"
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@mail\",\"password\":\"Password123\"}"))
                // Assert: 400
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService, jwtService);
    }

    /*
     * TEST 3: Contact message with an incomplete email
     * Checks that:
     *   - "jane@mail" is rejected with 400
     *   - The message is not saved
     */
    @Test
    void contact_rejectsEmailWithoutEnding() throws Exception {
        // Act: send a contact message with "jane@mail"
        mvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane@mail",
                                 "subject":"Question","message":"Hello"}
                                """))
                // Assert: 400
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contactService);
    }

    /*
     * TEST 4: Contact message with a correct email
     * Checks that:
     *   - A normal email like jane.doe+test@mail.co is accepted
     */
    @Test
    void contact_acceptsNormalEmail() throws Exception {
        // Act: send a contact message with a correct email
        mvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane","email":"jane.doe+test@mail.co",
                                 "subject":"Question","message":"Hello"}
                                """))
                // Assert: 200 and saved
                .andExpect(status().isOk());

        verify(contactService).submitMessage(any());
    }

    /*
     * TEST 5: Forgot password with a wrong email
     * Checks that:
     *   - The response is 400 with "Please enter a valid email address"
     *   - No reset link is created
     */
    @Test
    void forgotPassword_rejectsWrongEmail() throws Exception {
        // Act: ask for a reset link with "not-an-email"
        mvc.perform(post("/api/password/forgot")
                        .param("type", "CLIENT")
                        .param("email", "not-an-email"))
                // Assert: 400 and message
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Please enter a valid email address"));

        verifyNoInteractions(passwordResetService);
    }

    /*
     * TEST 6: Forgot password cleans the client email
     * Checks that:
     *   - "  Jane@Mail.COM " is changed to "jane@mail.com" before it is used
     */
    @Test
    void forgotPassword_client_usesCleanEmail() throws Exception {
        // Act: ask for a reset link with spaces and capital letters
        mvc.perform(post("/api/password/forgot")
                        .param("type", "CLIENT")
                        .param("email", "  Jane@Mail.COM "))
                // Assert: 200
                .andExpect(status().isOk());

        // Assert: the cleaned email was used
        verify(passwordResetService).requestClientPasswordReset("jane@mail.com");
    }

    /*
     * TEST 7: Forgot password cleans the admin email
     * Checks that:
     *   - "ADMIN@Test.com" is changed to "admin@test.com" before it is used
     *   - Only the admin reset is called
     */
    @Test
    void forgotPassword_admin_usesCleanEmail() throws Exception {
        // Act: ask for an admin reset link with capital letters
        mvc.perform(post("/api/password/forgot")
                        .param("type", "ADMIN")
                        .param("email", "ADMIN@Test.com"))
                // Assert: 200
                .andExpect(status().isOk());

        // Assert: the cleaned email was used for the admin reset only
        verify(passwordResetService).requestAdminPasswordReset("admin@test.com");
        verify(passwordResetService, never()).requestClientPasswordReset(anyString());
    }
}
