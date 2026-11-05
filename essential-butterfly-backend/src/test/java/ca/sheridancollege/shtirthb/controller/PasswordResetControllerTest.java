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

    @Test void rejectsInvalidEmail() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.forgotPassword("not-an-email").getStatusCode());
        verifyNoInteractions(service);
    }
    @Test void normalizesEmailBeforeRequestingReset() {
        var response = controller.forgotPassword("  USER@Example.com ");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).requestPasswordReset("user@example.com");
    }
    @Test void rejectsMismatchedPasswords() {
        ResetPasswordRequest request = mock(ResetPasswordRequest.class);
        when(request.getNewPassword()).thenReturn("Password123");
        when(request.getConfirmPassword()).thenReturn("Different123");
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPassword(request).getStatusCode());
        verifyNoInteractions(service);
    }
    @Test void returnsBadRequestWhenTokenCannotResetPassword() {
        ResetPasswordRequest request = mock(ResetPasswordRequest.class);
        when(request.getNewPassword()).thenReturn("Password123");
        when(request.getConfirmPassword()).thenReturn("Password123");
        when(request.getToken()).thenReturn("invalid");
        when(service.resetPassword("invalid","Password123")).thenReturn(false);
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPassword(request).getStatusCode());
    }
    @Test void returnsOkWhenPasswordResetSucceeds() {
        ResetPasswordRequest request = mock(ResetPasswordRequest.class);
        when(request.getNewPassword()).thenReturn("Password123");
        when(request.getConfirmPassword()).thenReturn("Password123");
        when(request.getToken()).thenReturn("valid");
        when(service.resetPassword("valid","Password123")).thenReturn(true);
        assertEquals(HttpStatus.OK, controller.resetPassword(request).getStatusCode());
    }
}
