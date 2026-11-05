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

import ca.sheridancollege.shtirthb.dto.IntakeClientStatusResponse;
import ca.sheridancollege.shtirthb.dto.IntakeSubmissionRequest;
import ca.sheridancollege.shtirthb.service.IntakeSubmissionService;

@ExtendWith(MockitoExtension.class)
class IntakeSubmissionControllerTest {
    @Mock IntakeSubmissionService service;
    @Mock Authentication authentication;
    @InjectMocks IntakeSubmissionController controller;

    @Test void submitsIntakeForAuthenticatedClient() {
        when(authentication.getName()).thenReturn("client@example.com");
        IntakeSubmissionRequest request = mock(IntakeSubmissionRequest.class);
        var response = controller.submitIntake(request, authentication);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).submitIntake(request, "client@example.com");
    }
    @Test void returnsClientIntakeStatus() {
        when(authentication.getName()).thenReturn("client@example.com");
        IntakeClientStatusResponse expected = mock(IntakeClientStatusResponse.class);
        when(service.getClientIntakeStatus("client@example.com")).thenReturn(expected);
        assertSame(expected, controller.getIntakeStatus(authentication).getBody());
    }
    @Test void returnsNotFoundWhenClientHasNoIntake() {
        when(authentication.getName()).thenReturn("client@example.com");
        when(service.getClientIntakeStatus("client@example.com")).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND, controller.getIntakeStatus(authentication).getStatusCode());
    }
}
