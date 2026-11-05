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

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.service.AdminAuthService;
import ca.sheridancollege.shtirthb.service.JwtService;

/**
 * UC-03: HTTP-level tests for /api/admin/login
 * Uses a standalone MockMvc (no database, no Spring context) so it runs fast.
 *
 * This file has 4 tests:
 *   Test 1 - Correct admin details -> 200 with token (token has role ADMIN)
 *   Test 2 - Wrong admin details -> 401 Unauthorized
 *   Test 3 - Wrong email format -> 400
 *   Test 4 - Account is locked after 5 failed attempts -> 423 Locked
 */
class AdminAuthControllerTest {

    // Fake services, so only the controller is tested
    private AdminAuthService adminAuthService;
    private JwtService jwtService;

    // MockMvc sends fake HTTP requests to the controller
    private MockMvc mvc;

    // Runs before every test: creates fresh fakes and a fresh controller
    @BeforeEach
    void setUp() {
        adminAuthService = mock(AdminAuthService.class);
        jwtService = mock(JwtService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AdminAuthController(adminAuthService, jwtService))
                .build();
    }

    /*
     * TEST 1: Successful admin login
     * Checks that:
     *   - The response is 200 OK
     *   - The admin token is sent back in the response
     *   - The token is made with the role "ADMIN"
     */
    @Test
    void adminLogin_returns200_withToken_whenCredentialsCorrect() throws Exception {
        // Arrange: admin details are correct, token service returns a fake token
        AdminUser admin = new AdminUser("Administrator", "admin@test.com", "ENCODED");
        when(adminAuthService.authenticate("admin@test.com", "Admin@12345")).thenReturn(admin);
        when(jwtService.generateToken("admin@test.com", "ADMIN")).thenReturn("ADMIN.JWT");

        // Act: send an admin login request
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"Admin@12345\"}"))
                // Assert: 200 and the token
                .andExpect(status().isOk())
                .andExpect(content().string("ADMIN.JWT"));
    }

    /*
     * TEST 2: Wrong admin details
     * Checks that:
     *   - The response is 401 Unauthorized with "Invalid admin email or password"
     *   - No token is created
     */
    @Test
    void adminLogin_returns401_whenCredentialsWrong() throws Exception {
        // Arrange: admin login fails
        when(adminAuthService.authenticate(anyString(), anyString())).thenReturn(null);

        // Act: send an admin login request with a wrong password
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"wrong\"}"))
                // Assert: 401 and error message
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid admin email or password"));

        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    /*
     * TEST 3: Wrong email format
     * Checks that:
     *   - The response is 400 Bad Request
     *   - The service is never called (blocked by validation)
     */
    @Test
    void adminLogin_returns400_whenEmailInvalid() throws Exception {
        // Act: send an admin login request with a bad email
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin\",\"password\":\"Admin@12345\"}"))
                // Assert: 400
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminAuthService);
    }

    /*
     * TEST 4: Admin account is locked
     * Checks that:
     *   - The response is 423 Locked
     *   - The message tells the admin to use the password reset option
     *   - No token is created
     */
    @Test
    void adminLogin_returns423_whenAccountLocked() throws Exception {
        // Arrange: service says the account is locked
        when(adminAuthService.authenticate(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Admin account is temporarily locked"));

        // Act: send an admin login request
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"Admin@12345\"}"))
                // Assert: 423 and lock message
                .andExpect(status().isLocked())
                .andExpect(content().string(
                        "Admin account is temporarily locked. Please use the password reset option."));

        verify(jwtService, never()).generateToken(anyString(), anyString());
    }
}
