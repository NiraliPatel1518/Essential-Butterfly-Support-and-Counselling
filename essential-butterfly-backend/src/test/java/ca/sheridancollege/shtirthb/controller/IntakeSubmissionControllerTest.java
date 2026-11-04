package ca.sheridancollege.shtirthb.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ca.sheridancollege.shtirthb.service.IntakeSubmissionService;

/**
 * UC-02: HTTP-level tests for POST /api/intake
 * Uses a standalone MockMvc (no database, no Spring context) so it runs fast.
 * The logged-in user is simulated with a TestingAuthenticationToken.
 *
 * This file has 3 tests:
 *   Test 1 - Valid form from a logged-in user -> 200, email is taken from the token
 *   Test 2 - Required fields are empty -> 400
 *   Test 3 - User account not found (e.g. admin token) -> 400 with error message
 */
class IntakeSubmissionControllerTest {

    // Fake service, so only the controller is tested
    private IntakeSubmissionService service;

    // MockMvc sends fake HTTP requests to the controller
    private MockMvc mvc;

    // A valid intake form body (JSON) used in several tests
    private static final String VALID_INTAKE = """
            {"clientFullName":"Sam Lee","parentGuardianName":"Alex Lee",
             "contactInfo":"alex@mail.com","preferredContactMethod":"Email",
             "overview":"Overview","supportNeeds":"Needs",
             "respiteGoals":"","subjectFocus":"","additionalNotes":""}
            """;

    // Runs before every test: creates a fresh fake and a fresh controller
    @BeforeEach
    void setUp() {
        service = mock(IntakeSubmissionService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new IntakeSubmissionController(service))
                .build();
    }

    /*
     * TEST 1: Successful intake submission
     * Checks that:
     *   - The response is 200 with "Intake submission received successfully"
     *   - The user's email is taken from the login token, not from the form
     */
    @Test
    void submit_returns200_andUsesEmailFromToken() throws Exception {
        // Act: send a valid form as logged-in user alex@mail.com
        mvc.perform(post("/api/intake")
                        .principal(new TestingAuthenticationToken("alex@mail.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INTAKE))
                // Assert: 200 and success message
                .andExpect(status().isOk())
                .andExpect(content().string("Intake submission received successfully"));

        verify(service).submitIntake(any(), eq("alex@mail.com"));
    }

    /*
     * TEST 2: Required fields are empty
     * Checks that:
     *   - The response is 400 Bad Request
     *   - The service is never called (blocked by validation)
     */
    @Test
    void submit_returns400_whenRequiredFieldsMissing() throws Exception {
        // Act: send a form with all required fields empty
        mvc.perform(post("/api/intake")
                        .principal(new TestingAuthenticationToken("alex@mail.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientFullName":"","contactInfo":"",
                                 "preferredContactMethod":"","overview":"","supportNeeds":""}
                                """))
                // Assert: 400
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    /*
     * TEST 3: User account not found
     * Checks that:
     *   - The response is 400 with "User account not found"
     *   - Example: an admin token is used, but admins are not client accounts
     */
    @Test
    void submit_returns400_whenUserAccountNotFound() throws Exception {
        // Arrange: service cannot find a client account for this email
        doThrow(new IllegalArgumentException("User account not found"))
                .when(service).submitIntake(any(), eq("admin@test.com"));

        // Act: send a valid form using the admin's email in the token
        mvc.perform(post("/api/intake")
                        .principal(new TestingAuthenticationToken("admin@test.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INTAKE))
                // Assert: 400 and error message
                .andExpect(status().isBadRequest())
                .andExpect(content().string("User account not found"));
    }
}