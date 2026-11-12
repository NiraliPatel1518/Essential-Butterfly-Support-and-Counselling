package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ca.sheridancollege.shtirthb.dto.AdminLoginOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminLoginResendRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.service.AdminEmailSecurityService;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * Tests for AdminAuthController: the second step of the admin login
 * (the 6-digit email code) and the "resend code" button.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock AdminEmailSecurityService securityService;
    @Mock JwtService jwtService;
    @InjectMocks AdminAuthController controller;

    @Test
    void verifyLoginOtp_returnsAnAdminTokenForTheVerifiedAdmin() {
        AdminLoginOtpRequest request = new AdminLoginOtpRequest();
        AdminUser admin = new AdminUser("Admin", "admin@test.com", "ENCODED");
        when(securityService.verifyLoginOtp(request)).thenReturn(admin);
        when(jwtService.generateToken("admin@test.com", "ADMIN")).thenReturn("jwt-token");

        var response = controller.verifyLoginOtp(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("jwt-token", response.getBody());
    }

    @Test
    void verifyLoginOtp_issuesNoTokenWhenTheCodeIsWrong() {
        AdminLoginOtpRequest request = new AdminLoginOtpRequest();
        when(securityService.verifyLoginOtp(request))
                .thenThrow(new AppExceptions.ValidationException("The verification code is incorrect."));

        assertThrows(AppExceptions.ValidationException.class,
                () -> controller.verifyLoginOtp(request));

        verifyNoInteractions(jwtService);
    }

    @Test
    void resendLoginOtp_callsTheServiceAndConfirms() {
        AdminLoginResendRequest request = new AdminLoginResendRequest();

        var response = controller.resendLoginOtp(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("new verification code has been sent"));
        verify(securityService).resendLoginOtp(request);
    }

    @Test
    void resendLoginOtp_passesTheWaitTimeErrorOn() {
        AdminLoginResendRequest request = new AdminLoginResendRequest();
        doThrow(new AppExceptions.BusinessRuleException("Please wait 30 second(s) before requesting another code."))
                .when(securityService).resendLoginOtp(request);

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> controller.resendLoginOtp(request));
    }
}