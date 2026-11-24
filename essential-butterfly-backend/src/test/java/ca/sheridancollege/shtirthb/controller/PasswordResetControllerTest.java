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

import ca.sheridancollege.shtirthb.dto.ResetPasswordRequest;
import ca.sheridancollege.shtirthb.service.PasswordResetService;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {
    @Mock PasswordResetService service;
    @InjectMocks PasswordResetController controller;

    /*
     * Checks: A forgot-password request with an invalid email is refused.
     * How:    We send "not-an-email". The answer is 400, and the service is never used.
     */
    @Test void rejectsInvalidEmail() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.forgotPassword("not-an-email").getStatusCode());
        verifyNoInteractions(service);
    }
    /*
     * Checks: The email is trimmed and lower-cased before the reset is requested.
     * How:    We send " USER@Example.com ". The service is called with user@example.com.
     */
    @Test void normalizesEmailBeforeRequestingReset() {
        var response = controller.forgotPassword("  USER@Example.com ");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).requestPasswordReset("user@example.com");
    }
    /*
     * Checks: A reset with two different passwords is refused.
     * How:    New and confirm passwords differ. The answer is 400, and the service is never used.
     */
    @Test void rejectsMismatchedPasswords() {
        ResetPasswordRequest request = mock(ResetPasswordRequest.class);
        when(request.getNewPassword()).thenReturn("Password123");
        when(request.getConfirmPassword()).thenReturn("Different123");
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPassword(request).getStatusCode());
        verifyNoInteractions(service);
    }
    /*
     * Checks: A bad token gives 400.
     * How:    The passwords match, but the service says the reset failed (false). The answer is 400.
     */
    @Test void returnsBadRequestWhenTokenCannotResetPassword() {
        ResetPasswordRequest request = mock(ResetPasswordRequest.class);
        when(request.getNewPassword()).thenReturn("Password123");
        when(request.getConfirmPassword()).thenReturn("Password123");
        when(request.getToken()).thenReturn("invalid");
        when(service.resetPassword("invalid","Password123")).thenReturn(false);
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPassword(request).getStatusCode());
    }
    /*
     * Checks: A good token and matching passwords give 200.
     * How:    The service returns true for the token and password. The answer is 200.
     */
    @Test void returnsOkWhenPasswordResetSucceeds() {
        ResetPasswordRequest request = mock(ResetPasswordRequest.class);
        when(request.getNewPassword()).thenReturn("Password123");
        when(request.getConfirmPassword()).thenReturn("Password123");
        when(request.getToken()).thenReturn("valid");
        when(service.resetPassword("valid","Password123")).thenReturn(true);
        assertEquals(HttpStatus.OK, controller.resetPassword(request).getStatusCode());
    }
}
