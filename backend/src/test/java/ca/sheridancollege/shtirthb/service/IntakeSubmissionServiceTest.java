package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.sheridancollege.shtirthb.dto.IntakeSubmissionRequest;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.IntakeSubmissionRepository;

/**
 * UC-02: Submit Intake Request
 * Unit tests for IntakeSubmissionService. The database is mocked,
 * so no real database is needed.
 *
 * This file has 2 tests:
 *   Test 1 - Logged-in user submits the form -> submission is saved with status NEW
 *   Test 2 - User account not found -> error, nothing is saved
 */
@ExtendWith(MockitoExtension.class)
class IntakeSubmissionServiceTest {

    // Fake database for intake submissions
    @Mock
    private IntakeSubmissionRepository intakeSubmissionRepository;

    // Fake database for client accounts
    @Mock
    private ClientUserRepository clientUserRepository;

    // The real IntakeSubmissionService, using the fake databases above
    @InjectMocks
    private IntakeSubmissionService intakeSubmissionService;

    // Helper: builds an intake form with all fields filled in
    private IntakeSubmissionRequest validRequest() {
        IntakeSubmissionRequest r = new IntakeSubmissionRequest();
        r.setClientFullName("Sam Lee");
        r.setParentGuardianName("Alex Lee");
        r.setContactInfo("alex@mail.com");
        r.setPreferredContactMethod("Email");
        r.setOverview("Overview text");
        r.setSupportNeeds("Support needs text");
        r.setRespiteGoals("Respite goals");
        r.setSubjectFocus("Math");
        r.setAdditionalNotes("None");
        return r;
    }

    /*
     * TEST 1: Successful intake submission
     * Checks that:
     *   - The submission is linked to the logged-in user
     *   - All form fields are saved correctly
     *   - The status is "NEW" and the created date is set
     */
    @Test
    void submitIntake_savesSubmission_linkedToLoggedInUser_withStatusNew() {
        // Arrange: the logged-in user exists
        ClientUser user = new ClientUser("Alex Lee", "alex@mail.com", "ENCODED");
        when(clientUserRepository.findByEmail("alex@mail.com")).thenReturn(Optional.of(user));

        // Act: submit the intake form
        intakeSubmissionService.submitIntake(validRequest(), "alex@mail.com");

        // Assert: capture the saved submission and check its values
        ArgumentCaptor<IntakeSubmission> captor =
                ArgumentCaptor.forClass(IntakeSubmission.class);
        verify(intakeSubmissionRepository).save(captor.capture());

        IntakeSubmission saved = captor.getValue();
        assertSame(user, saved.getClientUser());
        assertEquals("Sam Lee", saved.getClientFullName());
        assertEquals("Alex Lee", saved.getParentGuardianName());
        assertEquals("alex@mail.com", saved.getContactInfo());
        assertEquals("Email", saved.getPreferredContactMethod());
        assertEquals("Overview text", saved.getOverview());
        assertEquals("Support needs text", saved.getSupportNeeds());
        assertEquals("NEW", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    /*
     * TEST 2: User account not found
     * Checks that:
     *   - The error "User account not found" is thrown
     *   - No submission is saved
     */
    @Test
    void submitIntake_throwsError_whenUserAccountNotFound() {
        // Arrange: no user with this email
        when(clientUserRepository.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

        // Act + Assert: submit throws an error
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> intakeSubmissionService.submitIntake(validRequest(), "ghost@mail.com"));

        assertEquals("User account not found", ex.getMessage());
        verify(intakeSubmissionRepository, never()).save(any());
    }
}