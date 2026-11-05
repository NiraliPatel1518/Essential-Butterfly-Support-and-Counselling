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

    @Test void listsSubmissions() {
        when(service.getAllSubmissions()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getAllSubmissions().getStatusCode());
        verify(service).getAllSubmissions();
    }
    @Test void getsSubmission() {
        IntakeAdminResponse expected = mock(IntakeAdminResponse.class);
        when(service.getSubmissionById(3L)).thenReturn(expected);
        assertSame(expected, controller.getSubmissionById(3L).getBody());
    }
    @Test void acceptsSubmission() {
        IntakeAdminResponse expected = mock(IntakeAdminResponse.class);
        when(service.acceptSubmission(3L)).thenReturn(expected);
        assertSame(expected, controller.acceptSubmission(3L).getBody());
        verify(service).acceptSubmission(3L);
    }
    @Test void rejectsSubmissionWithReason() {
        RejectIntakeRequest request = new RejectIntakeRequest();
        request.setRejectionReason("Outside service scope");
        IntakeAdminResponse expected = mock(IntakeAdminResponse.class);
        when(service.rejectSubmission(3L, "Outside service scope")).thenReturn(expected);
        assertSame(expected, controller.rejectSubmission(3L, request).getBody());
        verify(service).rejectSubmission(3L, "Outside service scope");
    }
}
