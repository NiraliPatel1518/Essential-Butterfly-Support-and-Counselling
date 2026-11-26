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

import ca.sheridancollege.shtirthb.dto.AdminEmailChangeRequest;
import ca.sheridancollege.shtirthb.dto.AdminEmailOtpRequest;
import ca.sheridancollege.shtirthb.dto.AdminProfileResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.service.AdminEmailSecurityService;
import ca.sheridancollege.shtirthb.service.JwtService;

/*
 * Tests for AdminAccountController: the admin profile and the
 * "change my email address" steps. The email always comes from the
 * logged-in admin (Authentication), never from the request.
 */
@ExtendWith(MockitoExtension.class)
class AdminAccountControllerTest {

    @Mock AdminEmailSecurityService securityService;
    @Mock JwtService jwtService;
    @Mock Authentication authentication;
    @InjectMocks AdminAccountController controller;

    /*
     * Checks: The profile of the logged-in admin is returned.
     * How:    The fake Authentication gives admin@test.com. The fake service returns a profile for
     *   that email. The controller must answer 200 with the same profile object.
     */
    @Test
    void getProfile_returnsTheProfileOfTheLoggedInAdmin() {
        var profile = new AdminProfileResponse("Admin", "admin@test.com");
        when(authentication.getName()).thenReturn("admin@test.com");
        when(securityService.getProfile("admin@test.com")).thenReturn(profile);

        var response = controller.getProfile(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(profile, response.getBody());
    }

    /*
     * Checks: Asking to change the admin email uses the logged-in admin's email.
     * How:    The controller answers 200 with a message that a code was sent to the new address, and
     *   the service is called with the request and admin@test.com (not an email from the
     *   request).
     */
    @Test
    void requestEmailChange_sendsTheRequestForTheLoggedInAdmin() {
        AdminEmailChangeRequest request = new AdminEmailChangeRequest();
        when(authentication.getName()).thenReturn("admin@test.com");

        var response = controller.requestEmailChange(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("sent to the new email address"));
        verify(securityService).requestEmailChange(request, "admin@test.com");
    }

    /*
     * Checks: A conflict error from the service is passed on and not hidden.
     * How:    The fake service throws ConflictException (a client already uses the email).
     *   assertThrows checks the controller throws it too.
     */
    @Test
    void requestEmailChange_passesTheConflictErrorOn() {
        AdminEmailChangeRequest request = new AdminEmailChangeRequest();
        when(authentication.getName()).thenReturn("admin@test.com");
        doThrow(new AppExceptions.ConflictException("A client account already uses this email address."))
                .when(securityService).requestEmailChange(request, "admin@test.com");

        assertThrows(AppExceptions.ConflictException.class,
                () -> controller.requestEmailChange(request, authentication));
    }

    /*
     * Checks: After the code is verified, a new admin token is returned for the new email.
     * How:    The fake service returns an admin with new@test.com, and the fake JwtService returns
     *   new-jwt. The controller answers 200 with that token.
     */
    @Test
    void verifyEmailChange_returnsANewTokenForTheNewEmail() {
        AdminEmailOtpRequest request = new AdminEmailOtpRequest();
        AdminUser updated = new AdminUser("Admin", "new@test.com", "ENCODED");
        when(authentication.getName()).thenReturn("admin@test.com");
        when(securityService.verifyEmailChange(request, "admin@test.com")).thenReturn(updated);
        when(jwtService.generateToken("new@test.com", "ADMIN")).thenReturn("new-jwt");

        var response = controller.verifyEmailChange(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("new-jwt", response.getBody());
    }

    /*
     * Checks: A wrong code gives no token.
     * How:    The fake service throws ValidationException. The controller throws it too, and
     *   verifyNoInteractions proves JwtService was never used.
     */
    @Test
    void verifyEmailChange_issuesNoTokenWhenTheCodeIsWrong() {
        AdminEmailOtpRequest request = new AdminEmailOtpRequest();
        when(authentication.getName()).thenReturn("admin@test.com");
        when(securityService.verifyEmailChange(request, "admin@test.com"))
                .thenThrow(new AppExceptions.ValidationException("The verification code is incorrect."));

        assertThrows(AppExceptions.ValidationException.class,
                () -> controller.verifyEmailChange(request, authentication));

        verifyNoInteractions(jwtService);
    }

    /*
     * Checks: Resending the email-change code is done for the logged-in admin.
     * How:    The answer is 200, and the service is called with admin@test.com.
     */
    @Test
    void resendEmailChange_resendsForTheLoggedInAdmin() {
        when(authentication.getName()).thenReturn("admin@test.com");

        var response = controller.resendEmailChange(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(securityService).resendEmailChangeOtp("admin@test.com");
    }
}