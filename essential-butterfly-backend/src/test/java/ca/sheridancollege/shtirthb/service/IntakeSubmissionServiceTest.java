package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.sheridancollege.shtirthb.dto.IntakeSubmissionRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.IntakeSubmission;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.IntakeSubmissionRepository;

@ExtendWith(MockitoExtension.class)
class IntakeSubmissionServiceTest {

    @Mock
    private IntakeSubmissionRepository intakeSubmissionRepository;

    @Mock
    private ClientUserRepository clientUserRepository;

    @InjectMocks
    private IntakeSubmissionService intakeSubmissionService;

    private final List<String> pendingStatuses =
            List.of("NEW", "SUBMITTED", "IN_PROGRESS");

    // The service now requires a verified phone number before an intake can be submitted.
    private ClientUser verifiedClient() {
        ClientUser user = new ClientUser("Alex Lee", "alex@mail.com", "ENCODED");
        user.setPhoneNumber("+14165551234");
        return user;
    }

    private IntakeSubmissionRequest validRequest() {
        IntakeSubmissionRequest request = new IntakeSubmissionRequest();
        request.setClientFullName("Sam Lee");
        request.setParentGuardianName("Alex Lee");
        request.setContactInfo("alex@mail.com");
        request.setPreferredContactMethod("Email");
        request.setOverview("Overview text");
        request.setSupportNeeds("Support needs text");
        request.setRespiteGoals("Respite goals");
        request.setSubjectFocus("Math");
        request.setAdditionalNotes("None");
        return request;
    }

    /*
     * Checks: A valid intake is saved and linked to the logged-in client.
     * How:    Mockito fake repositories. We capture the saved object and check the client, each form
     *   field, status SUBMITTED and a created date.
     */
    @Test
    void submitIntake_savesSubmission_linkedToLoggedInUser() {
        ClientUser user = verifiedClient();

        when(clientUserRepository.findByEmail("alex@mail.com"))
                .thenReturn(Optional.of(user));

        when(intakeSubmissionRepository.existsByClientUserAndStatusIn(
                user, pendingStatuses)).thenReturn(false);

        intakeSubmissionService.submitIntake(validRequest(), "alex@mail.com");

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
        assertEquals("SUBMITTED", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    /*
     * Checks: An unknown account is refused.
     * How:    findByEmail returns empty. The service throws ResourceNotFoundException ("User account
     *   not found.") and saves nothing.
     */
    @Test
    void submitIntake_throwsError_whenUserAccountNotFound() {
        when(clientUserRepository.findByEmail("ghost@mail.com"))
                .thenReturn(Optional.empty());

        AppExceptions.ResourceNotFoundException exception =
                assertThrows(AppExceptions.ResourceNotFoundException.class,
                        () -> intakeSubmissionService.submitIntake(
                                validRequest(), "ghost@mail.com"));

        assertEquals("User account not found.", exception.getMessage());

        verify(intakeSubmissionRepository, never()).save(any());
        verify(intakeSubmissionRepository, never())
                .existsByClientUserAndStatusIn(any(), any());
    }

    /*
     * Checks: A second intake while one is pending is refused.
     * How:    The repository says a NEW, SUBMITTED or IN_PROGRESS intake exists. The service throws
     *   ConflictException and saves nothing.
     */
    @Test
    void submitIntake_throwsError_whenPendingIntakeAlreadyExists() {
        ClientUser user = verifiedClient();

        when(clientUserRepository.findByEmail("alex@mail.com"))
                .thenReturn(Optional.of(user));

        when(intakeSubmissionRepository.existsByClientUserAndStatusIn(
                user, pendingStatuses)).thenReturn(true);

        AppExceptions.ConflictException exception =
                assertThrows(AppExceptions.ConflictException.class,
                        () -> intakeSubmissionService.submitIntake(
                                validRequest(), "alex@mail.com"));

        assertEquals(
                "You already have an intake request under review. "
                + "Please wait until a decision has been made.",
                exception.getMessage());

        verify(intakeSubmissionRepository, never()).save(any());
    }

    /*
     * Checks: The pending check uses all three pending statuses before saving.
     * How:    We verify existsByClientUserAndStatusIn was called with NEW, SUBMITTED, IN_PROGRESS, and
     *   then save() was called.
     */
    @Test
    void submitIntake_checksAllPendingStatusesBeforeSaving() {
        ClientUser user = verifiedClient();

        when(clientUserRepository.findByEmail("alex@mail.com"))
                .thenReturn(Optional.of(user));

        when(intakeSubmissionRepository.existsByClientUserAndStatusIn(
                user, pendingStatuses)).thenReturn(false);

        intakeSubmissionService.submitIntake(validRequest(), "alex@mail.com");

        verify(intakeSubmissionRepository)
                .existsByClientUserAndStatusIn(user, pendingStatuses);

        verify(intakeSubmissionRepository)
                .save(any(IntakeSubmission.class));
    }

    /*
     * Checks: An unverified phone number is refused.
     * How:    The client has no phone number. The service throws BusinessRuleException ("Please verify
     *   your phone number...") before any check or save.
     */
    @Test
    void submitIntake_throwsError_whenPhoneNumberIsNotVerified() {
        ClientUser user = new ClientUser("Alex Lee", "alex@mail.com", "ENCODED");

        when(clientUserRepository.findByEmail("alex@mail.com"))
                .thenReturn(Optional.of(user));

        AppExceptions.BusinessRuleException exception =
                assertThrows(AppExceptions.BusinessRuleException.class,
                        () -> intakeSubmissionService.submitIntake(
                                validRequest(), "alex@mail.com"));

        assertEquals(
                "Please verify your phone number before submitting an intake request.",
                exception.getMessage());

        verify(intakeSubmissionRepository, never()).save(any());
        verify(intakeSubmissionRepository, never())
                .existsByClientUserAndStatusIn(any(), any());
    }
}