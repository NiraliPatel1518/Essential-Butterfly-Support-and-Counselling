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

    /*
     * Checks: A signed-in client can submit the intake form.
     * How:    The service submitIntake is verified to be called with the request and
     *   client@example.com.
     */
    @Test void submitsIntakeForAuthenticatedClient() {
        when(authentication.getName()).thenReturn("client@example.com");
        IntakeSubmissionRequest request = mock(IntakeSubmissionRequest.class);
        var response = controller.submitIntake(request, authentication);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).submitIntake(request, "client@example.com");
    }
    /*
     * Checks: The client can see the status of their intake.
     * How:    The fake service returns a status for the client's email, and the controller returns it.
     */
    @Test void returnsClientIntakeStatus() {
        when(authentication.getName()).thenReturn("client@example.com");
        IntakeClientStatusResponse expected = mock(IntakeClientStatusResponse.class);
        when(service.getClientIntakeStatus("client@example.com")).thenReturn(expected);
        assertSame(expected, controller.getIntakeStatus(authentication).getBody());
    }
    /*
     * Checks: A client with no intake gets 404.
     * How:    The fake service returns null. The status of the controller answer is NOT_FOUND.
     */
    @Test void returnsNotFoundWhenClientHasNoIntake() {
        when(authentication.getName()).thenReturn("client@example.com");
        when(service.getClientIntakeStatus("client@example.com")).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND, controller.getIntakeStatus(authentication).getStatusCode());
    }
}
