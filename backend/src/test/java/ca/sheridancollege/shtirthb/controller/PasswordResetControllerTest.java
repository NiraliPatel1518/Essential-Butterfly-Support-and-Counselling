package ca.sheridancollege.shtirthb.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ca.sheridancollege.shtirthb.service.PasswordResetService;

/**
 * UC-01 / UC-03: HTTP-level tests for /api/password/forgot and /api/password/reset
 * Uses a standalone MockMvc (no database, no Spring context) so it runs fast.
 *
 * This file has 8 tests:
 *
 *   Forgot password (4 tests)
 *     Test 1 - Client request -> 200, client reset is called
 *     Test 2 - Admin request -> 200, admin reset is called
 *     Test 3 - Unknown account type -> 400
 *     Test 4 - Unknown email -> same general message (does not reveal if email exists)
 *
 *   Reset password (4 tests)
 *     Test 5 - Valid token -> 200
 *     Test 6 - Passwords do not match -> 400
 *     Test 7 - Invalid or expired token -> 400
 *     Test 8 - New password too short -> 400
 */
class PasswordResetControllerTest {

    // Fake service, so only the controller is tested
    private PasswordResetService service;

    // MockMvc sends fake HTTP requests to the controller
    private MockMvc mvc;

    // Runs before every test: creates a fresh fake and a fresh controller
    @BeforeEach
    void setUp() {
        service = mock(PasswordResetService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new PasswordResetController(service))
                .build();
    }

    // ---------- forgot ----------

    /*
     * TEST 1: Client asks for a reset link
     * Checks that:
     *   - The response is 200 OK
     *   - Only the client reset is called (not the admin one)
     */
    @Test
    void forgot_client_returns200_andCallsClientReset() throws Exception {
        // Act: send a forgot-password request with type=CLIENT
        mvc.perform(post("/api/password/forgot")
                        .param("type", "CLIENT")
                        .param("email", "jane@mail.com"))
                // Assert: 200
                .andExpect(status().isOk());

        verify(service).requestClientPasswordReset("jane@mail.com");
        verify(service, never()).requestAdminPasswordReset(anyString());
    }

    /*
     * TEST 2: Admin asks for a reset link
     * Checks that:
     *   - The response is 200 OK
     *   - The admin reset is called
     *   - "admin" in lowercase also works
     */
    @Test
    void forgot_admin_returns200_andCallsAdminReset() throws Exception {
        // Act: send a forgot-password request with type=admin
        mvc.perform(post("/api/password/forgot")
                        .param("type", "admin")
                        .param("email", "admin@test.com"))
                // Assert: 200
                .andExpect(status().isOk());

        verify(service).requestAdminPasswordReset("admin@test.com");
    }

    /*
     * TEST 3: Unknown account type
     * Checks that:
     *   - The response is 400 with "Invalid account type"
     *   - The service is never called
     */
    @Test
    void forgot_returns400_whenTypeIsUnknown() throws Exception {
        // Act: send a request with a type that is not CLIENT or ADMIN
        mvc.perform(post("/api/password/forgot")
                        .param("type", "HACKER")
                        .param("email", "x@mail.com"))
                // Assert: 400 and error message
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid account type"));

        verifyNoInteractions(service);
    }

    /*
     * TEST 4: Email is not registered
     * Checks that:
     *   - The response is 200 with the same general message
     *   - So nobody can find out which emails have accounts
     */
    @Test
    void forgot_sameMessage_whetherOrNotEmailExists() throws Exception {
        // The service silently does nothing for unknown emails,
        // so the response must not reveal whether an account exists.
        mvc.perform(post("/api/password/forgot")
                        .param("type", "CLIENT")
                        .param("email", "unknown@mail.com"))
                // Assert: 200 and the general message
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "If an account exists with this email, a password reset link has been generated."));
    }

    // ---------- reset ----------

    /*
     * TEST 5: Successful password reset
     * Checks that:
     *   - The response is 200 with "Password reset successfully"
     */
    @Test
    void reset_returns200_whenTokenValid() throws Exception {
        // Arrange: service accepts the token
        when(service.resetPassword("abc", "NewPassword1")).thenReturn(true);

        // Act: send a reset request
        mvc.perform(post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"abc","newPassword":"NewPassword1",
                                 "confirmPassword":"NewPassword1"}
                                """))
                // Assert: 200 and success message
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successfully"));
    }

    /*
     * TEST 6: Passwords do not match
     * Checks that:
     *   - The response is 400 with "Passwords do not match"
     *   - The service is never called
     */
    @Test
    void reset_returns400_whenPasswordsDoNotMatch() throws Exception {
        // Act: send a reset request with two different passwords
        mvc.perform(post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"abc","newPassword":"NewPassword1",
                                 "confirmPassword":"Different1"}
                                """))
                // Assert: 400 and error message
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Passwords do not match"));

        verifyNoInteractions(service);
    }

    /*
     * TEST 7: Invalid or expired token
     * Checks that:
     *   - The response is 400 with the token error message
     */
    @Test
    void reset_returns400_whenTokenInvalidOrExpired() throws Exception {
        // Arrange: service rejects the token
        when(service.resetPassword("old", "NewPassword1")).thenReturn(false);

        // Act: send a reset request with an old token
        mvc.perform(post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"old","newPassword":"NewPassword1",
                                 "confirmPassword":"NewPassword1"}
                                """))
                // Assert: 400 and error message
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid, expired, or already used reset token"));
    }

    /*
     * TEST 8: New password too short
     * Checks that:
     *   - The response is 400 Bad Request
     *   - The service is never called (blocked by validation)
     */
    @Test
    void reset_returns400_whenNewPasswordTooShort() throws Exception {
        // Act: send a reset request with a 5-character password
        mvc.perform(post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"abc","newPassword":"short","confirmPassword":"short"}
                                """))
                // Assert: 400
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}