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

import ca.sheridancollege.shtirthb.dto.PhoneNumberRequest;
import ca.sheridancollege.shtirthb.dto.PhoneVerificationStatusResponse;
import ca.sheridancollege.shtirthb.dto.VerifyPhoneRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.service.PhoneVerificationService;

/*
 * Tests for PhoneVerificationController. The email always comes from the
 * logged-in client (Authentication), never from the request.
 */
@ExtendWith(MockitoExtension.class)
class PhoneVerificationControllerTest {

    @Mock PhoneVerificationService phoneVerificationService;
    @Mock Authentication authentication;
    @InjectMocks PhoneVerificationController controller;

    /*
     * Checks: The phone verification status of the logged-in client is returned.
     * How:    The fake Authentication gives jane@mail.com. The service returns a status for that
     *   email, and the controller returns it with 200.
     */
    @Test
    void getStatus_returnsTheStatusOfTheLoggedInClient() {
        var status = new PhoneVerificationStatusResponse(true, "+14165551234", null);
        when(authentication.getName()).thenReturn("jane@mail.com");
        when(phoneVerificationService.getStatus("jane@mail.com")).thenReturn(status);

        var response = controller.getStatus(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(status, response.getBody());
    }

    /*
     * Checks: Sending the code starts verification for the logged-in client.
     * How:    The answer says a verification code has been sent, and the service is called with the
     *   request and jane@mail.com.
     */
    @Test
    void sendCode_startsVerificationForTheLoggedInClient() {
        PhoneNumberRequest request = new PhoneNumberRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");

        var response = controller.sendCode(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("verification code has been sent"));
        verify(phoneVerificationService).startVerification(request, "jane@mail.com");
    }

    /*
     * Checks: An error such as "phone already verified" is passed on.
     * How:    The service throws BusinessRuleException and the controller throws it too.
     */
    @Test
    void sendCode_passesTheServiceErrorOn() {
        PhoneNumberRequest request = new PhoneNumberRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");
        doThrow(new AppExceptions.BusinessRuleException("This phone number is already verified."))
                .when(phoneVerificationService).startVerification(request, "jane@mail.com");

        assertThrows(AppExceptions.BusinessRuleException.class,
                () -> controller.sendCode(request, authentication));
    }

    /*
     * Checks: The entered code is checked for the logged-in client.
     * How:    The answer says verified successfully, and the service verify is called with
     *   jane@mail.com.
     */
    @Test
    void verify_checksTheCodeForTheLoggedInClient() {
        VerifyPhoneRequest request = new VerifyPhoneRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");

        var response = controller.verify(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("verified successfully"));
        verify(phoneVerificationService).verify(request, "jane@mail.com");
    }

    /*
     * Checks: A wrong code error is passed on.
     * How:    The service throws ValidationException and the controller throws it too.
     */
    @Test
    void verify_passesTheWrongCodeErrorOn() {
        VerifyPhoneRequest request = new VerifyPhoneRequest();
        when(authentication.getName()).thenReturn("jane@mail.com");
        doThrow(new AppExceptions.ValidationException("The verification code is incorrect."))
                .when(phoneVerificationService).verify(request, "jane@mail.com");

        assertThrows(AppExceptions.ValidationException.class,
                () -> controller.verify(request, authentication));
    }

    /*
     * Checks: Resending the code is done for the logged-in client.
     * How:    The answer is 200, and service.resend is called with jane@mail.com.
     */
    @Test
    void resend_resendsForTheLoggedInClient() {
        when(authentication.getName()).thenReturn("jane@mail.com");

        var response = controller.resend(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(phoneVerificationService).resend("jane@mail.com");
    }
}