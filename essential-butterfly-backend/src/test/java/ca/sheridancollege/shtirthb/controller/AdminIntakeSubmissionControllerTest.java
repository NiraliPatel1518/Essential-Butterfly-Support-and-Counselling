package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.List;
import ca.sheridancollege.shtirthb.dto.IntakeAdminResponse;
import ca.sheridancollege.shtirthb.dto.RejectIntakeRequest;
import ca.sheridancollege.shtirthb.service.IntakeSubmissionService;

@ExtendWith(MockitoExtension.class)
class AdminIntakeSubmissionControllerTest {
    @Mock IntakeSubmissionService service;
    @InjectMocks AdminIntakeSubmissionController controller;

    /*
     * Checks: The admin can list all intake submissions.
     * How:    The answer is 200 and the service getAllSubmissions is called.
     */
    @Test void listsSubmissions() {
        when(service.getAllSubmissions()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getAllSubmissions().getStatusCode());
        verify(service).getAllSubmissions();
    }
    /*
     * Checks: The admin can open one intake submission by id.
     * How:    The fake service returns a submission for id 3, and the controller returns the same
     *   object.
     */
    @Test void getsSubmission() {
        IntakeAdminResponse expected = mock(IntakeAdminResponse.class);
        when(service.getSubmissionById(3L)).thenReturn(expected);
        assertSame(expected, controller.getSubmissionById(3L).getBody());
    }
    /*
     * Checks: The admin can accept an intake submission.
     * How:    The controller calls acceptSubmission(3) on the service and returns its result.
     */
    @Test void acceptsSubmission() {
        IntakeAdminResponse expected = mock(IntakeAdminResponse.class);
        when(service.acceptSubmission(3L)).thenReturn(expected);
        assertSame(expected, controller.acceptSubmission(3L).getBody());
        verify(service).acceptSubmission(3L);
    }
    /*
     * Checks: The admin can reject a submission with a reason.
     * How:    The reason "Outside service scope" is set on the request. The service must be called
     *   with id 3 and that reason.
     */
    @Test void rejectsSubmissionWithReason() {
        RejectIntakeRequest request = new RejectIntakeRequest();
        request.setRejectionReason("Outside service scope");
        IntakeAdminResponse expected = mock(IntakeAdminResponse.class);
        when(service.rejectSubmission(3L, "Outside service scope")).thenReturn(expected);
        assertSame(expected, controller.rejectSubmission(3L, request).getBody());
        verify(service).rejectSubmission(3L, "Outside service scope");
    }
}
