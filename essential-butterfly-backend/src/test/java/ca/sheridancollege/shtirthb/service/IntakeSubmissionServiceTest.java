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

    @Test
    void submitIntake_savesSubmission_linkedToLoggedInUser() {
        ClientUser user =
                new ClientUser("Alex Lee", "alex@mail.com", "ENCODED");

        when(clientUserRepository.findByEmail("alex@mail.com"))
                .thenReturn(Optional.of(user));

        when(intakeSubmissionRepository.existsByClientUserAndStatusIn(
                user, pendingStatuses)).thenReturn(false);

        intakeSubmissionService.submitIntake(
                validRequest(), "alex@mail.com");

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

    @Test
    void submitIntake_throwsError_whenPendingIntakeAlreadyExists() {
        ClientUser user =
                new ClientUser("Alex Lee", "alex@mail.com", "ENCODED");

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

    @Test
    void submitIntake_checksAllPendingStatusesBeforeSaving() {
        ClientUser user =
                new ClientUser("Alex Lee", "alex@mail.com", "ENCODED");

        when(clientUserRepository.findByEmail("alex@mail.com"))
                .thenReturn(Optional.of(user));

        when(intakeSubmissionRepository.existsByClientUserAndStatusIn(
                user, pendingStatuses)).thenReturn(false);

        intakeSubmissionService.submitIntake(
                validRequest(), "alex@mail.com");

        verify(intakeSubmissionRepository)
                .existsByClientUserAndStatusIn(user, pendingStatuses);

        verify(intakeSubmissionRepository)
                .save(any(IntakeSubmission.class));
    }
}